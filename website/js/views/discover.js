import {
  FREE_MAX_LIKES_LIMIT,
  MAX_FREE_DISTANCE_KM,
  MAX_PREMIUM_DISTANCE_KM
} from "../firebase-config.js";
import { store, notify, getDiscoverProfiles } from "../state.js";
import {
  avatarGradientStyle,
  escapeHtml,
  fullNameOf,
  getPhotoSrc,
  initialsOf,
  isCurrentlyOnline,
  isVerifiedWithBlueTick,
  renderAvatarHtml
} from "../utils.js";
import { showFilterModal, showProfileDetailModal } from "./modals.js";

export function renderDiscoverView(container, actions) {
  const my = store.myProfile;
  const list = getDiscoverProfiles();
  const sentLikeIds = new Set(store.sentLikes.map((l) => l.toUserId));
  const isPrem = Boolean(my?.isPremium);
  const f = store.filters;

  const distLabel =
    isPrem && f.maxDistanceKm >= MAX_PREMIUM_DISTANCE_KM
      ? "Worldwide"
      : `Within ${Math.min(isPrem ? MAX_PREMIUM_DISTANCE_KM : MAX_FREE_DISTANCE_KM, f.maxDistanceKm)} km`;

  container.innerHTML = `
    <div class="stack">
      <div class="discover-toolbar">
        <div class="search-box">
          <span class="search-box-icon" aria-hidden="true">🔍</span>
          <input id="discover-search" class="input" type="search" placeholder="Search by name, city, or bio..." value="${escapeHtml(f.searchQuery)}" />
        </div>
        <button id="discover-filter-btn" class="btn btn-outline">⚙️ Filters (${escapeHtml(distLabel)})</button>
        <button id="discover-sync-gps-btn" class="btn btn-tonal">📍 Sync GPS</button>
        <div class="row-gap">
          <button id="view-list-btn" class="icon-btn ${store.discoverViewMode === "list" ? "btn-primary" : ""}" title="List View" aria-label="List View">☰</button>
          <button id="view-tiles-btn" class="icon-btn ${store.discoverViewMode === "tiles" ? "btn-primary" : ""}" title="Tiles View" aria-label="Tiles View">⊞</button>
        </div>
      </div>

      ${
        !isPrem
          ? `<div class="card-soft row-between">
              <span class="muted">
                Free Plan • Likes used: <strong>${store.sentLikes.length}/${FREE_MAX_LIKES_LIMIT}</strong> • Range up to ${MAX_FREE_DISTANCE_KM} km
              </span>
              <button id="discover-upgrade-btn" class="btn btn-sm btn-gold">👑 Go Gold</button>
            </div>`
          : ""
      }

      ${
        list.length === 0
          ? `<div class="empty-state">
              <span class="empty-icon">✨</span>
              <strong>No matching profiles nearby</strong>
              <p>Try widening your distance or resetting your age and interest filters.</p>
              <button id="empty-reset-filters" class="btn btn-tonal btn-sm">Reset Filters</button>
            </div>`
          : store.discoverViewMode === "list"
            ? `<div class="discover-list">
                ${list.map(({ profile, distanceKm }) => renderListCard(profile, distanceKm, sentLikeIds.has(profile.userId))).join("")}
              </div>`
            : `<div class="discover-tiles">
                ${list.map(({ profile, distanceKm }) => renderTileCard(profile, distanceKm, sentLikeIds.has(profile.userId))).join("")}
              </div>`
      }
    </div>
  `;

  const searchInput = container.querySelector("#discover-search");
  searchInput?.addEventListener("input", () => {
    store.filters.searchQuery = searchInput.value;
    // Update only list area without losing input focus
    renderDiscoverResultsOnly(container, actions);
  });

  container.querySelector("#discover-filter-btn")?.addEventListener("click", () => {
    showFilterModal(() => renderDiscoverView(container, actions));
  });
  container.querySelector("#discover-sync-gps-btn")?.addEventListener("click", () => {
    actions.refreshBrowserGps();
  });
  container.querySelector("#view-list-btn")?.addEventListener("click", () => {
    store.discoverViewMode = "list";
    notify();
  });
  container.querySelector("#view-tiles-btn")?.addEventListener("click", () => {
    store.discoverViewMode = "tiles";
    notify();
  });
  container.querySelector("#discover-upgrade-btn")?.addEventListener("click", () => {
    store.activeTab = "profile";
    notify();
  });
  container.querySelector("#empty-reset-filters")?.addEventListener("click", () => {
    store.filters = {
      selectedGender: "All",
      maxDistanceKm: isPrem ? MAX_PREMIUM_DISTANCE_KM : MAX_FREE_DISTANCE_KM,
      minAge: 18,
      maxAge: 60,
      selectedInterest: "All",
      searchQuery: ""
    };
    notify();
  });

  bindCardEvents(container, list, actions);
}

function renderDiscoverResultsOnly(container, actions) {
  const list = getDiscoverProfiles();
  const sentLikeIds = new Set(store.sentLikes.map((l) => l.toUserId));
  const target = container.querySelector(".discover-list, .discover-tiles, .empty-state");
  if (!target) return;

  const wrapper = document.createElement("div");
  if (list.length === 0) {
    wrapper.innerHTML = `<div class="empty-state">
      <span class="empty-icon">✨</span>
      <strong>No matching profiles found</strong>
      <p>Try clearing your search or expanding filters.</p>
    </div>`;
  } else if (store.discoverViewMode === "list") {
    wrapper.innerHTML = `<div class="discover-list">
      ${list.map(({ profile, distanceKm }) => renderListCard(profile, distanceKm, sentLikeIds.has(profile.userId))).join("")}
    </div>`;
  } else {
    wrapper.innerHTML = `<div class="discover-tiles">
      ${list.map(({ profile, distanceKm }) => renderTileCard(profile, distanceKm, sentLikeIds.has(profile.userId))).join("")}
    </div>`;
  }

  target.replaceWith(wrapper.firstElementChild);
  bindCardEvents(container, list, actions);
}

function bindCardEvents(container, list, actions) {
  const map = new Map(list.map((item) => [item.profile.userId, item]));
  container.querySelectorAll("[data-profile-card]").forEach((card) => {
    const uid = card.getAttribute("data-profile-card");
    const entry = map.get(uid);
    if (!entry) return;

    card.addEventListener("click", (e) => {
      if (e.target.closest("[data-action]")) return;
      showProfileDetailModal(entry.profile, entry.distanceKm, actions);
    });
  });

  container.querySelectorAll("[data-action='like']").forEach((btn) => {
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      const entry = map.get(btn.getAttribute("data-uid"));
      if (entry) actions.toggleLike(entry.profile);
    });
  });

  container.querySelectorAll("[data-action='chat']").forEach((btn) => {
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      const entry = map.get(btn.getAttribute("data-uid"));
      if (entry) actions.initiateChat(entry.profile);
    });
  });
}

function renderListCard(profile, distanceKm, isLiked) {
  const online = isCurrentlyOnline(profile);
  const verified = isVerifiedWithBlueTick(profile);
  const interests = (profile.interests || []).slice(0, 3);

  return `
    <article class="card profile-row-card" data-profile-card="${escapeHtml(profile.userId)}">
      <div class="profile-row-main">
        ${renderAvatarHtml(profile, fullNameOf(profile), 74)}
        <div class="profile-row-info">
          <div class="profile-title-line">
            <span class="profile-name">${escapeHtml(fullNameOf(profile))}, ${Number(profile.age) || 22}</span>
            ${verified ? `<span class="badge badge-verified">✓ Verified</span>` : ""}
            ${profile.isPremium ? `<span class="badge badge-gold">👑 Gold</span>` : ""}
            <span class="badge ${online ? "badge-online" : "badge-offline"}">
              <span class="dot ${online ? "dot-online" : "dot-offline"}"></span>
              ${online ? "Online" : "Offline"}
            </span>
          </div>
          <p class="muted">📍 ${escapeHtml(profile.locationName || "Nearby")} • <strong>${distanceKm} km</strong> away</p>
          <p class="profile-bio-clamp">${escapeHtml(profile.bio || "Say hi and start a conversation!")}</p>
          ${
            interests.length
              ? `<div class="chips-row">${interests.map((i) => `<span class="chip chip-static">${escapeHtml(i)}</span>`).join("")}</div>`
              : ""
          }
        </div>
      </div>

      <div class="row-gap">
        <button class="btn btn-sm ${isLiked ? "btn-tonal" : "btn-outline"}" data-action="like" data-uid="${escapeHtml(profile.userId)}">
          ${isLiked ? "❤️ Liked" : "🤍 Like"}
        </button>
        <button class="btn btn-sm btn-primary" data-action="chat" data-uid="${escapeHtml(profile.userId)}">
          💬 Chat
        </button>
      </div>
    </article>
  `;
}

function renderTileCard(profile, distanceKm, isLiked) {
  const online = isCurrentlyOnline(profile);
  const verified = isVerifiedWithBlueTick(profile);
  const imgSrc = getPhotoSrc(profile.photoBase64);
  const name = fullNameOf(profile);

  return `
    <article class="card profile-tile-card" data-profile-card="${escapeHtml(profile.userId)}">
      <div class="tile-photo-box" style="background:${avatarGradientStyle(profile.photoBase64)};">
        ${
          imgSrc
            ? `<img src="${imgSrc}" alt="${escapeHtml(name)}" loading="lazy" />`
            : `<span>${escapeHtml(initialsOf(name))}</span>`
        }
        <div class="tile-top-badges">
          <span class="badge ${online ? "badge-online" : "badge-offline"}" style="background:rgba(255,255,255,0.92);">
            <span class="dot ${online ? "dot-online" : "dot-offline"}"></span>
            ${online ? "Online" : "Offline"}
          </span>
          <span class="tile-distance-pill">${distanceKm} km</span>
        </div>
      </div>

      <div class="stack-sm">
        <div class="row-between">
          <span class="profile-name">${escapeHtml(name)}, ${Number(profile.age) || 22}</span>
          ${verified ? `<span class="badge badge-verified">✓</span>` : ""}
        </div>
        <p class="muted">📍 ${escapeHtml(profile.locationName || "Nearby")}</p>
        <p class="profile-bio-clamp">${escapeHtml(profile.bio || "Say hi!")}</p>
      </div>

      <div class="row-between" style="margin-top:auto;">
        <button class="btn btn-sm ${isLiked ? "btn-tonal" : "btn-outline"}" style="flex:1;" data-action="like" data-uid="${escapeHtml(profile.userId)}">
          ${isLiked ? "❤️ Liked" : "🤍 Like"}
        </button>
        <button class="btn btn-sm btn-primary" style="flex:1;" data-action="chat" data-uid="${escapeHtml(profile.userId)}">
          💬 Chat
        </button>
      </div>
    </article>
  `;
}
