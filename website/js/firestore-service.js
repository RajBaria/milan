import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut,
  onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  deleteDoc,
  query,
  where,
  onSnapshot,
  runTransaction,
  writeBatch,
  serverTimestamp
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";

import {
  FIREBASE_CONFIG,
  FIRESTORE_DATABASE_ID,
  ADMIN_SUPPORT_USER_ID
} from "./firebase-config.js";
import { buildChatId, generateShortId, timestampToMs } from "./utils.js";

const app = initializeApp(FIREBASE_CONFIG);
export const auth = getAuth(app);
export const db = getFirestore(app, FIRESTORE_DATABASE_ID);

const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: "select_account" });

export function listenAuthChanges(callback) {
  return onAuthStateChanged(auth, callback);
}

export async function resolveRedirectSignIn() {
  try {
    await getRedirectResult(auth);
  } catch (err) {
    console.warn("Redirect sign-in check:", err);
  }
}

export async function signInWithGoogle() {
  try {
    const result = await signInWithPopup(auth, googleProvider);
    return result.user;
  } catch (err) {
    if (
      err?.code === "auth/popup-blocked" ||
      err?.code === "auth/operation-not-supported-in-this-browser"
    ) {
      await signInWithRedirect(auth, googleProvider);
      return null;
    }
    throw err;
  }
}

export async function signOutGoogle() {
  await signOut(auth);
}

function requireUid() {
  const uid = auth.currentUser?.uid;
  if (!uid) throw new Error("You must sign in with Google first.");
  return uid;
}

// ==================== PROFILES ====================

export function observeMyProfile(uid, onData, onError) {
  const ref = doc(db, "profiles", uid);
  return onSnapshot(
    ref,
    (snap) => {
      onData(snap.exists() ? { id: snap.id, ...snap.data() } : null);
    },
    (err) => {
      console.error("observeMyProfile error:", err);
      onError?.(err);
    }
  );
}

export function observePublicProfiles(onData, onError) {
  const q = query(collection(db, "profiles"), where("visibility", "==", "public"));
  return onSnapshot(
    q,
    (snap) => {
      const list = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      onData(list);
    },
    (err) => {
      console.error("observePublicProfiles error:", err);
      onError?.(err);
    }
  );
}

export async function getProfileById(profileId) {
  const snap = await getDoc(doc(db, "profiles", profileId));
  return snap.exists() ? { id: snap.id, ...snap.data() } : null;
}

export async function createProfile(profile) {
  const uid = requireUid();
  const validPlan = ["Free", "Monthly", "Yearly", "FreeReward1Day"].includes(profile.premiumPlan)
    ? profile.premiumPlan
    : "Free";
  const validRole = profile.role === "admin" ? "admin" : "user";
  const validGender = ["Male", "Female", "Other"].includes(profile.gender) ? profile.gender : "Male";

  const payload = {
    userId: uid,
    firstName: String(profile.firstName || "User").trim().slice(0, 50) || "User",
    lastName: String(profile.lastName || "Member").trim().slice(0, 50) || "Member",
    birthDate: String(profile.birthDate || "2000-01-01").trim().slice(0, 20) || "2000-01-01",
    age: Math.min(100, Math.max(18, Number(profile.age) || 22)),
    gender: validGender,
    bio: String(profile.bio || "").trim().slice(0, 500),
    interests: (Array.isArray(profile.interests) ? profile.interests : [])
      .slice(0, 10)
      .map((i) => String(i).slice(0, 40)),
    photoBase64: String(profile.photoBase64 || "avatar_rose").slice(0, 700000) || "avatar_rose",
    latitude: Math.min(90, Math.max(-90, Number(profile.latitude) || 23.0225)),
    longitude: Math.min(180, Math.max(-180, Number(profile.longitude) || 72.5714)),
    locationName: String(profile.locationName || "Ahmedabad").trim().slice(0, 100) || "Ahmedabad",
    isPremium: Boolean(profile.isPremium),
    premiumPlan: validPlan,
    premiumExpiresAt: Math.max(0, Number(profile.premiumExpiresAt) || 0),
    dailyMessageCount: Math.max(0, Number(profile.dailyMessageCount) || 0),
    dailyRandomFindCount: Math.max(0, Number(profile.dailyRandomFindCount) || 0),
    lastRandomFindDate: String(profile.lastRandomFindDate || "").slice(0, 20),
    streakDays: Math.min(7, Math.max(0, Number(profile.streakDays) || 0)),
    todayUsageSeconds: Math.min(86400, Math.max(0, Number(profile.todayUsageSeconds) || 0)),
    lastUsageDate: String(profile.lastUsageDate || "").slice(0, 20),
    lastStreakCompletedDate: String(profile.lastStreakCompletedDate || "").slice(0, 40),
    role: validRole,
    visibility: profile.visibility === "hidden" ? "hidden" : "public",
    allowRandomMeet: profile.allowRandomMeet !== false,
    isOnline: true,
    lastActiveAtMs: Date.now(),
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  };

  if (Array.isArray(profile.galleryPhotos) && profile.galleryPhotos.length > 0) {
    payload.galleryPhotos = profile.galleryPhotos.slice(0, 6).map((p) => String(p).slice(0, 150000));
  }

  await setDoc(doc(db, "profiles", uid), payload);
  return uid;
}

export async function updateProfileDetails(profile) {
  const uid = requireUid();
  // Group A keys only per firestore.rules
  const payload = {
    firstName: String(profile.firstName || "User").trim().slice(0, 50) || "User",
    lastName: String(profile.lastName || "Member").trim().slice(0, 50) || "Member",
    birthDate: String(profile.birthDate || "2000-01-01").trim().slice(0, 20) || "2000-01-01",
    age: Math.min(100, Math.max(18, Number(profile.age) || 22)),
    gender: ["Male", "Female", "Other"].includes(profile.gender) ? profile.gender : "Male",
    bio: String(profile.bio || "").trim().slice(0, 500),
    interests: (Array.isArray(profile.interests) ? profile.interests : [])
      .slice(0, 10)
      .map((i) => String(i).slice(0, 40)),
    photoBase64: String(profile.photoBase64 || "avatar_rose").slice(0, 700000) || "avatar_rose",
    galleryPhotos: (Array.isArray(profile.galleryPhotos) ? profile.galleryPhotos : [])
      .slice(0, 6)
      .map((p) => String(p).slice(0, 150000)),
    latitude: Math.min(90, Math.max(-90, Number(profile.latitude) || 23.0225)),
    longitude: Math.min(180, Math.max(-180, Number(profile.longitude) || 72.5714)),
    locationName: String(profile.locationName || "Ahmedabad").trim().slice(0, 100) || "Ahmedabad",
    visibility: profile.visibility === "hidden" ? "hidden" : "public",
    allowRandomMeet: profile.allowRandomMeet !== false,
    updatedAt: serverTimestamp()
  };

  await updateDoc(doc(db, "profiles", uid), payload);
}

export async function updateUserLocation(latitude, longitude, locationName) {
  const uid = requireUid();
  await updateDoc(doc(db, "profiles", uid), {
    latitude: Math.min(90, Math.max(-90, Number(latitude) || 23.0225)),
    longitude: Math.min(180, Math.max(-180, Number(longitude) || 72.5714)),
    locationName: String(locationName || "Nearby").trim().slice(0, 100) || "Nearby",
    updatedAt: serverTimestamp()
  });
}

export async function updateGalleryPhotos(galleryPhotos) {
  const uid = requireUid();
  await updateDoc(doc(db, "profiles", uid), {
    galleryPhotos: (galleryPhotos || []).slice(0, 6).map((p) => String(p).slice(0, 150000)),
    updatedAt: serverTimestamp()
  });
}

export async function updateUserPresence(isOnline) {
  const uid = auth.currentUser?.uid;
  if (!uid) return;
  await updateDoc(doc(db, "profiles", uid), {
    isOnline: Boolean(isOnline),
    lastActiveAtMs: Date.now(),
    updatedAt: serverTimestamp()
  });
}

export async function setRandomMeetEnabled(enabled) {
  const uid = requireUid();
  await updateDoc(doc(db, "profiles", uid), {
    allowRandomMeet: Boolean(enabled),
    updatedAt: serverTimestamp()
  });
}

export async function recordDailyRandomFind(todayDate, newCount) {
  const uid = requireUid();
  await updateDoc(doc(db, "profiles", uid), {
    dailyRandomFindCount: Math.max(0, newCount),
    lastRandomFindDate: String(todayDate).slice(0, 20),
    updatedAt: serverTimestamp()
  });
}

export async function updateUsageStreakProgress({
  currentProfile,
  newStreakDays,
  newTodaySeconds,
  todayDate,
  completedDate,
  grantFreeRewardDay
}) {
  const uid = requireUid();
  const oneDayExpiry = Date.now() + 24 * 60 * 60 * 1000;
  await updateDoc(doc(db, "profiles", uid), {
    isPremium: grantFreeRewardDay ? true : Boolean(currentProfile.isPremium),
    premiumPlan: grantFreeRewardDay ? "FreeReward1Day" : currentProfile.premiumPlan || "Free",
    premiumExpiresAt: grantFreeRewardDay ? oneDayExpiry : Number(currentProfile.premiumExpiresAt || 0),
    streakDays: Math.min(7, Math.max(0, newStreakDays)),
    todayUsageSeconds: Math.min(86400, Math.max(0, newTodaySeconds)),
    lastUsageDate: String(todayDate).slice(0, 20),
    lastStreakCompletedDate: String(completedDate).slice(0, 40),
    updatedAt: serverTimestamp()
  });
}

export async function downgradeToFreePlan() {
  const uid = requireUid();
  await updateDoc(doc(db, "profiles", uid), {
    isPremium: false,
    premiumPlan: "Free",
    premiumExpiresAt: 0,
    paymentStatus: "None",
    pendingPlan: "",
    updatedAt: serverTimestamp()
  });
}

export async function submitUpiPaymentForVerification(planName, amountInr, upiTxnId) {
  const uid = requireUid();
  const validPlan = ["Monthly", "Yearly"].includes(planName) ? planName : "Monthly";
  await updateDoc(doc(db, "profiles", uid), {
    lastPaymentAmountInr: Math.min(100000, Math.max(0, Number(amountInr) || 199)),
    lastPaymentDateMs: Date.now(),
    lastUpiTxnId: String(upiTxnId).trim().slice(0, 64),
    paymentStatus: "PendingVerification",
    pendingPlan: validPlan,
    updatedAt: serverTimestamp()
  });
}

// ==================== ADMIN OPERATIONS ====================

export async function adminVerifyUserProfile(targetUserId, approve) {
  requireUid();
  const updates = {
    isProfileVerified: Boolean(approve),
    verificationStatus: approve ? "Verified" : "Rejected",
    updatedAt: serverTimestamp()
  };
  if (!approve) {
    updates.verificationVideoFrames = [];
  }
  await updateDoc(doc(db, "profiles", targetUserId), updates);
}

export async function adminUpdateUserPremium(targetUserId, planName, expiresAtMillis, paymentStatus, pendingPlan) {
  requireUid();
  const validPlan = ["Free", "Monthly", "Yearly", "FreeReward1Day"].includes(planName) ? planName : "Free";
  const updates = {
    isPremium: validPlan !== "Free",
    premiumPlan: validPlan,
    premiumExpiresAt: Math.max(0, Number(expiresAtMillis) || 0),
    updatedAt: serverTimestamp()
  };
  if (paymentStatus && ["None", "PendingVerification", "Verified", "Rejected"].includes(paymentStatus)) {
    updates.paymentStatus = paymentStatus;
  }
  if (pendingPlan !== undefined && pendingPlan !== null) {
    updates.pendingPlan = String(pendingPlan).slice(0, 30);
  }
  await updateDoc(doc(db, "profiles", targetUserId), updates);
}

export async function adminDeleteUserProfile(targetUserId) {
  requireUid();
  await deleteDoc(doc(db, "profiles", targetUserId));
}

// ==================== LIKES & BLOCKS ====================

export function observeSentLikes(uid, onData) {
  const q = query(collection(db, "likes"), where("fromUserId", "==", uid));
  return onSnapshot(q, (snap) => {
    onData(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
  });
}

export function observeReceivedLikes(uid, onData) {
  const q = query(collection(db, "likes"), where("toUserId", "==", uid));
  return onSnapshot(q, (snap) => {
    onData(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
  });
}

export async function sendLike(fromUserName, toUserId, toUserName) {
  const uid = requireUid();
  if (uid === toUserId) throw new Error("Cannot like your own profile");
  const likeId = `${uid}_${toUserId}`;
  await setDoc(doc(db, "likes", likeId), {
    fromUserId: uid,
    toUserId,
    fromUserName: String(fromUserName || "User").trim().slice(0, 100) || "User",
    toUserName: String(toUserName || "User").trim().slice(0, 100) || "User",
    createdAt: serverTimestamp()
  });
  return likeId;
}

export async function removeLike(toUserId) {
  const uid = requireUid();
  await deleteDoc(doc(db, "likes", `${uid}_${toUserId}`));
}

export function observeBlockedUsers(uid, onData) {
  const q = query(collection(db, "blocks"), where("userId", "==", uid));
  return onSnapshot(q, (snap) => {
    onData(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
  });
}

export async function blockUser(blockedUserId, blockedUserName) {
  const uid = requireUid();
  if (uid === blockedUserId) throw new Error("Cannot block yourself");
  const blockId = `${uid}_${blockedUserId}`;
  await setDoc(doc(db, "blocks", blockId), {
    userId: uid,
    blockedUserId,
    blockedUserName: String(blockedUserName || "User").trim().slice(0, 100) || "User",
    createdAt: serverTimestamp()
  });
}

export async function unblockUser(blockedUserId) {
  const uid = requireUid();
  await deleteDoc(doc(db, "blocks", `${uid}_${blockedUserId}`));
}

// ==================== DIRECT MESSAGES ====================

export function observeAllUserMessages(uid, isAdmin, onData) {
  let sent = [];
  let received = [];
  let support = [];

  const emitCombined = () => {
    const map = new Map();
    [...sent, ...received, ...support].forEach((m) => map.set(m.id, m));
    const list = Array.from(map.values()).sort(
      (a, b) => timestampToMs(a.createdAt) - timestampToMs(b.createdAt)
    );
    onData(list);
  };

  const unsubSent = onSnapshot(
    query(collection(db, "messages"), where("senderId", "==", uid)),
    (snap) => {
      sent = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      emitCombined();
    }
  );

  const unsubRecv = onSnapshot(
    query(collection(db, "messages"), where("receiverId", "==", uid)),
    (snap) => {
      received = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
      emitCombined();
    }
  );

  let unsubSupport = () => {};
  if (isAdmin) {
    unsubSupport = onSnapshot(
      query(collection(db, "messages"), where("receiverId", "==", ADMIN_SUPPORT_USER_ID)),
      (snap) => {
        support = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
        emitCombined();
      },
      () => {}
    );
  }

  return () => {
    unsubSent();
    unsubRecv();
    unsubSupport();
  };
}

export async function sendMessage({
  senderName,
  receiverId,
  receiverName,
  text,
  currentDailyCount,
  photoBase64 = "",
  messageType = "text",
  skipDailyIncrement = false
}) {
  const uid = requireUid();
  if (uid === receiverId) throw new Error("Cannot message yourself");
  const validType = ["text", "photo", "audio_call", "video_call"].includes(messageType)
    ? messageType
    : "text";
  const cleanText =
    String(text || "").trim().slice(0, 1000) ||
    (validType === "photo" ? "📷 Shared a photo" : "");
  if (!cleanText) throw new Error("Message text cannot be empty");

  const chatId = buildChatId(uid, receiverId);
  const messageId = generateShortId("msg");

  const payload = {
    chatId,
    senderId: uid,
    senderName: String(senderName || "User").trim().slice(0, 100) || "User",
    receiverId,
    receiverName: String(receiverName || "User").trim().slice(0, 100) || "User",
    text: cleanText,
    messageType: validType,
    createdAt: serverTimestamp()
  };

  if (photoBase64 && photoBase64.trim()) {
    payload.photoBase64 = photoBase64.slice(0, 700000);
  }

  await setDoc(doc(db, "messages", messageId), payload);

  if (!skipDailyIncrement && (validType === "text" || validType === "photo")) {
    await updateDoc(doc(db, "profiles", uid), {
      dailyMessageCount: Math.max(0, Number(currentDailyCount || 0) + 1),
      updatedAt: serverTimestamp()
    });
  }
  return messageId;
}

export async function deleteNormalChatThread(otherUserId) {
  const uid = requireUid();
  const chatId = buildChatId(uid, otherUserId);
  const sentSnap = await getDocs(
    query(collection(db, "messages"), where("chatId", "==", chatId), where("senderId", "==", uid))
  );
  const recvSnap = await getDocs(
    query(collection(db, "messages"), where("chatId", "==", chatId), where("receiverId", "==", uid))
  );
  const refs = new Map();
  sentSnap.docs.forEach((d) => refs.set(d.id, d.ref));
  recvSnap.docs.forEach((d) => refs.set(d.id, d.ref));

  const allRefs = Array.from(refs.values());
  for (let i = 0; i < allRefs.length; i += 400) {
    const batch = writeBatch(db);
    allRefs.slice(i, i + 400).forEach((r) => batch.delete(r));
    await batch.commit();
  }
}

// ==================== RANDOM CHAT ====================

export async function enqueueForRandomChat(myProfile, prefs) {
  const uid = requireUid();
  const minAge = Math.min(65, Math.max(18, Number(prefs.minAge) || 18));
  const maxAge = Math.min(65, Math.max(minAge, Number(prefs.maxAge) || 45));
  const maxDist = Math.min(25000, Math.max(10, Number(prefs.maxDistanceKm) || 500));
  const validGender = ["Male", "Female", "Other"].includes(myProfile.gender) ? myProfile.gender : "Male";
  const validPref = ["All", "Male", "Female"].includes(prefs.preferredGender) ? prefs.preferredGender : "All";

  await setDoc(doc(db, "random_chat_queue", uid), {
    userId: uid,
    gender: validGender,
    age: Math.min(100, Math.max(18, Number(myProfile.age) || 22)),
    latitude: Math.min(90, Math.max(-90, Number(myProfile.latitude) || 23.0225)),
    longitude: Math.min(180, Math.max(-180, Number(myProfile.longitude) || 72.5714)),
    preferredGender: validPref,
    preferredMinAge: minAge,
    preferredMaxAge: maxAge,
    preferredMaxDistanceKm: maxDist,
    status: "waiting",
    matchedSessionId: "",
    matchedPartnerId: "",
    updatedAt: serverTimestamp()
  });
}

export async function leaveRandomChatQueue() {
  const uid = auth.currentUser?.uid;
  if (!uid) return;
  try {
    await deleteDoc(doc(db, "random_chat_queue", uid));
  } catch {
    // Ignore if already deleted
  }
}

export function observeWaitingRandomQueueEntries(onData) {
  const q = query(collection(db, "random_chat_queue"), where("status", "==", "waiting"));
  return onSnapshot(q, (snap) => {
    onData(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
  });
}

export async function tryPairWithWaitingUser(myProfile, partnerEntry) {
  const uid = requireUid();
  if (uid === partnerEntry.userId) throw new Error("Cannot match with yourself");

  const sessionId = generateShortId("rnd_sess");
  const myIsA = uid < partnerEntry.userId;
  const userAId = myIsA ? uid : partnerEntry.userId;
  const userBId = myIsA ? partnerEntry.userId : uid;
  const myGender = ["Male", "Female", "Other"].includes(myProfile.gender) ? myProfile.gender : "Male";
  const partnerGender = ["Male", "Female", "Other"].includes(partnerEntry.gender) ? partnerEntry.gender : "Female";
  const userAGender = myIsA ? myGender : partnerGender;
  const userBGender = myIsA ? partnerGender : myGender;
  const participantIds = [userAId, userBId];

  const partnerQueueRef = doc(db, "random_chat_queue", partnerEntry.userId);
  const myQueueRef = doc(db, "random_chat_queue", uid);
  const sessionRef = doc(db, "random_chat_sessions", sessionId);

  await runTransaction(db, async (tx) => {
    const partnerSnap = await tx.get(partnerQueueRef);
    if (!partnerSnap.exists() || partnerSnap.data()?.status !== "waiting") {
      throw new Error("Partner is no longer waiting in queue");
    }
    const mySnap = await tx.get(myQueueRef);
    if (mySnap.exists() && mySnap.data()?.status === "matched" && mySnap.data()?.matchedSessionId) {
      throw new Error("Already matched");
    }

    tx.set(sessionRef, {
      userAId,
      userBId,
      participantIds,
      userAGender,
      userBGender,
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
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    });

    tx.update(partnerQueueRef, {
      status: "matched",
      matchedSessionId: sessionId,
      matchedPartnerId: uid,
      updatedAt: serverTimestamp()
    });

    if (mySnap.exists()) {
      tx.update(myQueueRef, {
        status: "matched",
        matchedSessionId: sessionId,
        matchedPartnerId: partnerEntry.userId,
        updatedAt: serverTimestamp()
      });
    }
  });

  return {
    id: sessionId,
    userAId,
    userBId,
    participantIds,
    userAGender,
    userBGender,
    status: "active",
    endedByUserId: "",
    userAConsentName: false,
    userBConsentName: false,
    userAConsentAge: false,
    userBConsentAge: false,
    userAConsentPhoto: false,
    userBConsentPhoto: false,
    userAConsentProfile: false,
    userBConsentProfile: false
  };
}

export async function createDirectRandomSession(myProfile, partnerProfile) {
  const uid = requireUid();
  if (uid === partnerProfile.userId) throw new Error("Cannot match with yourself");

  const sessionId = generateShortId("rnd_sess");
  const myIsA = uid < partnerProfile.userId;
  const userAId = myIsA ? uid : partnerProfile.userId;
  const userBId = myIsA ? partnerProfile.userId : uid;
  const myGender = ["Male", "Female", "Other"].includes(myProfile.gender) ? myProfile.gender : "Male";
  const partnerGender = ["Male", "Female", "Other"].includes(partnerProfile.gender)
    ? partnerProfile.gender
    : "Female";
  const userAGender = myIsA ? myGender : partnerGender;
  const userBGender = myIsA ? partnerGender : myGender;
  const participantIds = [userAId, userBId];

  await setDoc(doc(db, "random_chat_sessions", sessionId), {
    userAId,
    userBId,
    participantIds,
    userAGender,
    userBGender,
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
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });

  await leaveRandomChatQueue();

  return {
    id: sessionId,
    userAId,
    userBId,
    participantIds,
    userAGender,
    userBGender,
    status: "active",
    endedByUserId: "",
    userAConsentName: false,
    userBConsentName: false,
    userAConsentAge: false,
    userBConsentAge: false,
    userAConsentPhoto: false,
    userBConsentPhoto: false,
    userAConsentProfile: false,
    userBConsentProfile: false
  };
}

export function observeMyActiveRandomSessions(uid, onData) {
  const q = query(
    collection(db, "random_chat_sessions"),
    where("participantIds", "array-contains", uid)
  );
  return onSnapshot(q, (snap) => {
    const list = snap.docs
      .map((d) => ({ id: d.id, ...d.data() }))
      .filter((s) => s.status === "active")
      .sort((a, b) => timestampToMs(b.createdAt) - timestampToMs(a.createdAt));
    onData(list);
  });
}

export function observeRandomSessionById(sessionId, onData) {
  return onSnapshot(doc(db, "random_chat_sessions", sessionId), (snap) => {
    onData(snap.exists() ? { id: snap.id, ...snap.data() } : null);
  });
}

export function observeRandomChatMessages(sessionId, uid, onData) {
  const q = query(
    collection(db, "random_chat_messages"),
    where("sessionId", "==", sessionId),
    where("participantIds", "array-contains", uid)
  );
  return onSnapshot(q, (snap) => {
    const list = snap.docs
      .map((d) => ({ id: d.id, ...d.data() }))
      .sort((a, b) => timestampToMs(a.createdAt) - timestampToMs(b.createdAt));
    onData(list);
  });
}

export async function submitRandomRevealConsent(session, step) {
  const uid = requireUid();
  const amUserA = uid === session.userAId;
  let fieldName = "";
  if (step === "NAME") fieldName = amUserA ? "userAConsentName" : "userBConsentName";
  if (step === "AGE") fieldName = amUserA ? "userAConsentAge" : "userBConsentAge";
  if (step === "PHOTO") fieldName = amUserA ? "userAConsentPhoto" : "userBConsentPhoto";
  if (step === "PROFILE") fieldName = amUserA ? "userAConsentProfile" : "userBConsentProfile";
  if (!fieldName) return;

  await updateDoc(doc(db, "random_chat_sessions", session.id), {
    [fieldName]: true,
    updatedAt: serverTimestamp()
  });
}

export async function sendRandomChatMessage(session, text, messageType = "text") {
  const uid = requireUid();
  const cleanText = String(text || "").trim().slice(0, 1000);
  if (!cleanText) throw new Error("Message cannot be empty");
  const msgId = generateShortId("rmsg");
  await setDoc(doc(db, "random_chat_messages", msgId), {
    sessionId: session.id,
    participantIds: session.participantIds,
    senderId: uid,
    text: cleanText,
    messageType: messageType === "system" ? "system" : "text",
    createdAt: serverTimestamp()
  });
  return msgId;
}

export async function endRandomChatSession(sessionId) {
  const uid = requireUid();
  if (sessionId) {
    await updateDoc(doc(db, "random_chat_sessions", sessionId), {
      status: "ended",
      endedByUserId: uid,
      updatedAt: serverTimestamp()
    });
  }
  await leaveRandomChatQueue();
}

export async function deleteRandomChatSession(sessionId) {
  const uid = requireUid();
  if (!sessionId) return;
  const msgSnap = await getDocs(
    query(
      collection(db, "random_chat_messages"),
      where("sessionId", "==", sessionId),
      where("participantIds", "array-contains", uid)
    )
  );
  const refs = msgSnap.docs.map((d) => d.ref);
  for (let i = 0; i < refs.length; i += 400) {
    const batch = writeBatch(db);
    refs.slice(i, i + 400).forEach((r) => batch.delete(r));
    await batch.commit();
  }
  await deleteDoc(doc(db, "random_chat_sessions", sessionId));
  await leaveRandomChatQueue();
}
