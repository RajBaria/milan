import {
  FREE_DAILY_RANDOM_FIND_LIMIT,
  MAX_PREMIUM_DISTANCE_KM,
  UNLIMITED_DISTANCE_KM
} from "../firebase-config.js";
import {
  store,
  notify,
  getMaskedRandomPartner,
  getRandomSessionReveals
} from "../state.js";
import {
  escapeHtml,
  formatMessageDateTime,
  fullNameOf,
  isCurrentlyOnline,
  renderAvatarHtml,
  todayDateString
} from "../utils.js";
import { showProfileDetailModal } from "./modals.js";

const REVEAL_STEPS = [
  { key: "NAME", title: "1. Show Name" },
  { key: "AGE", title: "2. Show Age" },
  { key: "PHOTO", title: "3. Face Reveal" },
  { key: "PROFILE", title: "4. Full Profile" }
];

export function renderRandomChatView(container, actions) {
  if (store.randomStatus === "IDLE") {
    renderLobby(container, actions);
  } else if (store.randomStatus === "SEARCHING") {
    renderSearching(container, actions);
  } else {
    renderConnectedSession(container, actions);
  }
}

function renderLobby(container, actions) {
  const my = store.myProfile;
  const uid = store.user?.uid;
  const isUnlimited = Boolean(my?.isPremium || store.isAdmin);
  const today = todayDateString();
  const usedToday = my?.lastRandomFindDate === today ? Number(my?.dailyRandomFindCount || 0) : 0;
  const remainingFinds = isUnlimited ? Infinity : Math.max(0, FREE_DAILY_RANDOM_FIND_LIMIT - usedToday);

  const prefs = store.randomPrefs;
  const maxDistCap = isUnlimited ? MAX_PREMIUM_DISTANCE_KM : 500;
  const profileMap = new Map(store.publicProfiles.map((p) => [p.userId, p]));
  const blockedIds = new Set(store.blockedUsers.map((b) => b.blockedUserId));
  const activeSessions = store.myActiveRandomSessions.filter((s) => {
    const partnerId = s.userAId === uid ? s.userBId : s.userAId;
    return s.status === "active" && !blockedIds.has(partnerId);
  });

  container.innerHTML = `
    <div class="stack" style="max-width:820px;margin:0 auto;">
      <div class="card" style="background:linear-gradient(135deg, var(--primary-container), var(--surface));">
        <div class="row-between">
          <div>
            <span class="badge badge-gold">🎲 Anonymous &amp; Unlimited Chat</span>
            <h2 style="margin-top:8px;">Random Chat • Step-by-Step Reveal</h2>
            <p class="muted" style="margin-top:4px;">
              Only Gender is shown when matched. Unlock Name, Age, Face Photo, and Full Profile only when BOTH say Yes.
            </p>
          </div>
          <div class="chip chip-static">
            ${isUnlimited ? "👑 Unlimited Finds" : `Free Finds Today: ${remainingFinds}/${FREE_DAILY_RANDOM_FIND_LIMIT}`}
          </div>
        </div>
      </div>

      <div class="card row-between">
        <div>
          <strong>Allow Random Meet</strong>
          <p class="muted">Let matching Android &amp; Web users connect with you in Random Chat anytime.</p>
        </div>
        <input id="rnd-allow-meet" type="checkbox" style="width:24px;height:24px;accent-color:var(--primary);" ${my?.allowRandomMeet !== false ? "checked" : ""} />
      </div>

      <div class="card stack">
        <h3>Match Preferences</h3>
        <div class="stack-sm">
          <span class="field-label">Preferred Gender</span>
          <div class="chips-row">
            ${["All", "Female", "Male"]
              .map(
                (g) => `
              <button type="button" class="chip ${prefs.preferredGender === g ? "selected" : ""}" data-rnd-gender="${g}">
                ${g === "All" ? "Any Gender" : g}
              </button>
            `
              )
              .join("")}
          </div>
        </div>

        <div class="row-gap">
          <div class="field-group" style="flex:1;">
            <label class="field-label" for="rnd-min-age">Min Age (18-65)</label>
            <input id="rnd-min-age" class="input" type="number" min="18" max="65" value="${prefs.minAge}" />
          </div>
          <div class="field-group" style="flex:1;">
            <label class="field-label" for="rnd-max-age">Max Age (18-65)</label>
            <input id="rnd-max-age" class="input" type="number" min="18" max="65" value="${prefs.maxAge}" />
          </div>
        </div>

        <div class="field-group">
          <div class="row-between">
            <label class="field-label" for="rnd-dist">Max Distance</label>
            <strong id="rnd-dist-label">${
              isUnlimited && prefs.maxDistanceKm >= MAX_PREMIUM_DISTANCE_KM
                ? "Unlimited Global 🌍"
                : `${Math.min(maxDistCap, prefs.maxDistanceKm)} km`
            }</strong>
          </div>
          <input id="rnd-dist" type="range" min="10" max="${maxDistCap}" step="10" value="${Math.min(maxDistCap, prefs.maxDistanceKm)}" />
        </div>

        <button id="rnd-start-btn" class="btn btn-primary" ${!isUnlimited && remainingFinds <= 0 ? "disabled" : ""}>
          🎲 Find Random Person
        </button>
        ${
          !isUnlimited && remainingFinds <= 0
            ? `<button id="rnd-upgrade-btn" class="btn btn-gold">👑 Upgrade to Gold for Unlimited Finds</button>`
            : ""
        }
      </div>

      ${
        activeSessions.length
          ? `<div class="card stack">
              <h3>Active Random Chats (${activeSessions.length})</h3>
              ${activeSessions
                .map((s) => {
                  const partnerId = s.userAId === uid ? s.userBId : s.userAId;
                  const partnerGender = s.userAId === uid ? s.userBGender : s.userAGender;
                  const partnerProfile = profileMap.get(partnerId);
                  const { isName } = getRandomSessionReveals(s);
                  const online = isCurrentlyOnline(partnerProfile);
                  const displayName = isName ? fullNameOf(partnerProfile) : `Anonymous (${partnerGender})`;
                  return `
                    <div class="card-soft row-between">
                      <div class="row-gap">
                        <div class="avatar-wrap" style="width:46px;height:46px;">
                          <div class="avatar-box circle" style="background:linear-gradient(135deg,#8E7AB5,#E86F88);font-size:16px;">
                            ${isName ? escapeHtml(displayName.charAt(0)) : "?"}
                          </div>
                        </div>
                        <div>
                          <strong>${escapeHtml(displayName)}</strong>
                          <div class="row-gap" style="margin-top:2px;">
                            <span class="badge ${online ? "badge-online" : "badge-offline"}">
                              <span class="dot ${online ? "dot-online" : "dot-offline"}"></span>
                              ${online ? "Online" : "Offline"}
                            </span>
                            <span class="muted">${isName ? "Name Revealed" : "Anonymous"}</span>
                          </div>
                        </div>
                      </div>
                      <div class="row-gap">
                        <button class="btn btn-sm btn-outline" data-rnd-delete="${escapeHtml(s.id)}">🗑️</button>
                        <button class="btn btn-sm btn-primary" data-rnd-open="${escapeHtml(s.id)}">Open Chat</button>
                      </div>
                    </div>
                  `;
                })
                .join("")}
            </div>`
          : ""
      }
    </div>
  `;

  container.querySelector("#rnd-allow-meet")?.addEventListener("change", (e) => {
    actions.toggleRandomMeet(e.target.checked);
  });

  container.querySelectorAll("[data-rnd-gender]").forEach((btn) => {
    btn.addEventListener("click", () => {
      store.randomPrefs.preferredGender = btn.getAttribute("data-rnd-gender");
      notify();
    });
  });

  const minAgeEl = container.querySelector("#rnd-min-age");
  const maxAgeEl = container.querySelector("#rnd-max-age");
  const distEl = container.querySelector("#rnd-dist");
  const distLabel = container.querySelector("#rnd-dist-label");

  const syncPrefs = () => {
    const minA = Math.min(65, Math.max(18, Number(minAgeEl.value) || 18));
    const maxA = Math.min(65, Math.max(minA, Number(maxAgeEl.value) || 45));
    const rawDist = Number(distEl.value) || 500;
    store.randomPrefs = {
      ...store.randomPrefs,
      minAge: minA,
      maxAge: maxA,
      maxDistanceKm: isUnlimited && rawDist >= MAX_PREMIUM_DISTANCE_KM ? UNLIMITED_DISTANCE_KM : rawDist
    };
  };

  distEl?.addEventListener("input", () => {
    const val = Number(distEl.value);
    distLabel.textContent =
      isUnlimited && val >= MAX_PREMIUM_DISTANCE_KM ? "Unlimited Global 🌍" : `${val} km`;
    syncPrefs();
  });
  minAgeEl?.addEventListener("change", syncPrefs);
  maxAgeEl?.addEventListener("change", syncPrefs);

  container.querySelector("#rnd-start-btn")?.addEventListener("click", () => {
    syncPrefs();
    actions.startRandomMatchmaking();
  });
  container.querySelector("#rnd-upgrade-btn")?.addEventListener("click", () => {
    store.activeTab = "profile";
    notify();
  });
  container.querySelectorAll("[data-rnd-open]").forEach((btn) => {
    btn.addEventListener("click", () => {
      actions.openExistingRandomSession(btn.getAttribute("data-rnd-open"));
    });
  });
  container.querySelectorAll("[data-rnd-delete]").forEach((btn) => {
    btn.addEventListener("click", () => {
      if (confirm("Delete this Random Chat session?")) {
        actions.deleteRandomSession(btn.getAttribute("data-rnd-delete"));
      }
    });
  });
}

function renderSearching(container, actions) {
  container.innerHTML = `
    <div class="card" style="max-width:520px;margin:40px auto;text-align:center;padding:42px 24px;">
      <div class="searching-radar">🎲</div>
      <h2>Searching for a Random Match...</h2>
      <p class="muted" style="margin:8px 0 22px;">
        Looking across active Milan App &amp; Web users matching your Gender, Age, and Distance preferences.
      </p>
      <button id="rnd-cancel-search" class="btn btn-outline">Cancel Search</button>
    </div>
  `;
  container.querySelector("#rnd-cancel-search")?.addEventListener("click", () => {
    actions.cancelRandomMatchmaking();
  });
}

function renderConnectedSession(container, actions) {
  const uid = store.user?.uid;
  const session = store.activeRandomSession;
  const masked = getMaskedRandomPartner();
  const partnerEnded = store.randomStatus === "PARTNER_LEFT" || session?.status === "ended";
  const reveals = getRandomSessionReveals(session);

  if (!session || !masked) {
    renderLobby(container, actions);
    return;
  }

  const amA = session.userAId === uid;
  const myConsent = (step) => {
    if (step === "NAME") return amA ? session.userAConsentName : session.userBConsentName;
    if (step === "AGE") return amA ? session.userAConsentAge : session.userBConsentAge;
    if (step === "PHOTO") return amA ? session.userAConsentPhoto : session.userBConsentPhoto;
    return amA ? session.userAConsentProfile : session.userBConsentProfile;
  };
  const partnerConsent = (step) => {
    if (step === "NAME") return amA ? session.userBConsentName : session.userAConsentName;
    if (step === "AGE") return amA ? session.userBConsentAge : session.userAConsentAge;
    if (step === "PHOTO") return amA ? session.userBConsentPhoto : session.userAConsentPhoto;
    return amA ? session.userBConsentProfile : session.userAConsentProfile;
  };
  const stepUnlocked = (step) => {
    if (step === "NAME") return reveals.isName;
    if (step === "AGE") return reveals.isAge;
    if (step === "PHOTO") return reveals.isPhoto;
    return reveals.isProfile;
  };
  const stepAvailable = (step) => {
    if (step === "NAME") return true;
    if (step === "AGE") return reveals.isName;
    if (step === "PHOTO") return reveals.isAge;
    return reveals.isPhoto;
  };

  const titleName = masked.revealedName ? masked.revealedName : "Anonymous Partner";
  const ageTag = masked.revealedAge ? `, ${masked.revealedAge} yrs` : "";

  container.innerHTML = `
    <div class="stack" style="max-width:860px;margin:0 auto;">
      <!-- Masked Partner Header -->
      <div class="card">
        <div class="row-between">
          <div class="row-gap">
            <button id="rnd-minimize-btn" class="icon-btn" title="Back to Random Lobby">←</button>
            ${
              masked.revealedPhotoBase64
                ? renderAvatarHtml(masked.revealedPhotoBase64, titleName, 60, {
                    circle: true,
                    isVerified: masked.isVerified
                  })
                : `<div class="avatar-wrap" style="width:60px;height:60px;">
                    <div class="avatar-box circle" style="background:linear-gradient(135deg,#8E7AB5,#E86F88);font-size:24px;">?</div>
                  </div>`
            }
            <div>
              <div class="row-gap" style="gap:6px;">
                <h3>${escapeHtml(titleName)}${escapeHtml(ageTag)}</h3>
                ${masked.isVerified ? `<span class="badge badge-verified">✓ Verified</span>` : ""}
              </div>
              <div class="row-gap" style="margin-top:4px;">
                <span class="chip chip-static">Gender: ${escapeHtml(masked.gender)}</span>
                <span class="badge ${masked.isOnline ? "badge-online" : "badge-offline"}">
                  <span class="dot ${masked.isOnline ? "dot-online" : "dot-offline"}"></span>
                  ${masked.isOnline ? "Online" : "Offline"}
                </span>
                ${masked.distanceKm !== null ? `<span class="chip chip-static">📍 ${masked.distanceKm} km</span>` : ""}
              </div>
            </div>
          </div>

          <div class="row-gap">
            <button id="rnd-next-btn" class="btn btn-sm btn-tonal">⏭️ Next</button>
            <button id="rnd-end-btn" class="btn btn-sm btn-outline">End Chat</button>
            <button id="rnd-block-btn" class="btn btn-sm btn-danger">🚫</button>
          </div>
        </div>

        <!-- 4-Step Mutual Consent Reveal Bar -->
        <div class="reveal-stepper">
          ${REVEAL_STEPS.map((st) => {
            const unlocked = stepUnlocked(st.key);
            const mine = myConsent(st.key);
            const theirs = partnerConsent(st.key);
            const avail = stepAvailable(st.key) && !partnerEnded;
            let statusText = "Locked";
            let btnHtml = "";
            if (unlocked) {
              statusText = "✓ Unlocked";
            } else if (!avail) {
              statusText = "Complete prev step";
            } else if (mine && !theirs) {
              statusText = "You said Yes • Waiting";
            } else if (!mine && theirs) {
              statusText = "Partner wants to reveal!";
              btnHtml = `<button class="btn btn-sm btn-primary" style="min-height:32px;padding:4px 10px;" data-reveal-step="${st.key}">Agree ✓</button>`;
            } else {
              statusText = "Tap to request";
              btnHtml = `<button class="btn btn-sm btn-outline" style="min-height:32px;padding:4px 10px;" data-reveal-step="${st.key}">Say Yes</button>`;
            }

            return `
              <div class="reveal-step-box ${unlocked ? "unlocked" : ""}">
                <strong style="font-size:12px;">${escapeHtml(st.title)}</strong>
                <span class="muted" style="font-size:11px;">${escapeHtml(statusText)}</span>
                ${btnHtml}
              </div>
            `;
          }).join("")}
        </div>

        ${
          masked.revealedFullProfile
            ? `<div class="card-soft row-between" style="margin-top:12px;">
                <div>
                  <strong>🎉 Full Profile Unlocked!</strong>
                  <p class="muted">${escapeHtml(masked.revealedFullProfile.bio || "")} • 📍 ${escapeHtml(masked.revealedFullProfile.locationName || "Nearby")}</p>
                </div>
                <div class="row-gap">
                  <button id="rnd-view-full-profile" class="btn btn-sm btn-outline">View Profile</button>
                  <button id="rnd-open-direct-chat" class="btn btn-sm btn-primary">💬 Permanent Chat</button>
                </div>
              </div>`
            : ""
        }
      </div>

      ${
        partnerEnded
          ? `<div class="card-soft row-between" style="background:var(--error-container);color:var(--on-error-container);">
              <strong>This Random Chat session has ended.</strong>
              <button id="rnd-ended-find-new" class="btn btn-sm btn-primary">Find New Partner</button>
            </div>`
          : ""
      }

      <!-- Messages Box -->
      <div class="chat-thread-pane" style="height:440px;">
        <div id="rnd-messages-scroll" class="chat-messages-scroll">
          ${
            store.randomMessages.length === 0
              ? `<div class="empty-state" style="margin:auto;border:none;background:transparent;">
                  <span class="empty-icon">🎲</span>
                  <strong>Connected anonymously!</strong>
                  <p>Send unlimited messages. Use the reveal steps above whenever you both feel ready.</p>
                </div>`
              : store.randomMessages
                  .map((m) => {
                    if (m.messageType === "system") {
                      return `<div class="msg-row system"><div class="msg-bubble">${escapeHtml(m.text)}</div></div>`;
                    }
                    const mine = m.senderId === uid;
                    return `
                      <div class="msg-row ${mine ? "mine" : "theirs"}">
                        <div class="msg-bubble">${escapeHtml(m.text)}</div>
                        <span class="msg-time">${escapeHtml(formatMessageDateTime(m.createdAt))}</span>
                      </div>
                    `;
                  })
                  .join("")
          }
        </div>
        <form id="rnd-send-form" class="chat-composer">
          <input id="rnd-text-input" class="input" type="text" maxlength="1000" placeholder="${partnerEnded ? "Session ended" : "Type an anonymous message..."}" ${partnerEnded ? "disabled" : ""} autocomplete="off" />
          <button type="submit" class="btn btn-primary" ${partnerEnded ? "disabled" : ""}>Send</button>
        </form>
      </div>
    </div>
  `;

  const msgScroll = container.querySelector("#rnd-messages-scroll");
  if (msgScroll) msgScroll.scrollTop = msgScroll.scrollHeight;

  container.querySelector("#rnd-minimize-btn")?.addEventListener("click", () => {
    actions.minimizeRandomChat();
  });
  container.querySelector("#rnd-next-btn")?.addEventListener("click", () => {
    actions.findNextRandomPartner();
  });
  container.querySelector("#rnd-end-btn")?.addEventListener("click", () => {
    actions.endRandomChat();
  });
  container.querySelector("#rnd-block-btn")?.addEventListener("click", () => {
    if (confirm("End chat and block this user?")) {
      actions.blockRandomPartner(masked.partnerId, masked.revealedName);
    }
  });
  container.querySelector("#rnd-ended-find-new")?.addEventListener("click", () => {
    actions.startRandomMatchmaking();
  });

  container.querySelectorAll("[data-reveal-step]").forEach((btn) => {
    btn.addEventListener("click", () => {
      actions.consentRandomRevealStep(btn.getAttribute("data-reveal-step"));
    });
  });

  container.querySelector("#rnd-view-full-profile")?.addEventListener("click", () => {
    if (masked.revealedFullProfile) {
      showProfileDetailModal(masked.revealedFullProfile, masked.distanceKm, actions);
    }
  });
  container.querySelector("#rnd-open-direct-chat")?.addEventListener("click", () => {
    if (masked.revealedFullProfile) {
      actions.openDirectChat(masked.revealedFullProfile);
    }
  });

  const form = container.querySelector("#rnd-send-form");
  const input = container.querySelector("#rnd-text-input");
  form?.addEventListener("submit", async (e) => {
    e.preventDefault();
    const text = input.value.trim();
    if (!text) return;
    input.value = "";
    await actions.sendRandomMessage(text);
  });
}
