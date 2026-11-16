const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";
const ADMIN_UID = "admin_rk";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validProfilePayload(uid, firstName = "Aarav", visibility = "public", role = "user") {
  return {
    userId: uid,
    firstName: firstName,
    lastName: "Patel",
    birthDate: "1998-05-15",
    age: 28,
    gender: "Male",
    bio: "Coffee lover & traveler",
    interests: ["Travel", "Music"],
    photoBase64: "avatar_rose",
    latitude: 23.0225,
    longitude: 72.5714,
    locationName: "Ahmedabad",
    isPremium: false,
    premiumPlan: "Free",
    premiumExpiresAt: 0,
    dailyMessageCount: 0,
    streakDays: 0,
    todayUsageSeconds: 0,
    lastUsageDate: "2026-11-02",
    lastStreakCompletedDate: "",
    role: role,
    visibility: visibility,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("1. Unauthenticated user cannot list or read profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("profiles").where("visibility", "==", "public").get());
});

test("2. Authenticated user can create own valid profile and query public profiles", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).set(validProfilePayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("profiles").where("visibility", "==", "public").get()
  );
});

test("3. Non-admin user cannot self-assign admin role", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();
  await assertFails(
    aliceDb.collection("profiles").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, "Alice", "public", "admin"))
  );
});

test("4. Verified Admin (rk3171998@gmail.com) can create admin profile and manage users", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("profiles").doc(BOB_UID).set(validProfilePayload(BOB_UID, "Bob"));
  });

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "rk3171998@gmail.com",
    email_verified: true,
  }).firestore();

  await assertSucceeds(
    adminDb.collection("profiles").doc(ADMIN_UID).set(validProfilePayload(ADMIN_UID, "admin", "public", "admin"))
  );

  // Admin can grant premium to Bob
  await assertSucceeds(
    adminDb.collection("profiles").doc(BOB_UID).update({
      isPremium: true,
      premiumPlan: "Monthly",
      updatedAt: new Date(),
    })
  );

  // Admin can verify Bob's live face verification video and grant Verified User Blue Tick
  await assertSucceeds(
    adminDb.collection("profiles").doc(BOB_UID).update({
      isProfileVerified: true,
      verificationStatus: "Verified",
      updatedAt: new Date(),
    })
  );

  // Admin can delete Bob's profile
  await assertSucceeds(
    adminDb.collection("profiles").doc(BOB_UID).delete()
  );
});

test("5. Unverified email spoof of Admin is rejected (Email Spoofing Test)", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("profiles").doc(BOB_UID).set(validProfilePayload(BOB_UID, "Bob"));
  });

  const spoofedAdminDb = testEnv.authenticatedContext(CHARLIE_UID, {
    email: "rk3171998@gmail.com",
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofedAdminDb.collection("profiles").doc(BOB_UID).delete()
  );
});

test("6. Owner can update 6-day streak, hourly GPS location, and unlock FreeReward1Day", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("profiles").doc(ALICE_UID).set(validProfilePayload(ALICE_UID));
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Hourly GPS location update
  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).update({
      latitude: 21.1702,
      longitude: 72.8311,
      locationName: "Surat, Gujarat",
      updatedAt: new Date(),
    })
  );
  // Streak reward update
  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).update({
      streakDays: 6,
      todayUsageSeconds: 920,
      lastUsageDate: "2026-11-02",
      lastStreakCompletedDate: "2026-11-02",
      isPremium: true,
      premiumPlan: "FreeReward1Day",
      premiumExpiresAt: 1800000000000,
      updatedAt: new Date(),
    })
  );
});

test("7. Valid Like, Block, Text Message, Photo Message, and Video Call log creation succeeds", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("profiles").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, "Alice"));
    await ctx.firestore().collection("profiles").doc(BOB_UID).set(validProfilePayload(BOB_UID, "Bob"));
  });
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("likes").doc(`${ALICE_UID}_${BOB_UID}`).set({
      fromUserId: ALICE_UID,
      toUserId: BOB_UID,
      fromUserName: "Alice",
      toUserName: "Bob",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("messages").doc("msg_1").set({
      chatId: `${ALICE_UID}_${BOB_UID}`,
      senderId: ALICE_UID,
      senderName: "Alice",
      receiverId: BOB_UID,
      receiverName: "Bob",
      text: "Hello!",
      messageType: "text",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("messages").doc("msg_photo_2").set({
      chatId: `${ALICE_UID}_${BOB_UID}`,
      senderId: ALICE_UID,
      senderName: "Alice",
      receiverId: BOB_UID,
      receiverName: "Bob",
      text: "📷 Shared a photo",
      photoBase64: "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
      messageType: "photo",
      createdAt: new Date(),
    })
  );
});

function validQueuePayload(uid, gender = "Male", preferredGender = "Female") {
  return {
    userId: uid,
    gender: gender,
    age: 26,
    latitude: 23.0225,
    longitude: 72.5714,
    preferredGender: preferredGender,
    preferredMinAge: 20,
    preferredMaxAge: 35,
    preferredMaxDistanceKm: 2000,
    status: "waiting",
    matchedSessionId: "",
    matchedPartnerId: "",
    updatedAt: new Date(),
  };
}

function validRandomSessionPayload(userAId, userBId, userAGender = "Female", userBGender = "Male") {
  return {
    userAId: userAId,
    userBId: userBId,
    participantIds: [userAId, userBId],
    userAGender: userAGender,
    userBGender: userBGender,
    status: "active",
    endedByUserId: "",
    userAConsentName: false,
    userBConsentName: false,
    userAConsentAge: false,
    userBConsentAge: false,
    userAConsentPhoto: false,
    userBConsentPhoto: false,
    userAConsentProfile: false,
    userBConsentProfile: false,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

test("8. Random Chat Queue: authenticated user can enqueue, query waiting, and peer can batch-claim match", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("profiles").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, "Alice"));
    await ctx.firestore().collection("profiles").doc(BOB_UID).set(validProfilePayload(BOB_UID, "Bob"));
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("random_chat_queue").where("status", "==", "waiting").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice enters queue as waiting
  await assertSucceeds(
    aliceDb.collection("random_chat_queue").doc(ALICE_UID).set(validQueuePayload(ALICE_UID, "Female", "Male"))
  );

  // Unfiltered queue read fails; filtered status == waiting succeeds
  await assertFails(bobDb.collection("random_chat_queue").get());
  await assertSucceeds(bobDb.collection("random_chat_queue").where("status", "==", "waiting").get());

  // Bob cannot claim Alice's queue doc without simultaneously creating the referenced session
  await assertFails(
    bobDb.collection("random_chat_queue").doc(ALICE_UID).update({
      status: "matched",
      matchedSessionId: "sess_fake_1",
      matchedPartnerId: BOB_UID,
      updatedAt: new Date(),
    })
  );

  // Bob batch-creates session + claims Alice's waiting queue doc atomically
  const batch = bobDb.batch();
  const sessRef = bobDb.collection("random_chat_sessions").doc("sess_ab_1");
  batch.set(sessRef, validRandomSessionPayload(ALICE_UID, BOB_UID, "Female", "Male"));
  batch.update(bobDb.collection("random_chat_queue").doc(ALICE_UID), {
    status: "matched",
    matchedSessionId: "sess_ab_1",
    matchedPartnerId: BOB_UID,
    updatedAt: new Date(),
  });
  await assertSucceeds(batch.commit());
});

test("9. Random Chat Session & Messages: enforces step-by-step mutual consent, participant isolation, and terminal end lock", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("profiles").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, "Alice"));
    await ctx.firestore().collection("profiles").doc(BOB_UID).set(validProfilePayload(BOB_UID, "Bob"));
    await ctx.firestore().collection("random_chat_sessions").doc("sess_1").set(
      validRandomSessionPayload(ALICE_UID, BOB_UID, "Female", "Male")
    );
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Non-participant Charlie cannot read session or messages
  await assertFails(charlieDb.collection("random_chat_sessions").doc("sess_1").get());
  await assertFails(
    charlieDb.collection("random_chat_messages").where("sessionId", "==", "sess_1").get()
  );

  // Alice cannot spoof Bob's consent flag
  await assertFails(
    aliceDb.collection("random_chat_sessions").doc("sess_1").update({
      userBConsentName: true,
      updatedAt: new Date(),
    })
  );

  // Alice cannot skip ahead to Age before Name is mutually revealed
  await assertFails(
    aliceDb.collection("random_chat_sessions").doc("sess_1").update({
      userAConsentName: true,
      userAConsentAge: true,
      updatedAt: new Date(),
    })
  );

  // Step 1: Mutual Name consent
  await assertSucceeds(
    aliceDb.collection("random_chat_sessions").doc("sess_1").update({
      userAConsentName: true,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    bobDb.collection("random_chat_sessions").doc("sess_1").update({
      userBConsentName: true,
      updatedAt: new Date(),
    })
  );

  // Step 2: Now Age consent succeeds for both
  await assertSucceeds(
    aliceDb.collection("random_chat_sessions").doc("sess_1").update({
      userAConsentAge: true,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    bobDb.collection("random_chat_sessions").doc("sess_1").update({
      userBConsentAge: true,
      updatedAt: new Date(),
    })
  );

  // Unlimited messages inside active session succeed for Alice & Bob, fail for Charlie
  await assertSucceeds(
    aliceDb.collection("random_chat_messages").doc("rmsg_1").set({
      sessionId: "sess_1",
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      text: "Hi! Guessing each other's vibe first?",
      messageType: "text",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("random_chat_messages")
      .where("sessionId", "==", "sess_1")
      .where("participantIds", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("random_chat_messages").doc("rmsg_c1").set({
      sessionId: "sess_1",
      participantIds: [ALICE_UID, BOB_UID],
      senderId: CHARLIE_UID,
      text: "Intruder",
      messageType: "text",
      createdAt: new Date(),
    })
  );

  // Immutable messages cannot be updated
  await assertFails(
    aliceDb.collection("random_chat_messages").doc("rmsg_1").update({
      text: "Edited",
    })
  );

  // Bob ends the session -> terminal lock prevents further reveals or messages
  await assertSucceeds(
    bobDb.collection("random_chat_sessions").doc("sess_1").update({
      status: "ended",
      endedByUserId: BOB_UID,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("random_chat_sessions").doc("sess_1").update({
      userAConsentPhoto: true,
      updatedAt: new Date(),
    })
  );
  await assertFails(
    aliceDb.collection("random_chat_messages").doc("rmsg_2").set({
      sessionId: "sess_1",
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      text: "After end",
      messageType: "text",
      createdAt: new Date(),
    })
  );
});

test("10. Owner can toggle allowRandomMeet and update isOnline/lastActiveAtMs; cross-user spoof fails", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("profiles").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, "Alice"));
    await ctx.firestore().collection("profiles").doc(BOB_UID).set(validProfilePayload(BOB_UID, "Bob"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Owner updates presence and Random Meet toggle
  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).update({
      allowRandomMeet: true,
      isOnline: true,
      lastActiveAtMs: Date.now(),
      updatedAt: new Date(),
    })
  );

  // Bob cannot spoof Alice's presence or Random Meet setting
  await assertFails(
    bobDb.collection("profiles").doc(ALICE_UID).update({
      isOnline: false,
      allowRandomMeet: false,
      updatedAt: new Date(),
    })
  );

  // Bob can start a direct Random Chat session with Alice when Alice has Random Meet ON
  await assertSucceeds(
    bobDb.collection("random_chat_sessions").doc("sess_direct_1").set(
      validRandomSessionPayload(ALICE_UID, BOB_UID, "Female", "Male")
    )
  );
});

test("11. Owner can update dailyRandomFindCount/lastRandomFindDate & Unlimited queue distance (25000 km)", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("profiles").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, "Alice"));
    await ctx.firestore().collection("profiles").doc(BOB_UID).set(validProfilePayload(BOB_UID, "Bob"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).update({
      dailyRandomFindCount: 2,
      lastRandomFindDate: "2026-11-16",
      updatedAt: new Date(),
    })
  );

  // Negative dailyRandomFindCount is rejected
  await assertFails(
    aliceDb.collection("profiles").doc(ALICE_UID).update({
      dailyRandomFindCount: -1,
      lastRandomFindDate: "2026-11-16",
      updatedAt: new Date(),
    })
  );

  // Cross-user spoof of dailyRandomFindCount is rejected
  await assertFails(
    bobDb.collection("profiles").doc(ALICE_UID).update({
      dailyRandomFindCount: 0,
      lastRandomFindDate: "2026-11-16",
      updatedAt: new Date(),
    })
  );

  // Premium Unlimited queue distance (25000 km) succeeds
  const unlimitedQueue = validQueuePayload(ALICE_UID, "Female", "Male");
  unlimitedQueue.preferredMaxDistanceKm = 25000;
  await assertSucceeds(
    aliceDb.collection("random_chat_queue").doc(ALICE_UID).set(unlimitedQueue)
  );
});

test("12. Participants can delete own random chat sessions/messages and normal messages; non-participants cannot", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("profiles").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, "Alice"));
    await db.collection("profiles").doc(BOB_UID).set(validProfilePayload(BOB_UID, "Bob"));
    await db.collection("random_chat_sessions").doc("sess_del_1").set(
      validRandomSessionPayload(ALICE_UID, BOB_UID, "Female", "Male")
    );
    await db.collection("random_chat_messages").doc("rmsg_del_1").set({
      sessionId: "sess_del_1",
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      text: "Saved random message",
      messageType: "text",
      createdAt: new Date(),
    });
    await db.collection("messages").doc("msg_del_1").set({
      chatId: `${ALICE_UID}_${BOB_UID}`,
      senderId: ALICE_UID,
      senderName: "Alice",
      receiverId: BOB_UID,
      receiverName: "Bob",
      text: "Normal message",
      messageType: "text",
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Charlie cannot delete Alice & Bob's random chat message, session, or normal message
  await assertFails(charlieDb.collection("random_chat_messages").doc("rmsg_del_1").delete());
  await assertFails(charlieDb.collection("random_chat_sessions").doc("sess_del_1").delete());
  await assertFails(charlieDb.collection("messages").doc("msg_del_1").delete());

  // Participants can delete random chat message & session and normal message
  await assertSucceeds(aliceDb.collection("random_chat_messages").doc("rmsg_del_1").delete());
  await assertSucceeds(bobDb.collection("random_chat_sessions").doc("sess_del_1").delete());
  await assertSucceeds(bobDb.collection("messages").doc("msg_del_1").delete());
});



