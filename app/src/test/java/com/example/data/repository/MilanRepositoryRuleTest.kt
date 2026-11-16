package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.RandomChatPreferences
import com.example.data.model.RandomChatQueueEntry
import com.example.data.model.RandomRevealStep
import com.example.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class MilanRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createProfile_andUpdateLocation_andObservePublicProfiles_succeeds() = runBlocking(Dispatchers.IO) {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceUid = signInTestUser("alice_$suffix@test.com")
    val repo = MilanRepository(firestore)

    val aliceProfile = UserProfile(
      userId = aliceUid,
      firstName = "Aarohi",
      lastName = "Shah",
      birthDate = "1999-08-12",
      age = 27,
      gender = "Female",
      bio = "Art & Chai enthusiast",
      interests = listOf("Art", "Travel"),
      photoBase64 = "avatar_rose",
      latitude = 23.0225,
      longitude = 72.5714,
      locationName = "Ahmedabad",
      visibility = "public"
    )

    val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createOrSaveProfile(aliceProfile, isExistingProfile = false)
    }
    assertTrue("Expected profile save to succeed: ${saveResult.exceptionOrNull()}", saveResult.isSuccess)

    val locUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateUserLocation(21.1702, 72.8311, "Surat, Gujarat")
    }
    assertTrue("Expected hourly location update to succeed: ${locUpdate.exceptionOrNull()}", locUpdate.isSuccess)

    val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.getProfileById(aliceUid).getOrThrow()
    }
    assertNotNull(fetched)
    assertEquals("Surat, Gujarat", fetched?.locationName)
  }

  @Test
  fun sendLike_andSendPhotoMessage_andBlockUser_succeedsBetweenRegisteredUsers() = runBlocking(Dispatchers.IO) {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceUid = signInTestUser("alice_like_$suffix@test.com")
    val aliceRepo = MilanRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.createOrSaveProfile(
        UserProfile(userId = aliceUid, firstName = "Alice", lastName = "Patel", age = 25, gender = "Female"),
        isExistingProfile = false
      ).getOrThrow()
    }

    val bobUid = signInTestUser("bob_like_$suffix@test.com")
    val bobRepo = MilanRepository(firestore)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.createOrSaveProfile(
        UserProfile(userId = bobUid, firstName = "Bob", lastName = "Mehta", age = 27, gender = "Male"),
        isExistingProfile = false
      ).getOrThrow()
    }

    // Bob likes Alice
    val likeResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.sendLike(fromUserName = "Bob Mehta", toUserId = aliceUid, toUserName = "Alice Patel")
    }
    assertTrue("Expected sendLike to succeed: ${likeResult.exceptionOrNull()}", likeResult.isSuccess)

    // Bob sends photo message to Alice
    val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.sendMessage(
        senderName = "Bob Mehta",
        receiverId = aliceUid,
        receiverName = "Alice Patel",
        text = "📷 Shared a photo",
        currentDailyCount = 0,
        photoBase64 = "base64_photo_sample_bytes",
        messageType = "photo"
      )
    }
    assertTrue("Expected sendMessage to succeed: ${msgResult.exceptionOrNull()}", msgResult.isSuccess)

    // Bob blocks Alice
    val blockResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.blockUser(blockedUserId = aliceUid, blockedUserName = "Alice Patel")
    }
    assertTrue("Expected blockUser to succeed: ${blockResult.exceptionOrNull()}", blockResult.isSuccess)
  }

  @Test
  fun observePublicProfiles_unauthenticated_failsWithPermissionDenied() = runBlocking(Dispatchers.IO) {
    auth.signOut()
    val repo = MilanRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observePublicProfiles().first()
      }
      fail("Expected unauthenticated query to throw exception")
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      assertNotNull("Expected wrapped FirebaseFirestoreException but got $e", firestoreEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }
  }

  @Test
  fun observeBlockedUsers_crossUserQuery_failsWithPermissionDenied() = runBlocking(Dispatchers.IO) {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceUid = signInTestUser("alice_block_$suffix@test.com")
    signInTestUser("bob_block_$suffix@test.com")
    val bobRepo = MilanRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        bobRepo.observeBlockedUsers(aliceUid).first()
      }
      fail("Expected cross-user block query to fail with PERMISSION_DENIED")
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      assertNotNull("Expected wrapped FirebaseFirestoreException", firestoreEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }
  }

  @Test
  fun updateUsageStreakProgress_grantsFreeDayPremiumOnSixthConsecutiveDay() = runBlocking(Dispatchers.IO) {
    val suffix = UUID.randomUUID().toString().take(8)
    val userUid = signInTestUser("streak_$suffix@test.com")
    val repo = MilanRepository(firestore)

    val initialProfile = UserProfile(
      userId = userUid,
      firstName = "Karan",
      lastName = "Joshi",
      age = 26,
      gender = "Male",
      streakDays = 5,
      todayUsageSeconds = 850,
      lastUsageDate = "2026-11-02",
      lastStreakCompletedDate = "2026-11-01",
      visibility = "public"
    )

    withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.createOrSaveProfile(initialProfile, isExistingProfile = false).getOrThrow()
    }

    val streakUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.updateUsageStreakProgress(
        currentProfile = initialProfile,
        newStreakDays = 0,
        newTodaySeconds = 915,
        todayDate = "2026-11-02",
        completedDate = "2026-11-02",
        grantFreeRewardDay = true
      )
    }
    assertTrue("Expected streak reward update to succeed: ${streakUpdate.exceptionOrNull()}", streakUpdate.isSuccess)

    val updatedProfile = withTimeout(DEFAULT_TIMEOUT_MS) {
      repo.getProfileById(userUid).getOrThrow()
    }
    assertNotNull(updatedProfile)
    assertTrue(updatedProfile?.isPremium == true)
    assertEquals("FreeReward1Day", updatedProfile?.premiumPlan)
  }

  @Test
  fun submitFaceVerificationVideo_andSixGalleryPhotos_andAdminApproveBlueTick_succeeds() = runBlocking(Dispatchers.IO) {
    val suffix = UUID.randomUUID().toString().take(8)
    val userUid = signInTestUser("verify_user_$suffix@test.com")
    val userRepo = MilanRepository(firestore)

    val initialProfile = UserProfile(
      userId = userUid,
      firstName = "Diya",
      lastName = "Patel",
      age = 24,
      gender = "Female",
      photoBase64 = "main_dp_photo_base64_sample_bytes",
      visibility = "public"
    )

    withTimeout(DEFAULT_TIMEOUT_MS) {
      userRepo.createOrSaveProfile(initialProfile, isExistingProfile = false).getOrThrow()
    }

    // 1. User uploads 6 gallery photos
    val sixPhotos = (1..6).map { "gallery_photo_${it}_base64" }
    val galleryRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      userRepo.updateGalleryPhotos(sixPhotos)
    }
    assertTrue("Expected 6 gallery photos update to succeed: ${galleryRes.exceptionOrNull()}", galleryRes.isSuccess)

    // 2. User submits live face verification video (Left -> Right frames)
    val videoFrames = (1..8).map { "face_frame_left_to_right_$it" }
    val submitVideoRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      userRepo.submitFaceVerificationVideo(videoFrames)
    }
    assertTrue("Expected face verification video submission to succeed: ${submitVideoRes.exceptionOrNull()}", submitVideoRes.isSuccess)

    val pendingFetched = withTimeout(DEFAULT_TIMEOUT_MS) {
      userRepo.getProfileById(userUid).getOrThrow()
    }
    assertNotNull(pendingFetched)
    assertEquals(6, pendingFetched?.galleryPhotos?.size)
    assertEquals(8, pendingFetched?.verificationVideoFrames?.size)
    assertEquals("Pending", pendingFetched?.verificationStatus)
    assertEquals(false, pendingFetched?.isProfileVerified)
    assertEquals(true, pendingFetched?.hasPendingFaceVerification)
  }

  @Test
  fun randomChat_matchmaking_mutualReveal_andUnlimitedMessages_succeeds() = runBlocking(Dispatchers.IO) {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceUid = signInTestUser("rnd_alice_$suffix@test.com")
    val aliceRepo = MilanRepository(firestore)
    val aliceProfile = UserProfile(
      userId = aliceUid,
      firstName = "Ananya",
      lastName = "Rao",
      age = 24,
      gender = "Female",
      latitude = 23.0225,
      longitude = 72.5714,
      visibility = "public"
    )
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.createOrSaveProfile(aliceProfile, isExistingProfile = false).getOrThrow()
      aliceRepo.enqueueForRandomChat(
        aliceProfile,
        RandomChatPreferences(preferredGender = "Male", minAge = 20, maxAge = 35, maxDistanceKm = 1000)
      ).getOrThrow()
    }

    val bobUid = signInTestUser("rnd_bob_$suffix@test.com")
    val bobRepo = MilanRepository(firestore)
    val bobProfile = UserProfile(
      userId = bobUid,
      firstName = "Rohan",
      lastName = "Desai",
      age = 26,
      gender = "Male",
      latitude = 23.0300,
      longitude = 72.5800,
      visibility = "public"
    )
    withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.createOrSaveProfile(bobProfile, isExistingProfile = false).getOrThrow()
      bobRepo.enqueueForRandomChat(
        bobProfile,
        RandomChatPreferences(preferredGender = "Female", minAge = 20, maxAge = 35, maxDistanceKm = 1000)
      ).getOrThrow()
    }

    // Bob discovers Alice waiting in queue
    val waitingEntries = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeWaitingRandomQueueEntries().first { list -> list.any { it.userId == aliceUid } }
    }
    val aliceEntry: RandomChatQueueEntry = waitingEntries.first { it.userId == aliceUid }

    // Bob pairs with Alice atomically
    val createdSession = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.tryPairWithWaitingUser(bobProfile, aliceEntry).getOrThrow()
    }
    assertTrue(createdSession.id.isNotBlank())
    assertEquals(false, createdSession.isNameRevealed)

    // Step 1: Bob agrees to Name reveal, then Alice agrees -> mutually unlocked!
    withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.submitRandomRevealConsent(createdSession, RandomRevealStep.NAME, true).getOrThrow()
    }
    signInTestUser("rnd_alice_$suffix@test.com")
    val afterBobSession = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeRandomSessionById(createdSession.id).first { it != null }
    }!!
    assertEquals(false, afterBobSession.isNameRevealed)

    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.submitRandomRevealConsent(afterBobSession, RandomRevealStep.NAME, true).getOrThrow()
    }
    val afterNameSession = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeRandomSessionById(createdSession.id).first { it?.isNameRevealed == true }
    }!!
    assertTrue(afterNameSession.isNameRevealed)
    assertEquals(false, afterNameSession.isAgeRevealed)

    // Unlimited chat message in active session
    val msgRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.sendRandomChatMessage(afterNameSession, "Nice meeting you!", "text")
    }
    assertTrue("Expected random chat message send to succeed: ${msgRes.exceptionOrNull()}", msgRes.isSuccess)

    val msgs = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeRandomChatMessages(afterNameSession.id, aliceUid).first { it.isNotEmpty() }
    }
    assertEquals("Nice meeting you!", msgs.first().text)

    // Ending session cleans up and locks session
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.endRandomChatSession(afterNameSession.id).getOrThrow()
    }
    val ended = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeRandomSessionById(afterNameSession.id).first { it?.status == "ended" }
    }!!
    assertEquals(aliceUid, ended.endedByUserId)
  }

  @Test
  fun observeWaitingRandomQueueEntries_unauthenticated_failsWithPermissionDenied() = runBlocking(Dispatchers.IO) {
    auth.signOut()
    val repo = MilanRepository(firestore)

    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeWaitingRandomQueueEntries().first()
      }
      fail("Expected unauthenticated queue query to throw exception")
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      assertNotNull("Expected wrapped FirebaseFirestoreException but got $e", firestoreEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }
  }

  @Test
  fun updateUserPresence_setRandomMeetEnabled_andCreateDirectRandomSession_succeeds() = runBlocking(Dispatchers.IO) {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceUid = signInTestUser("rnd_direct_alice_$suffix@test.com")
    val aliceRepo = MilanRepository(firestore)
    val aliceProfile = UserProfile(
      userId = aliceUid,
      firstName = "Meera",
      lastName = "Patel",
      age = 24,
      gender = "Female",
      allowRandomMeet = true,
      isOnline = false,
      lastActiveAtMs = 0L,
      visibility = "public"
    )
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.createOrSaveProfile(aliceProfile, isExistingProfile = false).getOrThrow()
      aliceRepo.updateUserPresence(isOnline = true).getOrThrow()
      aliceRepo.setRandomMeetEnabled(enabled = false).getOrThrow()
      aliceRepo.setRandomMeetEnabled(enabled = true).getOrThrow()
    }

    val bobUid = signInTestUser("rnd_direct_bob_$suffix@test.com")
    val bobRepo = MilanRepository(firestore)
    val bobProfile = UserProfile(
      userId = bobUid,
      firstName = "Arjun",
      lastName = "Verma",
      age = 27,
      gender = "Male",
      allowRandomMeet = true,
      visibility = "public"
    )
    withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.createOrSaveProfile(bobProfile, isExistingProfile = false).getOrThrow()
    }

    // Bob initiates direct Random Chat with Alice even though Alice isn't waiting in queue
    val directSession = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.createDirectRandomSession(bobProfile, aliceProfile).getOrThrow()
    }
    assertTrue(directSession.id.isNotBlank())
    assertEquals(false, directSession.isNameRevealed)

    // Bob sends an unlimited random chat message while Alice isn't on the Random screen
    withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.sendRandomChatMessage(directSession, "Hi! Sent from Random Meet anytime", "text").getOrThrow()
    }

    // Alice signs in / resumes and observes active random sessions
    signInTestUser("rnd_direct_alice_$suffix@test.com")
    val aliceSessions = withTimeout(FLOW_TIMEOUT_MS) {
      aliceRepo.observeMyActiveRandomSessions(aliceUid).first { list -> list.any { it.id == directSession.id } }
    }
    assertTrue(aliceSessions.any { it.id == directSession.id && !it.isNameRevealed })
  }

  @Test
  fun recordDailyRandomFind_deleteRandomChatSession_andDeleteNormalChatThread_succeeds() = runBlocking(Dispatchers.IO) {
    val suffix = UUID.randomUUID().toString().take(8)
    val aliceUid = signInTestUser("del_alice_$suffix@test.com")
    val aliceRepo = MilanRepository(firestore)
    val aliceProfile = UserProfile(
      userId = aliceUid,
      firstName = "Kavya",
      lastName = "Joshi",
      age = 24,
      gender = "Female",
      allowRandomMeet = true,
      visibility = "public"
    )
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.createOrSaveProfile(aliceProfile, isExistingProfile = false).getOrThrow()
      aliceRepo.recordDailyRandomFind("2026-11-16", 2).getOrThrow()
    }
    val updatedAlice = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.getProfileById(aliceUid).getOrThrow()
    }
    assertEquals(2, updatedAlice?.dailyRandomFindCount)
    assertEquals("2026-11-16", updatedAlice?.lastRandomFindDate)

    val bobUid = signInTestUser("del_bob_$suffix@test.com")
    val bobRepo = MilanRepository(firestore)
    val bobProfile = UserProfile(
      userId = bobUid,
      firstName = "Dev",
      lastName = "Shah",
      age = 26,
      gender = "Male",
      allowRandomMeet = true,
      visibility = "public"
    )
    withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.createOrSaveProfile(bobProfile, isExistingProfile = false).getOrThrow()
      bobRepo.sendMessage(
        senderName = "Dev Shah",
        receiverId = aliceUid,
        receiverName = "Kavya Joshi",
        text = "Hi Kavya!",
        currentDailyCount = 0
      ).getOrThrow()
    }

    val rndSession = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.createDirectRandomSession(bobProfile, aliceProfile).getOrThrow()
    }
    withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.sendRandomChatMessage(rndSession, "Hello from random chat", "text").getOrThrow()
      bobRepo.deleteRandomChatSession(rndSession.id).getOrThrow()
      bobRepo.deleteNormalChatThread(aliceUid).getOrThrow()
    }

    val remainingRandom = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeMyActiveRandomSessions(bobUid).first()
    }
    assertTrue(remainingRandom.none { it.id == rndSession.id })

    val remainingNormal = withTimeout(FLOW_TIMEOUT_MS) {
      bobRepo.observeAllUserMessages(bobUid).first()
    }
    assertTrue(remainingNormal.isEmpty())
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 15000L
    const val FLOW_TIMEOUT_MS = 8000L
  }
}
