import {
  DAILY_STREAK_TARGET_SECONDS,
  FREE_DAILY_MESSAGE_LIMIT,
  FREE_DAILY_RANDOM_FIND_LIMIT,
  FREE_MAX_LIKES_LIMIT,
  MAX_FREE_DISTANCE_KM,
  MAX_PREMIUM_DISTANCE_KM,
  STREAK_REQUIRED_DAYS,
  UNLIMITED_DISTANCE_KM
} from "./firebase-config.js";
import * as api from "./firestore-service.js";
import {
  applyThemeToDom,
  buildAdminSupportProfile,
  getConversations,
  isAdminSupportPartner,
  markAllNotificationsRead,
  notify,
  pushToast,
  recordNotification,
  resetStoreForAuth,
  setPreLoginLocation,
  store,
  subscribe
} from "./state.js";
import {
  calculateDistanceKm,
  escapeHtml,
  fullNameOf,
  isCurrentlyOnline,
  isPaidPremium,
  renderAvatarHtml,
  timestampToMs,
  todayDateString,
  yesterdayDateString
} from "./utils.js";
import { renderAuthLanding, renderOnboardingOrEdit } from "./views/auth-onboarding.js";
import { renderDiscoverView } from "./views/discover.js";
import { renderChatsView, renderLikesView } from "./views/likes-chats.js";
import { showLikeGateModal, showNotificationsModal, showThemeModal } from "./views/modals.js";
import { renderProfileAndPremiumView } from "./views/profile-premium.js";
import { renderRandomChatView } from "./views/random-chat.js";

applyThemeToDom();

let dataUnsubs = [];
let activeRandomUnsubs = [];
let matchmakingCleanup = null;
let heartbeatTimer = null;
let streakTimer = null;
let lastEndedRandomSessionId = null;

// Track initial snapshot loads for toast notifications
let initialLikesLoaded = false;
let knownLikeIds = new Set();
let initialMessagesLoaded = false;
let knownMsgIds = new Set();
let initialRandomSessionsLoaded = false;
let knownRandomSessionIds = new Set();
const appBootTimeMs = Date.now();
let preLoginLocationSyncedForUid = null;
let autoPreLoginLocAttempted = false;
let activePreLoginLocPromise = null;

function clearDataSubscriptions() {
  dataUnsubs.forEach((u) => u?.());
  dataUnsubs = [];
  clearActiveRandomSessionListeners();
  stopMatchmaking();
  if (heartbeatTimer) clearInterval(heartbeatTimer);
  if (streakTimer) clearInterval(streakTimer);
  heartbeatTimer = null;
  streakTimer = null;
}

function clearActiveRandomSessionListeners() {
  activeRandomUnsubs.forEach((u) => u?.());
  activeRandomUnsubs = [];
}

function stopMatchmaking() {
  matchmakingCleanup?.();
  matchmakingCleanup = null;
}

function startUserSubscriptions(user) {
  clearDataSubscriptions();
  const uid = user.uid;

  dataUnsubs.push(
    api.observeMyProfile(uid, (profile) => {
      store.profileLoaded = true;
      store.myProfile = profile;
      if (profile) {
        if (profile.isPremium && store.filters.maxDistanceKm === MAX_FREE_DISTANCE_KM) {
          store.filters.maxDistanceKm = MAX_PREMIUM_DISTANCE_KM;
        }
        // Auto-expire finished premium plan
        if (profile.isPremium && profile.premiumExpiresAt > 0 && Date.now() >= profile.premiumExpiresAt) {
          api.downgradeToFreePlan().catch(() => {});
        }
        // Sync pre-login captured browser GPS once per login session
        if (
          preLoginLocationSyncedForUid !== uid &&
          store.preLoginLocation?.granted &&
          store.preLoginLocation.latitude != null &&
          store.preLoginLocation.longitude != null
        ) {
          preLoginLocationSyncedForUid = uid;
          const locLabel = store.preLoginLocation.locationName || profile.locationName || "Nearby";
          api
            .updateUserLocation(
              store.preLoginLocation.latitude,
              store.preLoginLocation.longitude,
              locLabel
            )
            .then(() => {
              store.locationSyncStatus = `Synced at ${new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
            })
            .catch(() => {});
        }
      }
      notify();
    })
  );

  dataUnsubs.push(
    api.observePublicProfiles((list) => {
      store.publicProfiles = list;
      notify();
    })
  );

  dataUnsubs.push(
    api.observeSentLikes(uid, (likes) => {
      store.sentLikes = likes;
      notify();
    })
  );

  dataUnsubs.push(
    api.observeReceivedLikes(uid, (likes) => {
      store.receivedLikes = likes;
      const nextIds = new Set(likes.map((l) => l.id));
      likes.forEach((like) => {
        const ms = timestampToMs(like.createdAt) || Date.now();
        const isLive =
          initialLikesLoaded &&
          !knownLikeIds.has(like.id) &&
          ms >= appBootTimeMs - 30_000;
        recordNotification({
          id: `like_${like.id}`,
          title: isLive ? "New Like Received! ❤️" : `Liked by ${like.fromUserName} ❤️`,
          body: `${like.fromUserName} liked your profile!`,
          timestampMs: ms,
          emitToast: isLive
        });
      });
      if (likes.length > 0) initialLikesLoaded = true;
      knownLikeIds = nextIds;
      notify();
    })
  );

  dataUnsubs.push(
    api.observeBlockedUsers(uid, (blocks) => {
      store.blockedUsers = blocks;
      notify();
    })
  );

  dataUnsubs.push(
    api.observeAllUserMessages(uid, store.isAdmin, (messages) => {
      store.messages = messages;
      const incoming = messages.filter(
        (m) => m.receiverId === uid || (store.isAdmin && m.receiverId === "milan_admin_support" && m.senderId !== uid)
      );
      const nextIds = new Set(incoming.map((m) => m.id));
      incoming.forEach((msg) => {
        const ms = timestampToMs(msg.createdAt) || appBootTimeMs;
        const ageMs = Math.max(0, Date.now() - ms);
        const isLive =
          initialMessagesLoaded &&
          !knownMsgIds.has(msg.id) &&
          ms >= appBootTimeMs - 30_000 &&
          ageMs <= 45_000;
        if (msg.messageType === "text" || msg.messageType === "photo") {
          recordNotification({
            id: `msg_${msg.id}`,
            title: `Message from ${msg.senderName} 💬`,
            body: msg.text,
            timestampMs: ms,
            emitToast: isLive
          });
        }
      });
      if (messages.length > 0) initialMessagesLoaded = true;
      knownMsgIds = nextIds;
      notify();
    })
  );

  dataUnsubs.push(
    api.observeMyActiveRandomSessions(uid, (sessions) => {
      store.myActiveRandomSessions = sessions;
      if (initialRandomSessionsLoaded) {
        sessions.forEach((sess) => {
          if (!knownRandomSessionIds.has(sess.id) && sess.id !== store.activeRandomSessionId) {
            const partnerGender = sess.userAId === uid ? sess.userBGender : sess.userAGender;
            recordNotification({
              id: `rnd_${sess.id}`,
              title: "New Random Chat Match 🎲",
              body: `Anonymous (${partnerGender}) started a Random Chat with you!`,
              timestampMs: timestampToMs(sess.createdAt) || Date.now(),
              emitToast: true
            });
          }
        });
      }
      initialRandomSessionsLoaded = true;
      knownRandomSessionIds = new Set(sessions.map((s) => s.id));
      notify();
    })
  );

  // Foreground Online Presence Heartbeat (every 30s)
  const syncPresence = (online) => {
    if (store.myProfile) {
      api.updateUserPresence(online).catch(() => {});
    }
  };
  syncPresence(true);
  heartbeatTimer = setInterval(() => {
    if (!document.hidden && store.myProfile) {
      api.updateUserPresence(true).catch(() => {});
    }
  }, 30_000);

  // 6-Day x 15-Minute Usage Streak & Expiry Tracker (every 5s)
  let unsyncedSeconds = 0;
  streakTimer = setInterval(async () => {
    const my = store.myProfile;
    if (!my || document.hidden) return;

    if (my.isPremium && my.premiumExpiresAt > 0 && Date.now() >= my.premiumExpiresAt) {
      try {
        await api.downgradeToFreePlan();
        pushToast("Premium Ended", "Your Premium period has ended. Switched back to Free Plan.");
      } catch {}
      return;
    }

    if (isPaidPremium(my)) {
      unsyncedSeconds = 0;
      return;
    }

    store.sessionUsageSeconds += 5;
    unsyncedSeconds += 5;
    const today = todayDateString();
    const projected = (my.lastUsageDate === today ? Number(my.todayUsageSeconds || 0) : 0) + unsyncedSeconds;
    const reachedGoal = projected >= DAILY_STREAK_TARGET_SECONDS && my.lastStreakCompletedDate !== today;

    if (reachedGoal || unsyncedSeconds >= 25) {
      const added = unsyncedSeconds;
      unsyncedSeconds = 0;
      const yesterday = yesterdayDateString();
      const baseSeconds = my.lastUsageDate === today ? Number(my.todayUsageSeconds || 0) : 0;
      const newTodaySeconds = Math.min(86400, baseSeconds + added);
      let currentStreak = [today, yesterday].includes(my.lastStreakCompletedDate)
        ? Number(my.streakDays || 0)
        : 0;
      let completedDate = my.lastStreakCompletedDate || "";
      let grantFreeDay = false;

      if (newTodaySeconds >= DAILY_STREAK_TARGET_SECONDS && completedDate !== today) {
        currentStreak += 1;
        completedDate = today;
        if (currentStreak >= STREAK_REQUIRED_DAYS) {
          grantFreeDay = true;
          currentStreak = 0;
        }
      }

      try {
        await api.updateUsageStreakProgress({
          currentProfile: my,
          newStreakDays: currentStreak,
          newTodaySeconds,
          todayDate: today,
          completedDate,
          grantFreeRewardDay: grantFreeDay
        });
        if (grantFreeDay) {
          recordNotification({
            id: `streak_${today}`,
            title: "🎁 24-Hour Free Premium Unlocked!",
            body: "6-day × 15-minute streak completed! 24-Hour Free Milan Gold is now active.",
            isReward: true,
            emitToast: true
          });
        }
      } catch {}
    }
  }, 5_000);
}

// ==================== ACTIONS ====================

const actions = {
  requestPreLoginLocation(options = {}) {
    if (activePreLoginLocPromise) return activePreLoginLocPromise;
    if (!navigator.geolocation) {
      const msg = "Your browser does not support GPS/location access.";
      setPreLoginLocation({ granted: false, status: "unsupported", error: msg });
      return Promise.resolve({ granted: false, error: msg });
    }

    setPreLoginLocation({ status: "checking", error: "" });

    activePreLoginLocPromise = new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        async (pos) => {
          const latitude = pos.coords.latitude;
          const longitude = pos.coords.longitude;
          let locationName = store.preLoginLocation?.locationName || "GPS Active";
          setPreLoginLocation({
            granted: true,
            status: "granted",
            latitude,
            longitude,
            locationName,
            error: ""
          });
          store.locationSyncStatus = `GPS Ready (${latitude.toFixed(2)}, ${longitude.toFixed(2)})`;

          try {
            const controller = new AbortController();
            const timer = setTimeout(() => controller.abort(), 3500);
            const resp = await fetch(
              `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${latitude}&lon=${longitude}`,
              { signal: controller.signal }
            );
            clearTimeout(timer);
            if (resp.ok) {
              const data = await resp.json();
              const city =
                data?.address?.city ||
                data?.address?.town ||
                data?.address?.village ||
                data?.address?.state_district ||
                data?.address?.state;
              const state = data?.address?.state;
              if (city) {
                locationName = state && city !== state ? `${city}, ${state}` : city;
                setPreLoginLocation({ locationName });
                store.locationSyncStatus = `GPS Ready • ${locationName}`;
              }
            }
          } catch {}

          activePreLoginLocPromise = null;
          resolve({ granted: true, latitude, longitude, locationName });
        },
        (err) => {
          let errorMsg = "Please turn on device Location / GPS and allow browser permission before logging in.";
          let status = "error";
          if (err?.code === 1) {
            status = "denied";
            errorMsg =
              "Location permission was denied. Please click the 🔒 icon in your browser address bar, allow Location, and tap 'Enable Location'.";
          } else if (err?.code === 2) {
            errorMsg = "Device Location / GPS appears to be turned off. Please turn on Location in device settings and retry.";
          } else if (err?.code === 3) {
            errorMsg = "Location lookup timed out. Make sure GPS is ON and tap 'Enable Location'.";
          }
          setPreLoginLocation({
            granted: false,
            status,
            error: options.autoOnLoad && err?.code === 1 ? errorMsg : errorMsg
          });
          activePreLoginLocPromise = null;
          resolve({ granted: false, error: errorMsg });
        },
        { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 }
      );
    });

    return activePreLoginLocPromise;
  },

  async signIn() {
    await api.signInWithGoogle();
  },

  async signOut() {
    if (store.myProfile) {
      await api.updateUserPresence(false).catch(() => {});
    }
    clearDataSubscriptions();
    await api.signOutGoogle();
  },

  async saveProfile(profile, isExisting) {
    if (isExisting) {
      await api.updateProfileDetails(profile);
      pushToast("Profile Updated", "Your profile changes are live across App & Web.");
    } else {
      await api.createProfile(profile);
      pushToast("Welcome to Milan!", "Your profile is live across App & Web.");
    }
  },

  refreshBrowserGps() {
    if (!navigator.geolocation) {
      pushToast("GPS Unavailable", "Browser geolocation is not supported.", "error");
      return;
    }
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const lat = pos.coords.latitude;
        const lon = pos.coords.longitude;
        let locName = store.myProfile?.locationName || "Nearby";
        try {
          const resp = await fetch(
            `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${lat}&lon=${lon}`
          );
          if (resp.ok) {
            const data = await resp.json();
            const city =
              data?.address?.city ||
              data?.address?.town ||
              data?.address?.village ||
              data?.address?.state_district ||
              data?.address?.state;
            const state = data?.address?.state;
            if (city) locName = state && city !== state ? `${city}, ${state}` : city;
          }
        } catch {}
        await api.updateUserLocation(lat, lon, locName);
        store.locationSyncStatus = `Synced at ${new Date().toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
        pushToast("Location Synced", `Updated to ${locName}`);
        notify();
      },
      (err) => {
        pushToast("GPS Permission", err.message || "Could not fetch browser location.", "error");
      }
    );
  },

  async toggleLike(targetProfile) {
    const my = store.myProfile;
    if (!my) return;
    const alreadyLiked = store.sentLikes.some((l) => l.toUserId === targetProfile.userId);
    if (!alreadyLiked && !my.isPremium && store.sentLikes.length >= FREE_MAX_LIKES_LIMIT) {
      pushToast(
        "Free Likes Limit Reached",
        `Free plan allows up to ${FREE_MAX_LIKES_LIMIT} likes. Upgrade to Gold for unlimited likes.`,
        "error"
      );
      return;
    }
    try {
      if (alreadyLiked) {
        await api.removeLike(targetProfile.userId);
        pushToast("Like Removed", `Removed like from ${targetProfile.firstName}.`);
      } else {
        await api.sendLike(fullNameOf(my), targetProfile.userId, fullNameOf(targetProfile));
        pushToast("Liked! ❤️", `You liked ${targetProfile.firstName}. You can now message each other.`);
      }
    } catch (err) {
      pushToast("Error", err.message, "error");
    }
  },

  async removeLike(toUserId) {
    try {
      await api.removeLike(toUserId);
      pushToast("Removed", "Like removed.");
    } catch (err) {
      pushToast("Error", err.message, "error");
    }
  },

  initiateChat(targetProfile) {
    const my = store.myProfile;
    if (!my) return;
    const hasLiked =
      store.sentLikes.some((l) => l.toUserId === targetProfile.userId) ||
      store.receivedLikes.some((l) => l.fromUserId === targetProfile.userId);

    if (my.isPremium || hasLiked || isAdminSupportPartner(targetProfile) || store.isAdmin) {
      actions.openDirectChat(targetProfile);
    } else {
      showLikeGateModal(targetProfile, actions);
    }
  },

  async likeAndOpenChat(targetProfile) {
    const my = store.myProfile;
    if (!my) return;
    try {
      await api.sendLike(fullNameOf(my), targetProfile.userId, fullNameOf(targetProfile));
      actions.openDirectChat(targetProfile);
    } catch (err) {
      pushToast("Error", err.message, "error");
    }
  },

  openDirectChat(targetProfile) {
    store.activeChatPartnerId = targetProfile.userId;
    store.activeChatPartnerSnapshot = targetProfile;
    store.activeTab = "chats";
    notify();
  },

  async sendDirectMessage(receiverProfile, text) {
    const my = store.myProfile;
    if (!my) return;
    const isSupport = isAdminSupportPartner(receiverProfile) || store.isAdmin;
    if (!isSupport && !my.isPremium && Number(my.dailyMessageCount || 0) >= FREE_DAILY_MESSAGE_LIMIT) {
      pushToast(
        "Daily Message Limit Reached",
        `Free users can send ${FREE_DAILY_MESSAGE_LIMIT} messages/day. Upgrade to Milan Gold for unlimited messaging.`,
        "error"
      );
      return;
    }

    const hasLiked =
      store.sentLikes.some((l) => l.toUserId === receiverProfile.userId) ||
      store.receivedLikes.some((l) => l.fromUserId === receiverProfile.userId);

    try {
      if (!isSupport && !my.isPremium && !hasLiked) {
        await api.sendLike(fullNameOf(my), receiverProfile.userId, fullNameOf(receiverProfile));
      }
      await api.sendMessage({
        senderName: fullNameOf(my),
        receiverId: receiverProfile.userId,
        receiverName: fullNameOf(receiverProfile),
        text,
        currentDailyCount: Number(my.dailyMessageCount || 0),
        messageType: "text",
        skipDailyIncrement: isSupport
      });
    } catch (err) {
      pushToast("Send Failed", err.message, "error");
    }
  },

  async sendPhotoMessage(receiverProfile, photoBase64, caption = "") {
    const my = store.myProfile;
    if (!my) return;
    const isSupport = isAdminSupportPartner(receiverProfile) || store.isAdmin;
    if (!isSupport && !my.isPremium && Number(my.dailyMessageCount || 0) >= FREE_DAILY_MESSAGE_LIMIT) {
      pushToast("Daily Limit Reached", "Upgrade to Milan Gold for unlimited photo messaging.", "error");
      return;
    }
    try {
      await api.sendMessage({
        senderName: fullNameOf(my),
        receiverId: receiverProfile.userId,
        receiverName: fullNameOf(receiverProfile),
        text: caption || "📷 Shared a photo",
        currentDailyCount: Number(my.dailyMessageCount || 0),
        photoBase64,
        messageType: "photo",
        skipDailyIncrement: isSupport
      });
    } catch (err) {
      pushToast("Photo Failed", err.message, "error");
    }
  },

  async deleteChatThread(otherUserId) {
    if (store.activeChatPartnerId === otherUserId) {
      store.activeChatPartnerId = null;
      store.activeChatPartnerSnapshot = null;
    }
    try {
      await api.deleteNormalChatThread(otherUserId);
      pushToast("Chat Deleted", "Conversation history removed.");
      notify();
    } catch (err) {
      pushToast("Error", err.message, "error");
    }
  },

  async blockUser(targetUid, targetName) {
    try {
      await api.blockUser(targetUid, targetName);
      if (store.activeChatPartnerId === targetUid) {
        store.activeChatPartnerId = null;
        store.activeChatPartnerSnapshot = null;
      }
      pushToast("User Blocked", `${targetName} has been blocked.`);
      notify();
    } catch (err) {
      pushToast("Error", err.message, "error");
    }
  },

  async unblockUser(blockedUid, blockedName) {
    try {
      await api.unblockUser(blockedUid);
      pushToast("Unblocked", `${blockedName} has been unblocked.`);
    } catch (err) {
      pushToast("Error", err.message, "error");
    }
  },

  async updateGallery(galleryPhotos) {
    try {
      await api.updateGalleryPhotos(galleryPhotos);
      pushToast("Gallery Updated", `Saved ${galleryPhotos.length}/6 photos.`);
    } catch (err) {
      pushToast("Gallery Error", err.message, "error");
    }
  },

  async submitUpiUtr(planName, amountInr, utr12Digits) {
    const my = store.myProfile;
    if (!my) return;
    try {
      await api.submitUpiPaymentForVerification(planName, amountInr, utr12Digits);
      const adminProfile = buildAdminSupportProfile();
      if (adminProfile.userId !== store.user?.uid) {
        await api.sendMessage({
          senderName: fullNameOf(my),
          receiverId: adminProfile.userId,
          receiverName: fullNameOf(adminProfile),
          text: `💳 UPI Payment Verification Request • Plan: ${planName} (₹${amountInr}) • 12-Digit UTR: ${utr12Digits} • Waiting for Admin verification.`,
          currentDailyCount: Number(my.dailyMessageCount || 0),
          messageType: "text",
          skipDailyIncrement: true
        });
      }
      pushToast("UTR Submitted ⏳", `Admin will verify UTR ${utr12Digits} and activate ${planName} Gold.`, "reward");
    } catch (err) {
      pushToast("Submission Failed", err.message, "error");
    }
  },

  async adminVerifyUpi(targetUser, approve) {
    const my = store.myProfile;
    const planToGrant = ["Monthly", "Yearly"].includes(targetUser.pendingPlan)
      ? targetUser.pendingPlan
      : "Monthly";
    const now = Date.now();
    try {
      if (approve) {
        const expiresAt =
          planToGrant === "Yearly" ? now + 365 * 86400_000 : now + 30 * 86400_000;
        await api.adminUpdateUserPremium(targetUser.userId, planToGrant, expiresAt, "Verified", "");
        if (my && targetUser.userId !== store.user?.uid) {
          await api.sendMessage({
            senderName: "Admin Support",
            receiverId: targetUser.userId,
            receiverName: fullNameOf(targetUser),
            text: `✅ Payment Verified! Your UPI UTR (${targetUser.lastUpiTxnId}) for ₹${targetUser.lastPaymentAmountInr} has been verified by Admin. Your Milan ${planToGrant} Gold Premium is now active!`,
            currentDailyCount: Number(my.dailyMessageCount || 0),
            skipDailyIncrement: true
          });
        }
        pushToast("Admin: Approved", `Activated ${planToGrant} Gold for ${fullNameOf(targetUser)}.`);
      } else {
        await api.adminUpdateUserPremium(targetUser.userId, "Free", 0, "Rejected", "");
        if (my && targetUser.userId !== store.user?.uid) {
          await api.sendMessage({
            senderName: "Admin Support",
            receiverId: targetUser.userId,
            receiverName: fullNameOf(targetUser),
            text: `❌ Payment Verification Failed: Your submitted UPI UTR (${targetUser.lastUpiTxnId}) was not found in bank records.`,
            currentDailyCount: Number(my.dailyMessageCount || 0),
            skipDailyIncrement: true
          });
        }
        pushToast("Admin: Rejected", `Rejected UTR for ${fullNameOf(targetUser)}.`);
      }
    } catch (err) {
      pushToast("Admin Error", err.message, "error");
    }
  },

  async adminVerifyFace(targetUser, approve) {
    const my = store.myProfile;
    try {
      await api.adminVerifyUserProfile(targetUser.userId, approve);
      if (my && targetUser.userId !== store.user?.uid) {
        await api.sendMessage({
          senderName: "Admin Support",
          receiverId: targetUser.userId,
          receiverName: fullNameOf(targetUser),
          text: approve
            ? `☑️ Congratulations ${targetUser.firstName}! Admin verified your live face video and photos. Your profile now has the Verified User Blue Tick!`
            : `⚠️ Profile Unverified: Admin reviewed your profile and marked it Unverified. Please record a new Live Face Verification Video in the app.`,
          currentDailyCount: Number(my.dailyMessageCount || 0),
          skipDailyIncrement: true
        });
      }
      pushToast("Admin", approve ? `Verified ${fullNameOf(targetUser)}` : `Marked ${fullNameOf(targetUser)} Unverified`);
    } catch (err) {
      pushToast("Admin Error", err.message, "error");
    }
  },

  // ==================== RANDOM CHAT ACTIONS ====================

  async toggleRandomMeet(enabled) {
    try {
      await api.setRandomMeetEnabled(enabled);
      pushToast(
        enabled ? "Random Meet ON" : "Random Meet OFF",
        enabled ? "Matching users can now connect with you in Random Chat." : "Hidden from new Random Chat matches."
      );
    } catch (err) {
      pushToast("Error", err.message, "error");
    }
  },

  connectToRandomSession(session) {
    stopMatchmaking();
    clearActiveRandomSessionListeners();
    const uid = store.user?.uid;
    store.activeRandomSessionId = session.id;
    store.activeRandomSession = session;
    store.randomStatus = session.status === "ended" ? "PARTNER_LEFT" : "CONNECTED";
    store.fetchedRandomPartner = null;

    const partnerId = session.userAId === uid ? session.userBId : session.userAId;
    api.getProfileById(partnerId).then((p) => {
      if (p) {
        store.fetchedRandomPartner = p;
        notify();
      }
    }).catch(() => {});

    activeRandomUnsubs.push(
      api.observeRandomSessionById(session.id, (updated) => {
        if (!updated) return;
        store.activeRandomSession = updated;
        if (updated.status === "ended" && updated.endedByUserId !== uid) {
          store.randomStatus = "PARTNER_LEFT";
        } else if (updated.status === "active") {
          store.randomStatus = "CONNECTED";
        }
        notify();
      })
    );

    activeRandomUnsubs.push(
      api.observeRandomChatMessages(session.id, uid, (msgs) => {
        store.randomMessages = msgs;
        notify();
      })
    );

    notify();
  },

  openExistingRandomSession(sessionId) {
    const existing = store.myActiveRandomSessions.find((s) => s.id === sessionId);
    if (existing) actions.connectToRandomSession(existing);
  },

  async startRandomMatchmaking(excludePartnerId = null) {
    const my = store.myProfile;
    const uid = store.user?.uid;
    if (!my || !uid) return;

    const isUnlimited = Boolean(my.isPremium || store.isAdmin);
    const today = todayDateString();
    const usedToday = my.lastRandomFindDate === today ? Number(my.dailyRandomFindCount || 0) : 0;
    if (!isUnlimited && usedToday >= FREE_DAILY_RANDOM_FIND_LIMIT) {
      pushToast(
        "Free Random Limit Reached",
        `Free users get ${FREE_DAILY_RANDOM_FIND_LIMIT} new Random finds/day. Upgrade to Gold for unlimited finds.`,
        "error"
      );
      return;
    }

    stopMatchmaking();
    clearActiveRandomSessionListeners();
    const prevSid = store.activeRandomSessionId;
    store.activeRandomSessionId = null;
    store.activeRandomSession = null;
    store.randomMessages = [];
    store.randomStatus = "SEARCHING";
    notify();

    let consumedFind = false;
    const consumeOneFind = () => {
      if (!isUnlimited && !consumedFind) {
        consumedFind = true;
        api.recordDailyRandomFind(today, usedToday + 1).catch(() => {});
      }
    };

    const prefs = store.randomPrefs;
    const iUseUnlimitedDist = isUnlimited && prefs.maxDistanceKm >= MAX_PREMIUM_DISTANCE_KM;
    const effectivePrefs = {
      ...prefs,
      maxDistanceKm: iUseUnlimitedDist
        ? UNLIMITED_DISTANCE_KM
        : isUnlimited
          ? prefs.maxDistanceKm
          : Math.min(500, prefs.maxDistanceKm)
    };

    try {
      await api.enqueueForRandomChat(my, effectivePrefs);
    } catch (err) {
      store.randomStatus = "IDLE";
      pushToast("Random Chat Error", err.message, "error");
      notify();
      return;
    }

    let cancelled = false;
    const unsubList = [];
    let fallbackTimer = null;

    matchmakingCleanup = () => {
      cancelled = true;
      unsubList.forEach((u) => u?.());
      if (fallbackTimer) clearTimeout(fallbackTimer);
      api.leaveRandomChatQueue().catch(() => {});
    };

    // Watcher A: Incoming session created by another peer
    unsubList.push(
      api.observeMyActiveRandomSessions(uid, (sessions) => {
        if (cancelled || store.randomStatus !== "SEARCHING") return;
        const latest = sessions.find(
          (s) => s.status === "active" && s.id !== prevSid && s.id !== lastEndedRandomSessionId
        );
        if (latest) {
          consumeOneFind();
          actions.connectToRandomSession(latest);
        }
      })
    );

    // Watcher B: Waiting queue scan
    unsubList.push(
      api.observeWaitingRandomQueueEntries(async (waiting) => {
        if (cancelled || store.randomStatus !== "SEARCHING") return;
        const blockedIds = new Set(store.blockedUsers.map((b) => b.blockedUserId));
        const candidates = waiting.filter((e) => {
          if (e.userId === uid || e.status !== "waiting" || blockedIds.has(e.userId) || e.userId === excludePartnerId) {
            return false;
          }
          const iWantGender =
            effectivePrefs.preferredGender === "All" ||
            String(e.gender).toLowerCase() === effectivePrefs.preferredGender.toLowerCase();
          const partnerWantsGender =
            e.preferredGender === "All" ||
            String(my.gender).toLowerCase() === String(e.preferredGender).toLowerCase();
          const partnerInMyAge = e.age >= effectivePrefs.minAge && e.age <= effectivePrefs.maxAge;
          const iInPartnerAge = my.age >= e.preferredMinAge && my.age <= e.preferredMaxAge;
          const dist = calculateDistanceKm(my.latitude ?? 23.0225, my.longitude ?? 72.5714, e.latitude, e.longitude);
          const myDistOk = iUseUnlimitedDist || dist <= effectivePrefs.maxDistanceKm;
          const partnerDistOk = e.preferredMaxDistanceKm >= MAX_PREMIUM_DISTANCE_KM || dist <= e.preferredMaxDistanceKm;
          return iWantGender && partnerWantsGender && partnerInMyAge && iInPartnerAge && myDistOk && partnerDistOk;
        });

        for (const candidate of candidates) {
          if (cancelled || store.randomStatus !== "SEARCHING") break;
          try {
            const paired = await api.tryPairWithWaitingUser(my, candidate);
            if (!cancelled && paired) {
              consumeOneFind();
              actions.connectToRandomSession(paired);
              await api.sendRandomChatMessage(
                paired,
                "🎲 Random Chat Connected. Only Gender is visible until both agree.",
                "system"
              );
              break;
            }
          } catch {
            // Try next candidate
          }
        }
      })
    );

    // Watcher C: Instant Matchmaker against allowRandomMeet public profiles (online first)
    fallbackTimer = setTimeout(async () => {
      if (cancelled || store.randomStatus !== "SEARCHING") return;
      const blockedIds = new Set(store.blockedUsers.map((b) => b.blockedUserId));
      const activePartnerIds = new Set(
        store.myActiveRandomSessions.map((s) => (s.userAId === uid ? s.userBId : s.userAId))
      );

      const allMatches = store.publicProfiles.filter((p) => {
        if (p.userId === uid || p.allowRandomMeet === false || blockedIds.has(p.userId)) return false;
        const genderOk =
          effectivePrefs.preferredGender === "All" ||
          String(p.gender).toLowerCase() === effectivePrefs.preferredGender.toLowerCase();
        const ageOk = Number(p.age) >= effectivePrefs.minAge && Number(p.age) <= effectivePrefs.maxAge;
        const dist = calculateDistanceKm(
          my.latitude ?? 23.0225,
          my.longitude ?? 72.5714,
          p.latitude ?? 23.0225,
          p.longitude ?? 72.5714
        );
        const distOk = iUseUnlimitedDist || dist <= effectivePrefs.maxDistanceKm;
        return genderOk && ageOk && distOk;
      });

      const fresh = allMatches.filter((p) => !activePartnerIds.has(p.userId));
      const pool = fresh.filter((p) => p.userId !== excludePartnerId);
      const effectivePool = pool.length ? pool : fresh;
      const onlineFirst = [
        ...effectivePool.filter((p) => isCurrentlyOnline(p)),
        ...effectivePool.filter((p) => !isCurrentlyOnline(p))
      ];

      const chosen = onlineFirst[0];
      if (chosen && !cancelled && store.randomStatus === "SEARCHING") {
        try {
          const created = await api.createDirectRandomSession(my, chosen);
          consumeOneFind();
          actions.connectToRandomSession(created);
          const statusLabel = isCurrentlyOnline(chosen) ? "Online" : "Offline";
          await api.sendRandomChatMessage(
            created,
            `🎲 Connected (${statusLabel}). Only Gender is visible until both say Yes.`,
            "system"
          );
        } catch (err) {
          pushToast("Matchmaking", err.message, "error");
        }
      } else if (!cancelled && store.randomStatus === "SEARCHING") {
        const reusable = store.myActiveRandomSessions.find((s) => {
          const pid = s.userAId === uid ? s.userBId : s.userAId;
          return allMatches.some((m) => m.userId === pid && pid !== excludePartnerId);
        });
        if (reusable) {
          actions.connectToRandomSession(reusable);
        } else {
          stopMatchmaking();
          store.randomStatus = "IDLE";
          pushToast("No Match Found", "No matching Random Meet users found right now.");
          notify();
        }
      }
    }, 600);
  },

  cancelRandomMatchmaking() {
    stopMatchmaking();
    store.randomStatus = "IDLE";
    notify();
  },

  async consentRandomRevealStep(step) {
    const session = store.activeRandomSession;
    const uid = store.user?.uid;
    if (!session || !uid || session.status !== "active") return;
    const amA = session.userAId === uid;
    const partnerConsented =
      step === "NAME"
        ? amA ? session.userBConsentName : session.userAConsentName
        : step === "AGE"
          ? amA ? session.userBConsentAge : session.userAConsentAge
          : step === "PHOTO"
            ? amA ? session.userBConsentPhoto : session.userAConsentPhoto
            : amA ? session.userBConsentProfile : session.userAConsentProfile;

    try {
      await api.submitRandomRevealConsent(session, step);
      const labelMap = { NAME: "Show Name", AGE: "Show Age", PHOTO: "Face Reveal", PROFILE: "Full Profile" };
      const msg = partnerConsented
        ? `🎉 Partner also said Yes! ${labelMap[step]} unlocked.`
        : `✨ Requested '${labelMap[step]}'. Waiting for partner.`;
      await api.sendRandomChatMessage(session, msg, "system");
    } catch (err) {
      pushToast("Reveal Error", err.message, "error");
    }
  },

  async sendRandomMessage(text) {
    const session = store.activeRandomSession;
    if (!session || session.status !== "active") return;
    try {
      await api.sendRandomChatMessage(session, text, "text");
    } catch (err) {
      pushToast("Send Error", err.message, "error");
    }
  },

  minimizeRandomChat() {
    stopMatchmaking();
    clearActiveRandomSessionListeners();
    store.activeRandomSessionId = null;
    store.activeRandomSession = null;
    store.randomMessages = [];
    store.randomStatus = "IDLE";
    notify();
  },

  async endRandomChat() {
    const sid = store.activeRandomSessionId;
    if (sid) lastEndedRandomSessionId = sid;
    stopMatchmaking();
    clearActiveRandomSessionListeners();
    store.activeRandomSessionId = null;
    store.activeRandomSession = null;
    store.randomMessages = [];
    store.randomStatus = "IDLE";
    notify();
    try {
      await api.endRandomChatSession(sid);
    } catch {}
  },

  async deleteRandomSession(sessionId) {
    if (store.activeRandomSessionId === sessionId) {
      clearActiveRandomSessionListeners();
      store.activeRandomSessionId = null;
      store.activeRandomSession = null;
      store.randomMessages = [];
      store.randomStatus = "IDLE";
    }
    try {
      await api.deleteRandomChatSession(sessionId);
      pushToast("Deleted", "Random chat session deleted.");
      notify();
    } catch (err) {
      pushToast("Error", err.message, "error");
    }
  },

  findNextRandomPartner() {
    const uid = store.user?.uid;
    const sess = store.activeRandomSession;
    const prevPartnerId = sess ? (sess.userAId === uid ? sess.userBId : sess.userAId) : null;
    actions.startRandomMatchmaking(prevPartnerId);
  },

  async blockRandomPartner(partnerId, revealedName) {
    await actions.endRandomChat();
    await actions.blockUser(partnerId, revealedName || "Random Chat User");
  }
};

// ==================== RENDER ENGINE ====================

function renderToasts() {
  const box = document.getElementById("toast-container");
  if (!box) return;
  box.innerHTML = store.toasts
    .map(
      (t) => `
    <div class="toast ${t.variant === "reward" ? "reward" : t.variant === "error" ? "error" : ""}">
      <strong>${escapeHtml(t.title)}</strong>
      ${t.body ? `<span class="muted">${escapeHtml(t.body)}</span>` : ""}
    </div>
  `
    )
    .join("");
}

function renderApp() {
  renderToasts();
  const root = document.getElementById("app-root");
  if (!root) return;

  // Preserve active input focus & text across real-time Firestore re-renders
  const activeEl = document.activeElement;
  const activeId = activeEl?.id || null;
  const activeVal = activeEl && ("value" in activeEl) ? activeEl.value : null;
  const selStart = activeEl?.selectionStart ?? null;
  const selEnd = activeEl?.selectionEnd ?? null;

  if (!store.authReady) return;

  if (!store.user) {
    renderAuthLanding(root, actions);
    return;
  }

  if (!store.profileLoaded) {
    root.innerHTML = `
      <div class="boot-screen">
        <div class="boot-logo">❤</div>
        <h2>Loading your Milan profile...</h2>
      </div>
    `;
    return;
  }

  if (!store.myProfile || store.editProfileOpen) {
    renderOnboardingOrEdit(root, actions, store.myProfile);
    return;
  }

  const my = store.myProfile;
  const unreadNotifs = store.notifications.filter((n) => !n.isRead).length;
  const convosCount = getConversations().length;
  const likesCount = store.receivedLikes.length;

  const navItems = [
    { id: "discover", icon: "✨", label: "Discover", count: 0 },
    { id: "likes", icon: "❤️", label: "Likes", count: likesCount },
    { id: "random", icon: "🎲", label: "Random", count: store.myActiveRandomSessions.length },
    { id: "chats", icon: "💬", label: "Chats", count: convosCount },
    { id: "profile", icon: "👤", label: "Profile", count: 0 }
  ];

  const tabTitles = {
    discover: ["Discover Nearby", store.locationSyncStatus],
    likes: ["Likes & Matches", "People who liked you & profiles you liked"],
    random: ["Random Chat", "Anonymous matchmaking with 4-step mutual reveal"],
    chats: ["Direct Messages", "Real-time chats synced across Milan App & Website"],
    profile: ["Profile & Milan Gold", "Manage your gallery, streak, theme & subscription"]
  };
  const [pageTitle, pageSub] = tabTitles[store.activeTab] || ["Milan", ""];

  root.innerHTML = `
    <div class="shell">
      <aside class="sidebar">
        <div class="brand-header">
          <div class="brand-left">
            <div class="brand-badge">❤</div>
            <div>
              <div class="brand-title">Milan</div>
              <div class="brand-sub">App &amp; Web Synced</div>
            </div>
          </div>
          <button id="sidebar-theme-btn" class="icon-btn" title="Theme & Dark Mode" aria-label="Theme">🎨</button>
        </div>

        <nav class="nav-list" aria-label="Main Navigation">
          ${navItems
            .map(
              (item) => `
            <button class="nav-item ${store.activeTab === item.id ? "active" : ""}" data-nav-tab="${item.id}">
              <span class="nav-item-left">
                <span>${item.icon}</span>
                <span>${escapeHtml(item.label)}</span>
              </span>
              ${item.count > 0 ? `<span class="nav-pill-count">${item.count}</span>` : ""}
            </button>
          `
            )
            .join("")}
        </nav>

        <div class="sidebar-profile-card">
          ${renderAvatarHtml(my, fullNameOf(my), 44, { circle: true })}
          <div style="min-width:0;flex:1;">
            <strong style="display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(fullNameOf(my))}</strong>
            <span class="muted">${my.isPremium ? `👑 ${escapeHtml(my.premiumPlan)}` : "Free Plan"} • 🟢 Online</span>
          </div>
        </div>
      </aside>

      <div class="main-area">
        <header class="top-bar">
          <div class="top-bar-title">
            <h2>${escapeHtml(pageTitle)}</h2>
            <span class="top-bar-subtitle">${escapeHtml(pageSub)}</span>
          </div>
          <div class="top-bar-actions">
            <button id="topbar-theme-btn" class="icon-btn" title="Appearance & Theme" aria-label="Theme">🎨</button>
            <button id="topbar-notif-btn" class="icon-btn" title="Notifications" aria-label="Notifications">
              🔔
              ${
                unreadNotifs > 0
                  ? `<span class="nav-pill-count" style="position:absolute;top:-5px;right:-5px;min-width:18px;height:18px;padding:0 4px;font-size:10px;">${unreadNotifs}</span>`
                  : ""
              }
            </button>
          </div>
        </header>

        <main id="page-content" class="page-content"></main>
      </div>

      <nav class="mobile-bottom-nav" aria-label="Mobile Navigation">
        ${navItems
          .map(
            (item) => `
          <button class="mobile-nav-btn ${store.activeTab === item.id ? "active" : ""}" data-nav-tab="${item.id}">
            <span class="mobile-nav-pill">${item.icon}</span>
            <span>${escapeHtml(item.label)}</span>
          </button>
        `
          )
          .join("")}
      </nav>
    </div>
  `;

  root.querySelectorAll("[data-nav-tab]").forEach((btn) => {
    btn.addEventListener("click", () => {
      store.activeTab = btn.getAttribute("data-nav-tab");
      notify();
    });
  });

  root.querySelector("#sidebar-theme-btn")?.addEventListener("click", showThemeModal);
  root.querySelector("#topbar-theme-btn")?.addEventListener("click", showThemeModal);
  root.querySelector("#topbar-notif-btn")?.addEventListener("click", () => {
    markAllNotificationsRead();
    showNotificationsModal();
  });

  const pageContainer = root.querySelector("#page-content");
  if (store.activeTab === "discover") {
    renderDiscoverView(pageContainer, actions);
  } else if (store.activeTab === "likes") {
    renderLikesView(pageContainer, actions);
  } else if (store.activeTab === "random") {
    renderRandomChatView(pageContainer, actions);
  } else if (store.activeTab === "chats") {
    renderChatsView(pageContainer, actions);
  } else if (store.activeTab === "profile") {
    renderProfileAndPremiumView(pageContainer, actions);
  }

  // Restore focus & draft value if user was typing when an async snapshot fired
  if (activeId && ["chat-text-input", "rnd-text-input", "discover-search"].includes(activeId)) {
    const restored = document.getElementById(activeId);
    if (restored) {
      if (activeVal !== null && restored.value !== activeVal) {
        restored.value = activeVal;
      }
      restored.focus({ preventScroll: true });
      if (selStart !== null && selEnd !== null && typeof restored.setSelectionRange === "function") {
        try {
          restored.setSelectionRange(selStart, selEnd);
        } catch {}
      }
    }
  }
}

// ==================== BOOTSTRAP ====================

subscribe(renderApp);

document.addEventListener("visibilitychange", () => {
  if (store.myProfile) {
    api.updateUserPresence(!document.hidden).catch(() => {});
  }
});

window.addEventListener("beforeunload", () => {
  if (store.myProfile) {
    api.updateUserPresence(false).catch(() => {});
  }
});

(async () => {
  await api.resolveRedirectSignIn();
  api.listenAuthChanges((user) => {
    resetStoreForAuth(user);
    if (user) {
      startUserSubscriptions(user);
    } else {
      clearDataSubscriptions();
      preLoginLocationSyncedForUid = null;
      if (!autoPreLoginLocAttempted) {
        autoPreLoginLocAttempted = true;
        actions.requestPreLoginLocation({ autoOnLoad: true });
      }
    }
  });
})();
