import {
  ADMIN_EMAIL,
  ADMIN_SUPPORT_USER_ID,
  FREE_MAX_PROFILE_VIEWS,
  MAX_FREE_DISTANCE_KM,
  MAX_PREMIUM_DISTANCE_KM
} from "./firebase-config.js";
import {
  calculateDistanceKm,
  extractGoogleAccountIdentity,
  fullNameOf,
  isCurrentlyOnline,
  isVerifiedWithBlueTick,
  timestampToMs
} from "./utils.js";

const savedTheme = localStorage.getItem("milan_color_theme") || "pink";
const savedMode = localStorage.getItem("milan_dark_mode") === "true" ? "dark" : "light";

function loadSavedPreLoginLocation() {
  try {
    const raw = sessionStorage.getItem("milan_pre_login_location");
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed.latitude === "number" && typeof parsed.longitude === "number") {
        return {
          status: "granted",
          granted: true,
          latitude: parsed.latitude,
          longitude: parsed.longitude,
          locationName: String(parsed.locationName || "GPS Active"),
          error: ""
        };
      }
    }
  } catch {}
  return {
    status: "idle", // idle | checking | granted | denied | error | unsupported
    granted: false,
    latitude: null,
    longitude: null,
    locationName: "",
    error: ""
  };
}

export const store = {
  authReady: false,
  user: null, // Firebase User
  profileLoaded: false,
  myProfile: null,
  googleIdentity: null,
  isAdmin: false,

  publicProfiles: [],
  sentLikes: [],
  receivedLikes: [],
  blockedUsers: [],
  messages: [],
  myActiveRandomSessions: [],

  // UI navigation & preferences
  activeTab: "discover", // discover | likes | random | chats | profile
  likesSubTab: "received", // received | sent
  discoverViewMode: "list", // list | tiles
  filters: {
    selectedGender: "All",
    maxDistanceKm: MAX_FREE_DISTANCE_KM,
    minAge: 18,
    maxAge: 60,
    selectedInterest: "All",
    searchQuery: ""
  },
  activeChatPartnerId: null,
  activeChatPartnerSnapshot: null,

  // Random Chat state
  randomPrefs: {
    preferredGender: "All",
    minAge: 18,
    maxAge: 45,
    maxDistanceKm: 500
  },
  randomStatus: "IDLE", // IDLE | SEARCHING | CONNECTED | PARTNER_LEFT
  activeRandomSessionId: null,
  activeRandomSession: null,
  randomMessages: [],
  fetchedRandomPartner: null,

  // Theme & notifications
  colorTheme: savedTheme,
  darkMode: savedMode === "dark",
  notifications: [],
  toasts: [],
  sessionUsageSeconds: 0,
  locationSyncStatus: "Browser Location • Click Sync to update",
  preLoginLocation: loadSavedPreLoginLocation(),
  editProfileOpen: false
};

export function setPreLoginLocation(nextState) {
  store.preLoginLocation = { ...store.preLoginLocation, ...nextState };
  try {
    if (store.preLoginLocation.granted && store.preLoginLocation.latitude != null) {
      sessionStorage.setItem(
        "milan_pre_login_location",
        JSON.stringify({
          latitude: store.preLoginLocation.latitude,
          longitude: store.preLoginLocation.longitude,
          locationName: store.preLoginLocation.locationName
        })
      );
    }
  } catch {}
  notify();
}

const listeners = new Set();

export function subscribe(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function notify() {
  listeners.forEach((fn) => fn(store));
}

export function applyThemeToDom() {
  document.documentElement.setAttribute("data-theme", store.colorTheme);
  document.documentElement.setAttribute("data-mode", store.darkMode ? "dark" : "light");
  localStorage.setItem("milan_color_theme", store.colorTheme);
  localStorage.setItem("milan_dark_mode", String(store.darkMode));
}

export function setTheme(colorTheme, darkMode) {
  if (colorTheme) store.colorTheme = colorTheme;
  if (typeof darkMode === "boolean") store.darkMode = darkMode;
  applyThemeToDom();
  notify();
}

export function pushToast(title, body = "", variant = "info") {
  const id = `t_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`;
  store.toasts = [{ id, title, body, variant }, ...store.toasts].slice(0, 4);
  notify();
  setTimeout(() => {
    store.toasts = store.toasts.filter((t) => t.id !== id);
    notify();
  }, 4500);
}

export function recordNotification({ id, title, body, isReward = false, timestampMs = Date.now(), emitToast = true }) {
  const dedupeId = id || `${title}_${body}`.toLowerCase();
  if (store.notifications.some((n) => n.id === dedupeId)) return;
  store.notifications = [
    { id: dedupeId, title, body, isReward, timestampMs, isRead: !emitToast },
    ...store.notifications
  ].slice(0, 60);
  if (emitToast) {
    pushToast(title, body, isReward ? "reward" : "info");
  }
  notify();
}

export function markAllNotificationsRead() {
  store.notifications = store.notifications.map((n) => ({ ...n, isRead: true }));
  notify();
}

export function resetStoreForAuth(user) {
  store.authReady = true;
  store.user = user;
  if (!user) {
    store.profileLoaded = false;
    store.myProfile = null;
    store.googleIdentity = null;
    store.isAdmin = false;
    store.publicProfiles = [];
    store.sentLikes = [];
    store.receivedLikes = [];
    store.blockedUsers = [];
    store.messages = [];
    store.myActiveRandomSessions = [];
    store.activeChatPartnerId = null;
    store.activeChatPartnerSnapshot = null;
    store.activeRandomSessionId = null;
    store.activeRandomSession = null;
    store.randomMessages = [];
    store.randomStatus = "IDLE";
    store.notifications = [];
  } else {
    store.isAdmin =
      String(user.email || "").trim().toLowerCase() === ADMIN_EMAIL.toLowerCase();
    store.googleIdentity = extractGoogleAccountIdentity(
      user.displayName,
      user.email,
      user.uid
    );
  }
  notify();
}

// ==================== COMPUTED SELECTORS ====================

export function isAdminSupportPartner(profile) {
  if (!profile) return false;
  return (
    profile.userId === ADMIN_SUPPORT_USER_ID ||
    profile.role === "admin" ||
    String(profile.firstName || "").toLowerCase() === "admin"
  );
}

export function buildAdminSupportProfile() {
  const existingAdmin = store.publicProfiles.find(
    (p) => p.role === "admin" || String(p.firstName || "").toLowerCase() === "admin"
  );
  if (existingAdmin && existingAdmin.userId !== store.user?.uid) {
    return existingAdmin;
  }
  return {
    id: ADMIN_SUPPORT_USER_ID,
    userId: ADMIN_SUPPORT_USER_ID,
    firstName: "admin",
    lastName: "Support",
    age: 28,
    gender: "Male",
    bio: "Official Milan Admin Support • Ask any question about your account, payments, or features.",
    locationName: "Milan Help Desk",
    photoBase64: "avatar_lavender",
    isPremium: true,
    role: "admin"
  };
}

export function getDiscoverProfiles() {
  const my = store.myProfile;
  const uid = store.user?.uid;
  const myLat = my?.latitude ?? 23.0225;
  const myLon = my?.longitude ?? 72.5714;
  const myGender = my?.gender || store.googleIdentity?.gender || "Male";
  const isPrem = Boolean(my?.isPremium);
  const blockedIds = new Set(store.blockedUsers.map((b) => b.blockedUserId));

  const requiredOppositeGender =
    myGender.toLowerCase() === "male"
      ? "Female"
      : myGender.toLowerCase() === "female"
        ? "Male"
        : null;

  const f = store.filters;
  const effMaxDist = isPrem
    ? f.maxDistanceKm >= MAX_PREMIUM_DISTANCE_KM
      ? Infinity
      : Math.max(5, f.maxDistanceKm)
    : Math.min(MAX_FREE_DISTANCE_KM, Math.max(5, f.maxDistanceKm));

  const searchLower = f.searchQuery.trim().toLowerCase();

  const seen = new Set();
  const list = store.publicProfiles
    .filter((p) => {
      if (!p.userId || p.userId === uid || blockedIds.has(p.userId) || seen.has(p.userId)) {
        return false;
      }
      seen.add(p.userId);
      return true;
    })
    .map((p) => ({
      profile: p,
      distanceKm: calculateDistanceKm(myLat, myLon, p.latitude ?? 23.0225, p.longitude ?? 72.5714)
    }))
    .filter(({ profile, distanceKm }) => {
      const matchesGender = requiredOppositeGender
        ? String(profile.gender || "").toLowerCase() === requiredOppositeGender.toLowerCase()
        : f.selectedGender === "All" ||
          String(profile.gender || "").toLowerCase() === f.selectedGender.toLowerCase();

      const matchesDist = effMaxDist === Infinity || distanceKm <= effMaxDist;
      const age = Number(profile.age) || 22;
      const matchesAge = age >= f.minAge && age <= f.maxAge;
      const matchesInterest =
        f.selectedInterest === "All" ||
        (profile.interests || []).some(
          (i) => String(i).toLowerCase() === f.selectedInterest.toLowerCase()
        );
      const matchesSearch =
        !searchLower ||
        fullNameOf(profile).toLowerCase().includes(searchLower) ||
        String(profile.locationName || "").toLowerCase().includes(searchLower) ||
        String(profile.bio || "").toLowerCase().includes(searchLower);

      return matchesGender && matchesDist && matchesAge && matchesInterest && matchesSearch;
    })
    .sort((a, b) => a.distanceKm - b.distanceKm);

  return isPrem ? list : list.slice(0, FREE_MAX_PROFILE_VIEWS);
}

export function getConversations() {
  const uid = store.user?.uid;
  if (!uid) return [];
  const blockedIds = new Set(store.blockedUsers.map((b) => b.blockedUserId));
  const profileMap = new Map(store.publicProfiles.map((p) => [p.userId, p]));

  const groups = new Map();
  for (const msg of store.messages) {
    if (!groups.has(msg.chatId)) groups.set(msg.chatId, []);
    groups.get(msg.chatId).push(msg);
  }

  const summaries = [];
  for (const [chatId, msgs] of groups.entries()) {
    const last = msgs[msgs.length - 1];
    if (!last) continue;
    const isSelfSender =
      last.senderId === uid || (store.isAdmin && last.senderId === ADMIN_SUPPORT_USER_ID);
    const otherId = isSelfSender ? last.receiverId : last.senderId;
    if (blockedIds.has(otherId)) continue;
    const otherName = isSelfSender ? last.receiverName : last.senderName;
    const resolvedProfile =
      profileMap.get(otherId) ||
      (otherId === ADMIN_SUPPORT_USER_ID ? buildAdminSupportProfile() : null);

    summaries.push({
      chatId,
      otherUserId: otherId,
      otherUserName: otherName,
      otherUserProfile: resolvedProfile,
      lastMessageText: last.text,
      lastMessageMs: timestampToMs(last.createdAt),
      lastMessageTimestamp: last.createdAt,
      messageCount: msgs.length
    });
  }

  return summaries.sort((a, b) => b.lastMessageMs - a.lastMessageMs);
}

export function getActiveChatPartnerProfile() {
  const id = store.activeChatPartnerId;
  if (!id) return null;
  const fromPublic = store.publicProfiles.find((p) => p.userId === id);
  if (fromPublic) return fromPublic;
  if (id === ADMIN_SUPPORT_USER_ID) return buildAdminSupportProfile();
  if (store.activeChatPartnerSnapshot?.userId === id) return store.activeChatPartnerSnapshot;
  return {
    userId: id,
    firstName: "Milan",
    lastName: "Member",
    age: 24,
    gender: "Other",
    locationName: "Nearby",
    photoBase64: "avatar_rose"
  };
}

export function getRandomSessionReveals(session) {
  if (!session) {
    return { isName: false, isAge: false, isPhoto: false, isProfile: false };
  }
  const isName = Boolean(session.userAConsentName && session.userBConsentName);
  const isAge = Boolean(isName && session.userAConsentAge && session.userBConsentAge);
  const isPhoto = Boolean(isAge && session.userAConsentPhoto && session.userBConsentPhoto);
  const isProfile = Boolean(isPhoto && session.userAConsentProfile && session.userBConsentProfile);
  return { isName, isAge, isPhoto, isProfile };
}

export function getMaskedRandomPartner() {
  const session = store.activeRandomSession;
  const uid = store.user?.uid;
  if (!session || !uid) return null;

  const amA = session.userAId === uid;
  const partnerId = amA ? session.userBId : session.userAId;
  const partnerGender = amA ? session.userBGender : session.userAGender;
  const partnerProfile =
    store.publicProfiles.find((p) => p.userId === partnerId) ||
    (store.fetchedRandomPartner?.userId === partnerId ? store.fetchedRandomPartner : null);

  const reveals = getRandomSessionReveals(session);
  const distKm =
    store.myProfile && partnerProfile
      ? calculateDistanceKm(
          store.myProfile.latitude ?? 23.0225,
          store.myProfile.longitude ?? 72.5714,
          partnerProfile.latitude ?? 23.0225,
          partnerProfile.longitude ?? 72.5714
        )
      : null;

  return {
    partnerId,
    gender: partnerGender,
    isOnline: isCurrentlyOnline(partnerProfile),
    revealedName: reveals.isName ? fullNameOf(partnerProfile) : null,
    revealedAge: reveals.isAge ? partnerProfile?.age ?? null : null,
    revealedPhotoBase64: reveals.isPhoto ? partnerProfile?.photoBase64 ?? "avatar_rose" : null,
    isVerified: reveals.isPhoto && isVerifiedWithBlueTick(partnerProfile),
    revealedFullProfile: reveals.isProfile ? partnerProfile : null,
    distanceKm: reveals.isProfile ? distKm : null
  };
}
