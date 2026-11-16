import {
  COLOR_THEMES,
  MAX_FREE_DISTANCE_KM,
  MAX_PREMIUM_DISTANCE_KM,
  ALL_INTERESTS
} from "../firebase-config.js";
import {
  store,
  notify,
  setTheme,
  markAllNotificationsRead,
  pushToast
} from "../state.js";
import {
  escapeHtml,
  fullNameOf,
  getPhotoSrc,
  isCurrentlyOnline,
  isVerifiedWithBlueTick,
  renderAvatarHtml,
  formatMillisDateTime
} from "../utils.js";

const modalRoot = () => document.getElementById("modal-root");

export function closeModal() {
  const root = modalRoot();
  if (!root) return;
  root.classList.add("hidden");
  root.innerHTML = "";
}

export function openModal(html, onBind) {
  const root = modalRoot();
  if (!root) return;
  root.innerHTML = `<div class="modal-card">${html}</div>`;
  root.classList.remove("hidden");

  root.onclick = (e) => {
    if (e.target === root) closeModal();
  };
  root.querySelectorAll("[data-close-modal]").forEach((btn) => {
    btn.addEventListener("click", closeModal);
  });
  onBind?.(root);
}

export function showThemeModal() {
  const html = `
    <div class="modal-header">
      <h3>Appearance & Theme</h3>
      <button class="icon-btn" data-close-modal aria-label="Close">✕</button>
    </div>
    <div class="stack">
      <div class="row-between card-soft">
        <div>
          <strong>Dark Mode</strong>
          <p class="muted">Switch between warm light and comfortable dark mode</p>
        </div>
        <button id="toggle-dark-mode-btn" class="btn btn-sm ${store.darkMode ? "btn-primary" : "btn-outline"}">
          ${store.darkMode ? "🌙 Dark On" : "☀️ Light On"}
        </button>
      </div>
      <div class="stack-sm">
        <span class="field-label">Color Palette</span>
        <div class="theme-swatches">
          ${COLOR_THEMES.map(
            (t) => `
            <button class="theme-swatch-btn ${store.colorTheme === t.id ? "active" : ""}" data-pick-theme="${t.id}">
              <span class="swatch-dot" style="background:${t.swatch}"></span>
              <span>${escapeHtml(t.name)}</span>
            </button>
          `
          ).join("")}
        </div>
      </div>
    </div>
  `;

  openModal(html, (root) => {
    root.querySelector("#toggle-dark-mode-btn")?.addEventListener("click", () => {
      setTheme(null, !store.darkMode);
      showThemeModal();
    });
    root.querySelectorAll("[data-pick-theme]").forEach((btn) => {
      btn.addEventListener("click", () => {
        setTheme(btn.getAttribute("data-pick-theme"), null);
        showThemeModal();
      });
    });
  });
}

export function showNotificationsModal() {
  markAllNotificationsRead();
  const list = store.notifications;
  const html = `
    <div class="modal-header">
      <h3>Notifications (${list.length})</h3>
      <div class="row-gap">
        ${list.length ? `<button id="clear-notifs-btn" class="btn btn-sm btn-outline">Clear</button>` : ""}
        <button class="icon-btn" data-close-modal aria-label="Close">✕</button>
      </div>
    </div>
    <div class="stack-sm">
      ${
        list.length === 0
          ? `<div class="empty-state"><span class="empty-icon">🔔</span><strong>No notifications yet</strong><p>New likes, messages, and streak rewards appear here in real time.</p></div>`
          : list
              .map(
                (n) => `
            <div class="card-soft" style="${n.isReward ? "border-left:4px solid var(--gold);" : ""}">
              <div class="row-between">
                <strong>${escapeHtml(n.title)}</strong>
                <span class="muted">${escapeHtml(formatMillisDateTime(n.timestampMs))}</span>
              </div>
              <p class="muted" style="margin-top:4px;">${escapeHtml(n.body)}</p>
            </div>
          `
              )
              .join("")
      }
    </div>
  `;
  openModal(html, (root) => {
    root.querySelector("#clear-notifs-btn")?.addEventListener("click", () => {
      store.notifications = [];
      notify();
      showNotificationsModal();
    });
  });
}

export function showPhotoLightbox(photoBase64, caption = "") {
  const src = getPhotoSrc(photoBase64);
  if (!src) return;
  openModal(`
    <div class="modal-header">
      <h3>${escapeHtml(caption || "Photo")}</h3>
      <button class="icon-btn" data-close-modal aria-label="Close">✕</button>
    </div>
    <img src="${src}" alt="${escapeHtml(caption || "Photo")}" style="width:100%;max-height:70vh;object-fit:contain;border-radius:16px;" />
  `);
}

export function showProfileDetailModal(profile, distanceKm, actions) {
  const isLiked = store.sentLikes.some((l) => l.toUserId === profile.userId);
  const online = isCurrentlyOnline(profile);
  const verified = isVerifiedWithBlueTick(profile);
  const gallery = (profile.galleryPhotos || []).filter((p) => getPhotoSrc(p));
  const interests = profile.interests || [];

  const html = `
    <div class="modal-header">
      <div class="row-gap">
        <h3>${escapeHtml(fullNameOf(profile))}, ${Number(profile.age) || 22}</h3>
        ${verified ? `<span class="badge badge-verified">✓ Verified</span>` : ""}
        ${profile.isPremium ? `<span class="badge badge-gold">👑 Gold</span>` : ""}
      </div>
      <button class="icon-btn" data-close-modal aria-label="Close">✕</button>
    </div>

    <div class="row-gap" style="align-items:flex-start;">
      ${renderAvatarHtml(profile, fullNameOf(profile), 96)}
      <div class="stack-sm" style="flex:1;min-width:200px;">
        <div class="row-gap">
          <span class="badge ${online ? "badge-online" : "badge-offline"}">
            <span class="dot ${online ? "dot-online" : "dot-offline"}"></span>
            ${online ? "Online Now" : "Offline"}
          </span>
          <span class="chip chip-static">${escapeHtml(profile.gender || "Member")}</span>
          ${distanceKm !== null && distanceKm !== undefined ? `<span class="chip chip-static">📍 ${distanceKm} km away</span>` : ""}
        </div>
        <p class="muted">📍 ${escapeHtml(profile.locationName || "Nearby")}</p>
        <p>${escapeHtml(profile.bio || "No bio added yet.")}</p>
      </div>
    </div>

    ${
      interests.length
        ? `<div class="stack-sm">
            <span class="field-label">Interests</span>
            <div class="chips-row">${interests.map((i) => `<span class="chip chip-static">${escapeHtml(i)}</span>`).join("")}</div>
          </div>`
        : ""
    }

    ${
      gallery.length
        ? `<div class="stack-sm">
            <span class="field-label">Gallery (${gallery.length})</span>
            <div class="gallery-grid">
              ${gallery
                .map(
                  (g, idx) => `
                  <div class="gallery-slot" data-gallery-idx="${idx}" style="border-style:solid;">
                    <img src="${getPhotoSrc(g)}" alt="Gallery ${idx + 1}" />
                  </div>
                `
                )
                .join("")}
            </div>
          </div>`
        : ""
    }

    <hr class="divider" />
    <div class="row-between">
      <button id="detail-block-btn" class="btn btn-sm btn-danger">🚫 Block</button>
      <div class="row-gap">
        <button id="detail-like-btn" class="btn btn-sm ${isLiked ? "btn-tonal" : "btn-outline"}">
          ${isLiked ? "❤️ Liked" : "🤍 Like"}
        </button>
        <button id="detail-chat-btn" class="btn btn-sm btn-primary">💬 Message</button>
      </div>
    </div>
  `;

  openModal(html, (root) => {
    root.querySelectorAll("[data-gallery-idx]").forEach((el) => {
      el.addEventListener("click", () => {
        const idx = Number(el.getAttribute("data-gallery-idx"));
        showPhotoLightbox(gallery[idx], `${fullNameOf(profile)} • Photo ${idx + 1}`);
      });
    });
    root.querySelector("#detail-like-btn")?.addEventListener("click", async () => {
      await actions.toggleLike(profile);
      showProfileDetailModal(profile, distanceKm, actions);
    });
    root.querySelector("#detail-chat-btn")?.addEventListener("click", () => {
      closeModal();
      actions.initiateChat(profile);
    });
    root.querySelector("#detail-block-btn")?.addEventListener("click", async () => {
      if (confirm(`Block ${fullNameOf(profile)}? You won't see each other in Discover or Chats.`)) {
        closeModal();
        await actions.blockUser(profile.userId, fullNameOf(profile));
      }
    });
  });
}

export function showLikeGateModal(targetProfile, actions) {
  const html = `
    <div class="modal-header">
      <h3>Like ${escapeHtml(targetProfile.firstName)} to Chat</h3>
      <button class="icon-btn" data-close-modal aria-label="Close">✕</button>
    </div>
    <p>Free accounts need to send a Like before starting a direct chat. Send a quick Like to unlock messaging with <strong>${escapeHtml(fullNameOf(targetProfile))}</strong>, or upgrade to Milan Gold Premium for unlimited direct chats!</p>
    <div class="row-between" style="margin-top:8px;">
      <button id="gate-premium-btn" class="btn btn-gold">👑 Upgrade Gold</button>
      <button id="gate-like-chat-btn" class="btn btn-primary">❤️ Like & Chat</button>
    </div>
  `;
  openModal(html, (root) => {
    root.querySelector("#gate-premium-btn")?.addEventListener("click", () => {
      closeModal();
      store.activeTab = "profile";
      notify();
    });
    root.querySelector("#gate-like-chat-btn")?.addEventListener("click", async () => {
      closeModal();
      await actions.likeAndOpenChat(targetProfile);
    });
  });
}

export function showFilterModal(onApply) {
  const my = store.myProfile;
  const isPrem = Boolean(my?.isPremium);
  const maxAllowedDist = isPrem ? MAX_PREMIUM_DISTANCE_KM : MAX_FREE_DISTANCE_KM;
  const draft = { ...store.filters };

  const renderInner = () => {
    const html = `
      <div class="modal-header">
        <h3>Discover Filters</h3>
        <button class="icon-btn" data-close-modal aria-label="Close">✕</button>
      </div>
      <div class="stack">
        <div class="field-group">
          <div class="row-between">
            <label class="field-label">Maximum Distance</label>
            <strong>${draft.maxDistanceKm >= MAX_PREMIUM_DISTANCE_KM && isPrem ? "Unlimited 🌍" : `${draft.maxDistanceKm} km`}</strong>
          </div>
          <input id="filter-dist" type="range" min="5" max="${maxAllowedDist}" step="5" value="${Math.min(maxAllowedDist, draft.maxDistanceKm)}" />
          ${
            !isPrem
              ? `<span class="muted">Free plan max range is ${MAX_FREE_DISTANCE_KM} km. Upgrade to Milan Gold for worldwide distance.</span>`
              : ""
          }
        </div>

        <div class="row-gap">
          <div class="field-group" style="flex:1;">
            <label class="field-label" for="filter-min-age">Min Age</label>
            <input id="filter-min-age" class="input" type="number" min="18" max="80" value="${draft.minAge}" />
          </div>
          <div class="field-group" style="flex:1;">
            <label class="field-label" for="filter-max-age">Max Age</label>
            <input id="filter-max-age" class="input" type="number" min="18" max="80" value="${draft.maxAge}" />
          </div>
        </div>

        <div class="field-group">
          <label class="field-label" for="filter-interest">Interest</label>
          <select id="filter-interest" class="select">
            <option value="All" ${draft.selectedInterest === "All" ? "selected" : ""}>All Interests</option>
            ${ALL_INTERESTS.map(
              (i) => `<option value="${escapeHtml(i)}" ${draft.selectedInterest === i ? "selected" : ""}>${escapeHtml(i)}</option>`
            ).join("")}
          </select>
        </div>

        <div class="row-between">
          <button id="filter-reset-btn" class="btn btn-outline">Reset</button>
          <button id="filter-apply-btn" class="btn btn-primary">Apply Filters</button>
        </div>
      </div>
    `;

    openModal(html, (root) => {
      const distInput = root.querySelector("#filter-dist");
      distInput?.addEventListener("input", () => {
        draft.maxDistanceKm = Number(distInput.value);
        const label = distInput.parentElement.querySelector("strong");
        if (label) {
          label.textContent =
            draft.maxDistanceKm >= MAX_PREMIUM_DISTANCE_KM && isPrem
              ? "Unlimited 🌍"
              : `${draft.maxDistanceKm} km`;
        }
      });

      root.querySelector("#filter-reset-btn")?.addEventListener("click", () => {
        store.filters = {
          selectedGender: "All",
          maxDistanceKm: isPrem ? MAX_PREMIUM_DISTANCE_KM : MAX_FREE_DISTANCE_KM,
          minAge: 18,
          maxAge: 60,
          selectedInterest: "All",
          searchQuery: ""
        };
        closeModal();
        onApply();
        pushToast("Filters reset", "Showing all matching nearby profiles.");
      });

      root.querySelector("#filter-apply-btn")?.addEventListener("click", () => {
        const minA = Math.min(80, Math.max(18, Number(root.querySelector("#filter-min-age").value) || 18));
        const maxA = Math.min(80, Math.max(minA, Number(root.querySelector("#filter-max-age").value) || 60));
        store.filters = {
          ...store.filters,
          maxDistanceKm: Math.min(maxAllowedDist, Math.max(5, Number(distInput.value) || MAX_FREE_DISTANCE_KM)),
          minAge: minA,
          maxAge: maxA,
          selectedInterest: root.querySelector("#filter-interest").value || "All"
        };
        closeModal();
        onApply();
      });
    });
  };

  renderInner();
}
