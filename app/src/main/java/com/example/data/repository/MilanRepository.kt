package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BlockRecord
import com.example.data.model.CallStreamPacket
import com.example.data.model.ChatMessage
import com.example.data.model.LikeRecord
import com.example.data.model.RandomChatMessage
import com.example.data.model.RandomChatPreferences
import com.example.data.model.RandomChatQueueEntry
import com.example.data.model.RandomChatSession
import com.example.data.model.RandomRevealStep
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class MilanRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = FirebaseAuth.getInstance(db.app)

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeMyProfile(userId: String): Flow<UserProfile?> {
        val path = "profiles/$userId"
        return db.collection("profiles").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun getProfileById(profileId: String): Result<UserProfile?> = runCatching {
        val docRef = db.collection("profiles").document(profileId)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    fun observePublicProfiles(): Flow<List<UserProfile>> {
        val path = "profiles"
        return db.collection("profiles")
            .whereEqualTo("visibility", "public")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createOrSaveProfile(profile: UserProfile, isExistingProfile: Boolean = false): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("profiles").document(uid)
        try {
            if (!isExistingProfile) {
                docRef.set(profile.toCreateMap(uid)).await()
            } else {
                docRef.update(profile.toProfileEditMap()).await()
            }
            uid
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserLocation(
        latitude: Double,
        longitude: Double,
        locationName: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("profiles").document(uid)
        val updates = mapOf(
            "latitude" to latitude.coerceIn(-90.0, 90.0),
            "longitude" to longitude.coerceIn(-180.0, 180.0),
            "locationName" to locationName.trim().take(100).ifEmpty { "Nearby" },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun upgradeToPremium(
        planName: String,
        expiresAtMillis: Long = 0L,
        paymentAmountInr: Int? = null,
        paymentDateMs: Long? = null,
        upiTxnId: String? = null,
        paymentStatus: String? = null,
        pendingPlan: String? = null,
        streakDays: Int? = null,
        todayUsageSeconds: Int? = null,
        lastUsageDate: String? = null,
        lastStreakCompletedDate: String? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("profiles").document(uid)
        val validPlan = if (planName in listOf("Free", "Monthly", "Yearly", "FreeReward1Day")) planName else "Monthly"
        val isPrem = validPlan != "Free"
        val updates = mutableMapOf<String, Any>(
            "isPremium" to isPrem,
            "premiumPlan" to validPlan,
            "premiumExpiresAt" to expiresAtMillis.coerceAtLeast(0L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (paymentAmountInr != null) updates["lastPaymentAmountInr"] = paymentAmountInr.coerceIn(0, 100000)
        if (paymentDateMs != null) updates["lastPaymentDateMs"] = paymentDateMs.coerceAtLeast(0L)
        if (upiTxnId != null) updates["lastUpiTxnId"] = upiTxnId.take(64)
        if (paymentStatus != null && paymentStatus in listOf("None", "PendingVerification", "Verified", "Rejected")) {
            updates["paymentStatus"] = paymentStatus
        }
        if (pendingPlan != null) updates["pendingPlan"] = pendingPlan.take(30)
        if (streakDays != null) updates["streakDays"] = streakDays.coerceIn(0, 7)
        if (todayUsageSeconds != null) updates["todayUsageSeconds"] = todayUsageSeconds.coerceIn(0, 86400)
        if (lastUsageDate != null) updates["lastUsageDate"] = lastUsageDate.take(20)
        if (lastStreakCompletedDate != null) updates["lastStreakCompletedDate"] = lastStreakCompletedDate.take(40)

        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    /**
     * Submits a 12-digit UPI UTR / Reference number for Admin verification WITHOUT activating Premium prematurely.
     */
    suspend fun submitUpiPaymentForVerification(
        planName: String,
        amountInr: Int,
        upiTxnId: String,
        paymentDateMs: Long = System.currentTimeMillis()
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("profiles").document(uid)
        val validPlan = if (planName in listOf("Monthly", "Yearly")) planName else "Monthly"
        val updates = mapOf(
            "lastPaymentAmountInr" to amountInr.coerceIn(0, 100000),
            "lastPaymentDateMs" to paymentDateMs.coerceAtLeast(0L),
            "lastUpiTxnId" to upiTxnId.trim().take(64),
            "paymentStatus" to "PendingVerification",
            "pendingPlan" to validPlan,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateUsageStreakProgress(
        currentProfile: UserProfile,
        newStreakDays: Int,
        newTodaySeconds: Int,
        todayDate: String,
        completedDate: String,
        grantFreeRewardDay: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("profiles").document(uid)
        val nowMillis = System.currentTimeMillis()
        val oneDayExpiry = nowMillis + 24L * 60L * 60L * 1000L

        val isPrem = if (grantFreeRewardDay) true else currentProfile.isPremium
        val plan = if (grantFreeRewardDay) "FreeReward1Day" else currentProfile.premiumPlan
        val expiresAt = if (grantFreeRewardDay) oneDayExpiry else currentProfile.premiumExpiresAt

        val updates = mapOf(
            "isPremium" to isPrem,
            "premiumPlan" to plan,
            "premiumExpiresAt" to expiresAt,
            "streakDays" to newStreakDays.coerceIn(0, 7),
            "todayUsageSeconds" to newTodaySeconds.coerceIn(0, 86400),
            "lastUsageDate" to todayDate.take(20),
            "lastStreakCompletedDate" to completedDate.take(40),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateGalleryPhotos(galleryPhotos: List<String>): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("profiles").document(uid)
        val updates = mapOf(
            "galleryPhotos" to galleryPhotos.take(6).map { it.take(150000) },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun submitFaceVerificationVideo(
        videoFrames: List<String>,
        mainPhotoBase64: String? = null,
        galleryPhotos: List<String>? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("profiles").document(uid)
        val updates = mutableMapOf<String, Any>(
            "isProfileVerified" to false,
            "verificationStatus" to "Pending",
            "verificationVideoFrames" to videoFrames.take(12).map { it.take(100000) },
            "verificationSubmittedAt" to System.currentTimeMillis(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!mainPhotoBase64.isNullOrBlank()) {
            updates["photoBase64"] = mainPhotoBase64.take(700000)
        }
        if (galleryPhotos != null) {
            updates["galleryPhotos"] = galleryPhotos.take(6).map { it.take(150000) }
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // Admin actions (only allowed by firestore.rules when request.auth.token.email == 'rk3171998@gmail.com')
    suspend fun adminVerifyUserProfile(
        targetUserId: String,
        approve: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("profiles").document(targetUserId)
        val updates = mutableMapOf<String, Any>(
            "isProfileVerified" to approve,
            "verificationStatus" to if (approve) "Verified" else "Rejected",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!approve) {
            updates["verificationVideoFrames"] = emptyList<String>()
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun adminUpdateUserPremium(
        targetUserId: String,
        planName: String,
        expiresAtMillis: Long = 0L,
        paymentStatus: String? = null,
        pendingPlan: String? = null
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("profiles").document(targetUserId)
        val validPlan = if (planName in listOf("Free", "Monthly", "Yearly", "FreeReward1Day")) planName else "Free"
        val isPrem = validPlan != "Free"
        val updates = mutableMapOf<String, Any>(
            "isPremium" to isPrem,
            "premiumPlan" to validPlan,
            "premiumExpiresAt" to expiresAtMillis,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (paymentStatus != null && paymentStatus in listOf("None", "PendingVerification", "Verified", "Rejected")) {
            updates["paymentStatus"] = paymentStatus
        }
        if (pendingPlan != null) {
            updates["pendingPlan"] = pendingPlan.take(30)
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun adminDeleteUserProfile(targetUserId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("profiles").document(targetUserId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeSentLikes(userId: String): Flow<List<LikeRecord>> {
        val path = "likes"
        return db.collection("likes")
            .whereEqualTo("fromUserId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LikeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeReceivedLikes(userId: String): Flow<List<LikeRecord>> {
        val path = "likes"
        return db.collection("likes")
            .whereEqualTo("toUserId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LikeRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun sendLike(fromUserName: String, toUserId: String, toUserName: String): Result<String> = runCatching {
        val uid = requireUserId()
        require(uid != toUserId) { "Cannot like your own profile" }
        val likeId = "${uid}_${toUserId}"
        val docRef = db.collection("likes").document(likeId)
        val payload = mapOf(
            "fromUserId" to uid,
            "toUserId" to toUserId,
            "fromUserName" to fromUserName.trim().take(100).ifEmpty { "User" },
            "toUserName" to toUserName.trim().take(100).ifEmpty { "User" },
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            likeId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun removeLike(toUserId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val likeId = "${uid}_${toUserId}"
        val docRef = db.collection("likes").document(likeId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeBlockedUsers(userId: String): Flow<List<BlockRecord>> {
        val path = "blocks"
        return db.collection("blocks")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(BlockRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun blockUser(blockedUserId: String, blockedUserName: String): Result<String> = runCatching {
        val uid = requireUserId()
        require(uid != blockedUserId) { "Cannot block yourself" }
        val blockId = "${uid}_${blockedUserId}"
        val docRef = db.collection("blocks").document(blockId)
        val payload = mapOf(
            "userId" to uid,
            "blockedUserId" to blockedUserId,
            "blockedUserName" to blockedUserName.trim().take(100).ifEmpty { "User" },
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            blockId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun unblockUser(blockedUserId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val blockId = "${uid}_${blockedUserId}"
        val docRef = db.collection("blocks").document(blockId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeAllUserMessages(userId: String, isAdmin: Boolean = false): Flow<List<ChatMessage>> {
        val path = "messages"
        val sentFlow = db.collection("messages")
            .whereEqualTo("senderId", userId)
            .snapshots()
            .map { it.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }

        val receivedFlow = db.collection("messages")
            .whereEqualTo("receiverId", userId)
            .snapshots()
            .map { it.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }

        if (!isAdmin) {
            return combine(sentFlow, receivedFlow) { sent, received ->
                (sent + received)
                    .distinctBy { it.id }
                    .sortedBy { (it.createdAt ?: Timestamp(0, 0)).seconds }
            }
        }

        val adminSupportFlow = db.collection("messages")
            .whereEqualTo("receiverId", com.example.data.model.ADMIN_SUPPORT_USER_ID)
            .snapshots()
            .map { it.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { _ -> emit(emptyList()) }

        return combine(sentFlow, receivedFlow, adminSupportFlow) { sent, received, support ->
            (sent + received + support)
                .distinctBy { it.id }
                .sortedBy { (it.createdAt ?: Timestamp(0, 0)).seconds }
        }
    }

    suspend fun sendMessage(
        senderName: String,
        receiverId: String,
        receiverName: String,
        text: String,
        currentDailyCount: Int,
        photoBase64: String = "",
        messageType: String = "text"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(uid != receiverId) { "Cannot message yourself" }
        val validType = if (messageType in listOf("text", "photo", "audio_call", "video_call")) messageType else "text"
        val cleanText = text.trim().take(1000).ifEmpty {
            when (validType) {
                "photo" -> "📷 Shared a photo"
                "audio_call" -> "📞 Audio call"
                "video_call" -> "📹 Video call"
                else -> ""
            }
        }
        require(cleanText.isNotEmpty()) { "Message text cannot be empty" }

        val chatId = buildChatId(uid, receiverId)
        val messageId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("messages").document(messageId)
        val payload = mutableMapOf<String, Any>(
            "chatId" to chatId,
            "senderId" to uid,
            "senderName" to senderName.trim().take(100).ifEmpty { "User" },
            "receiverId" to receiverId,
            "receiverName" to receiverName.trim().take(100).ifEmpty { "User" },
            "text" to cleanText,
            "messageType" to validType,
            "createdAt" to FieldValue.serverTimestamp()
        )
        if (photoBase64.isNotBlank()) {
            payload["photoBase64"] = photoBase64.take(700000)
        }
        try {
            docRef.set(payload).await()
            if (validType == "text" || validType == "photo") {
                db.collection("profiles").document(uid).update(
                    mapOf(
                        "dailyMessageCount" to (currentDailyCount + 1),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                runCatching {
                    db.collection("profiles").document(uid).update(
                        mapOf(
                            "dailyMessageCount" to currentDailyCount.coerceAtLeast(0),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            messageId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteNormalChatThread(otherUserId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val chatId = buildChatId(uid, otherUserId)
        val path = "messages"
        try {
            val sentDocs = db.collection(path)
                .whereEqualTo("chatId", chatId)
                .whereEqualTo("senderId", uid)
                .get()
                .await()
                .documents
            val receivedDocs = db.collection(path)
                .whereEqualTo("chatId", chatId)
                .whereEqualTo("receiverId", uid)
                .get()
                .await()
                .documents
            val allRefs = (sentDocs + receivedDocs).distinctBy { it.id }.map { it.reference }
            allRefs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "$path/$chatId")
            throw e
        }
    }

    suspend fun publishCallStreamPacket(
        partnerId: String,
        callMode: String,
        isActive: Boolean,
        seq: Long,
        videoFrameBase64: String = "",
        audioChunkBase64: String = "",
        micLevel: Int = 0
    ): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        val docRef = db.collection("call_streams").document(uid)
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "partnerId" to partnerId,
            "callMode" to if (callMode == "VIDEO") "VIDEO" else "AUDIO",
            "isActive" to isActive,
            "seq" to seq.coerceAtLeast(0L),
            "micLevel" to micLevel.coerceIn(0, 100),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (videoFrameBase64.isNotBlank()) {
            payload["videoFrameBase64"] = videoFrameBase64.take(240000)
        }
        if (audioChunkBase64.isNotBlank()) {
            payload["audioChunkBase64"] = audioChunkBase64.take(240000)
        }
        docRef.set(payload).await()
    }

    fun observePartnerCallStream(partnerUserId: String): Flow<CallStreamPacket?> {
        return db.collection("call_streams")
            .document(partnerUserId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(CallStreamPacket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .retryWhen { _, _ ->
                delay(1000L)
                true
            }
            .catch { _ -> emit(null) }
    }

    suspend fun clearCallStream(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        db.collection("call_streams").document(uid).delete().await()
    }

    // ==================== RANDOM CHAT (UNLIMITED + STEP-BY-STEP MUTUAL REVEAL) ====================

    suspend fun enqueueForRandomChat(
        myProfile: UserProfile,
        preferences: RandomChatPreferences
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("random_chat_queue").document(uid)
        val validGender = if (myProfile.gender in listOf("Male", "Female", "Other")) myProfile.gender else "Male"
        val validPrefGender = if (preferences.preferredGender in listOf("All", "Male", "Female")) {
            preferences.preferredGender
        } else {
            "All"
        }
        val minAge = preferences.minAge.coerceIn(18, 65)
        val maxAge = preferences.maxAge.coerceIn(minAge, 65)
        val payload = mapOf(
            "userId" to uid,
            "gender" to validGender,
            "age" to myProfile.age.coerceIn(18, 100),
            "latitude" to myProfile.latitude.coerceIn(-90.0, 90.0),
            "longitude" to myProfile.longitude.coerceIn(-180.0, 180.0),
            "preferredGender" to validPrefGender,
            "preferredMinAge" to minAge,
            "preferredMaxAge" to maxAge,
            "preferredMaxDistanceKm" to preferences.maxDistanceKm.coerceIn(10, 25000),
            "status" to "waiting",
            "matchedSessionId" to "",
            "matchedPartnerId" to "",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun leaveRandomChatQueue(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        val docRef = db.collection("random_chat_queue").document(uid)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeWaitingRandomQueueEntries(): Flow<List<RandomChatQueueEntry>> {
        val path = "random_chat_queue"
        return db.collection(path)
            .whereEqualTo("status", "waiting")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RandomChatQueueEntry::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun tryPairWithWaitingUser(
        myProfile: UserProfile,
        partnerEntry: RandomChatQueueEntry
    ): Result<RandomChatSession> = runCatching {
        val uid = requireUserId()
        require(uid != partnerEntry.userId) { "Cannot match with yourself" }

        val sessionId = "rnd_sess_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val myIsA = uid < partnerEntry.userId
        val userAId = if (myIsA) uid else partnerEntry.userId
        val userBId = if (myIsA) partnerEntry.userId else uid
        val myGender = if (myProfile.gender in listOf("Male", "Female", "Other")) myProfile.gender else "Male"
        val partnerGender = if (partnerEntry.gender in listOf("Male", "Female", "Other")) partnerEntry.gender else "Female"
        val userAGender = if (myIsA) myGender else partnerGender
        val userBGender = if (myIsA) partnerGender else myGender
        val participants = listOf(userAId, userBId)

        val partnerQueueRef = db.collection("random_chat_queue").document(partnerEntry.userId)
        val myQueueRef = db.collection("random_chat_queue").document(uid)
        val sessionRef = db.collection("random_chat_sessions").document(sessionId)

        try {
            db.runTransaction { transaction ->
                val partnerSnap = transaction.get(partnerQueueRef)
                if (!partnerSnap.exists() || partnerSnap.getString("status") != "waiting") {
                    throw IllegalStateException("Partner is no longer waiting in queue")
                }
                val mySnap = transaction.get(myQueueRef)
                if (mySnap.exists() && mySnap.getString("status") == "matched") {
                    val existingSessId = mySnap.getString("matchedSessionId").orEmpty()
                    if (existingSessId.isNotBlank()) {
                        throw IllegalStateException("Already matched into session $existingSessId")
                    }
                }

                val sessionPayload = mapOf(
                    "userAId" to userAId,
                    "userBId" to userBId,
                    "participantIds" to participants,
                    "userAGender" to userAGender,
                    "userBGender" to userBGender,
                    "status" to "active",
                    "endedByUserId" to "",
                    "userAConsentName" to false,
                    "userBConsentName" to false,
                    "userAConsentAge" to false,
                    "userBConsentAge" to false,
                    "userAConsentPhoto" to false,
                    "userBConsentPhoto" to false,
                    "userAConsentProfile" to false,
                    "userBConsentProfile" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(sessionRef, sessionPayload)

                transaction.update(
                    partnerQueueRef,
                    mapOf(
                        "status" to "matched",
                        "matchedSessionId" to sessionId,
                        "matchedPartnerId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                if (mySnap.exists()) {
                    transaction.update(
                        myQueueRef,
                        mapOf(
                            "status" to "matched",
                            "matchedSessionId" to sessionId,
                            "matchedPartnerId" to partnerEntry.userId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            RandomChatSession(
                id = sessionId,
                userAId = userAId,
                userBId = userBId,
                participantIds = participants,
                userAGender = userAGender,
                userBGender = userBGender,
                status = "active"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, sessionRef.path)
            throw e
        }
    }

    suspend fun updateUserPresence(isOnline: Boolean): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        val docRef = db.collection("profiles").document(uid)
        val updates = mapOf(
            "isOnline" to isOnline,
            "lastActiveAtMs" to System.currentTimeMillis(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun setRandomMeetEnabled(enabled: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("profiles").document(uid)
        val updates = mapOf(
            "allowRandomMeet" to enabled,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun recordDailyRandomFind(todayDate: String, newCount: Int): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("profiles").document(uid)
        val updates = mapOf(
            "dailyRandomFindCount" to newCount.coerceAtLeast(0),
            "lastRandomFindDate" to todayDate.take(20),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun createDirectRandomSession(
        myProfile: UserProfile,
        partnerProfile: UserProfile
    ): Result<RandomChatSession> = runCatching {
        val uid = requireUserId()
        require(uid != partnerProfile.userId) { "Cannot match with yourself" }

        val sessionId = "rnd_sess_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val myIsA = uid < partnerProfile.userId
        val userAId = if (myIsA) uid else partnerProfile.userId
        val userBId = if (myIsA) partnerProfile.userId else uid
        val myGender = if (myProfile.gender in listOf("Male", "Female", "Other")) myProfile.gender else "Male"
        val partnerGender = if (partnerProfile.gender in listOf("Male", "Female", "Other")) partnerProfile.gender else "Female"
        val userAGender = if (myIsA) myGender else partnerGender
        val userBGender = if (myIsA) partnerGender else myGender
        val participants = listOf(userAId, userBId)

        val sessionRef = db.collection("random_chat_sessions").document(sessionId)
        val sessionPayload = mapOf(
            "userAId" to userAId,
            "userBId" to userBId,
            "participantIds" to participants,
            "userAGender" to userAGender,
            "userBGender" to userBGender,
            "status" to "active",
            "endedByUserId" to "",
            "userAConsentName" to false,
            "userBConsentName" to false,
            "userAConsentAge" to false,
            "userBConsentAge" to false,
            "userAConsentPhoto" to false,
            "userBConsentPhoto" to false,
            "userAConsentProfile" to false,
            "userBConsentProfile" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            sessionRef.set(sessionPayload).await()
            runCatching {
                db.collection("random_chat_queue").document(uid).delete().await()
            }
            RandomChatSession(
                id = sessionId,
                userAId = userAId,
                userBId = userBId,
                participantIds = participants,
                userAGender = userAGender,
                userBGender = userBGender,
                status = "active"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, sessionRef.path)
            throw e
        }
    }

    fun observeMyActiveRandomSessions(userId: String): Flow<List<RandomChatSession>> {
        val path = "random_chat_sessions"
        return db.collection(path)
            .whereArrayContains("participantIds", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RandomChatSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                    .filter { it.status == "active" }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeMyLatestActiveRandomSession(userId: String): Flow<RandomChatSession?> {
        val path = "random_chat_sessions"
        return db.collection(path)
            .whereArrayContains("participantIds", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RandomChatSession::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                    .filter { it.status == "active" }
                    .maxByOrNull { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeRandomSessionById(sessionId: String): Flow<RandomChatSession?> {
        val path = "random_chat_sessions/$sessionId"
        return db.collection("random_chat_sessions").document(sessionId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        RandomChatSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    suspend fun submitRandomRevealConsent(
        session: RandomChatSession,
        step: RandomRevealStep,
        consent: Boolean = true
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(session.id.isNotBlank()) { "Session ID cannot be blank" }
        require(session.status == "active") { "Session is already ended" }
        require(uid == session.userAId || uid == session.userBId) { "Not a session participant" }

        when (step) {
            RandomRevealStep.NAME -> Unit
            RandomRevealStep.AGE -> require(session.isNameRevealed) { "Name must be mutually revealed before Age" }
            RandomRevealStep.PHOTO -> require(session.isAgeRevealed) { "Age must be mutually revealed before Face Reveal" }
            RandomRevealStep.PROFILE -> require(session.isPhotoRevealed) { "Face Reveal must be completed before Full Profile" }
        }

        val amUserA = (uid == session.userAId)
        val fieldName = when (step) {
            RandomRevealStep.NAME -> if (amUserA) "userAConsentName" else "userBConsentName"
            RandomRevealStep.AGE -> if (amUserA) "userAConsentAge" else "userBConsentAge"
            RandomRevealStep.PHOTO -> if (amUserA) "userAConsentPhoto" else "userBConsentPhoto"
            RandomRevealStep.PROFILE -> if (amUserA) "userAConsentProfile" else "userBConsentProfile"
        }

        val docRef = db.collection("random_chat_sessions").document(session.id)
        try {
            docRef.update(
                mapOf(
                    fieldName to consent,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun endRandomChatSession(sessionId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (sessionId.isNotBlank()) {
            val docRef = db.collection("random_chat_sessions").document(sessionId)
            try {
                docRef.update(
                    mapOf(
                        "status" to "ended",
                        "endedByUserId" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, docRef.path)
                throw e
            }
        }
        runCatching {
            db.collection("random_chat_queue").document(uid).delete().await()
        }
        Unit
    }

    suspend fun deleteRandomChatSession(sessionId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        if (sessionId.isBlank()) return@runCatching
        val sessionRef = db.collection("random_chat_sessions").document(sessionId)
        try {
            val messageDocs = db.collection("random_chat_messages")
                .whereEqualTo("sessionId", sessionId)
                .whereArrayContains("participantIds", uid)
                .get()
                .await()
                .documents
            messageDocs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            sessionRef.delete().await()
            runCatching {
                db.collection("random_chat_queue").document(uid).delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, sessionRef.path)
            throw e
        }
    }

    fun observeRandomChatMessages(sessionId: String, currentUserId: String): Flow<List<RandomChatMessage>> {
        val path = "random_chat_messages"
        return db.collection(path)
            .whereEqualTo("sessionId", sessionId)
            .whereArrayContains("participantIds", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RandomChatMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { (it.createdAt ?: Timestamp(0, 0)).seconds }
            }
            .catch { error ->
                if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun sendRandomChatMessage(
        session: RandomChatSession,
        text: String,
        messageType: String = "text"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(session.id.isNotBlank()) { "Session ID cannot be blank" }
        val cleanText = text.trim().take(1000)
        require(cleanText.isNotEmpty()) { "Message cannot be empty" }
        val validType = if (messageType == "system") "system" else "text"

        val msgId = "rmsg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = db.collection("random_chat_messages").document(msgId)
        val payload = mapOf(
            "sessionId" to session.id,
            "participantIds" to session.participantIds,
            "senderId" to uid,
            "text" to cleanText,
            "messageType" to validType,
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            msgId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }


    companion object {
        fun buildChatId(uid1: String, uid2: String): String {
            return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
        }

        fun calculateDistanceKm(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            val earthRadiusKm = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2.0) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2.0)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return (earthRadiusKm * c * 10.0).toInt() / 10.0
        }
    }
}
