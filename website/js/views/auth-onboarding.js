import { ALL_INTERESTS, AVATAR_PRESETS } from "../firebase-config.js";
import { store, notify, pushToast } from "../state.js";
import {
  compressImageToRawBase64,
  escapeHtml,
  getPhotoSrc,
  renderAvatarHtml,
  todayDateString
} from "../utils.js";

export function renderAuthLanding(container, actions) {
  const loc = store.preLoginLocation || { status: "idle", granted: false };
  const isChecking = loc.status === "checking";
  const isReady = Boolean(loc.granted && loc.latitude != null);

  container.innerHTML = `
    <div class="auth-page">
      <div class="auth-card-shell">
        <section class="auth-hero-panel">
          <div class="auth-brand-pill">❤ Milan Web &amp; App Sync</div>
          <h1>Find real connections near you.</h1>
          <p class="auth-tagline">
            Sign in with Google to browse verified profiles, chat across Android and Web in real time, and meet new people in Random Chat.
          </p>
          <div class="auth-feature-list">
            <div class="auth-feature-item">📍 Real-time distance matching &amp; location sync</div>
            <div class="auth-feature-item">💬 Instant App ↔ Website cross-platform messaging &amp; photo sharing</div>
            <div class="auth-feature-item">🎲 Anonymous Random Chat with 4-step mutual consent reveal</div>
            <div class="auth-feature-item">☑️ Verified Blue Tick profiles &amp; 6-day usage streak rewards</div>
          </div>
        </section>

        <section class="auth-login-panel">
          <div>
            <h2>Welcome to Milan</h2>
            <p class="muted" style="margin-top:6px;">
              Use your Google Account to sign up or log in. If you already use the Milan Android app, your profile, likes, and chats sync automatically.
            </p>
          </div>

          <div id="pre-login-location-card" class="card-soft" style="background:${
            isReady
              ? "color-mix(in srgb, var(--primary-container) 55%, var(--surface))"
              : "color-mix(in srgb, var(--secondary-container) 60%, var(--surface))"
          };">
            <div class="row-between" style="gap:12px;">
              <div style="min-width:0;flex:1;">
                <strong style="display:block;color:${isReady ? "var(--primary)" : "var(--on-surface)"};">
                  ${
                    isChecking
                      ? "⏳ Enabling Location &amp; GPS..."
                      : isReady
                        ? `✓ Location Active • ${escapeHtml(loc.locationName || "GPS Ready")}`
                        : "📍 Location Required Before Login"
                  }
                </strong>
                <span class="muted" style="font-size:12px;display:block;margin-top:2px;">
                  ${
                    isChecking
                      ? "Please tap 'Allow' in the browser location prompt."
                      : isReady
                        ? `GPS coordinates ready (${Number(loc.latitude).toFixed(4)}, ${Number(loc.longitude).toFixed(4)})`
                        : "Allow browser location & turn on GPS before signing in."
                  }
                </span>
              </div>
              <button
                type="button"
                id="enable-location-btn"
                class="btn btn-sm ${isReady ? "btn-outline" : "btn-tonal"}"
                ${isChecking ? "disabled" : ""}
              >
                ${isChecking ? "Checking..." : isReady ? "Refresh" : "Enable Location"}
              </button>
            </div>
            ${
              loc.error && !isReady
                ? `<p style="margin-top:8px;font-size:12px;color:var(--error);">${escapeHtml(loc.error)}</p>`
                : ""
            }
          </div>

          <button id="google-signin-btn" class="google-btn">
            <span class="google-g-badge">
              <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
                <path fill="#4285F4" d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.665-5.17 3.665-9.17Z"/>
                <path fill="#34A853" d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.11-6.72-4.96H1.29v3.14C3.26 21.3 7.31 24 12 24Z"/>
                <path fill="#FBBC05" d="M5.28 14.24c-.24-.72-.38-1.49-.38-2.24s.14-1.52.38-2.24V6.62H1.29C.47 8.24 0 10.06 0 12s.47 3.76 1.29 5.38l3.99-3.14Z"/>
                <path fill="#EA4335" d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.31 0 3.26 2.7 1.29 6.62l3.99 3.14c.95-2.85 3.6-4.96 6.72-4.96Z"/>
              </svg>
            </span>
            <span>${isReady ? "Continue with Google" : "Allow Location &amp; Continue with Google"}</span>
          </button>

          <div id="auth-error-box" class="card-soft hidden" style="background:var(--error-container);color:var(--on-error-container);"></div>

          <p class="muted" style="font-size:12px;">
            Connected to Firebase Auth &amp; Cloud Firestore. Must be 18+ to join Milan.
          </p>
        </section>
      </div>
    </div>
  `;

  const btn = container.querySelector("#google-signin-btn");
  const enableLocBtn = container.querySelector("#enable-location-btn");
  const errBox = container.querySelector("#auth-error-box");

  enableLocBtn?.addEventListener("click", async () => {
    errBox.classList.add("hidden");
    await actions.requestPreLoginLocation();
  });

  btn?.addEventListener("click", async () => {
    btn.disabled = true;
    errBox.classList.add("hidden");
    try {
      if (!store.preLoginLocation?.granted) {
        const locRes = await actions.requestPreLoginLocation();
        if (!locRes?.granted) {
          btn.disabled = false;
          errBox.textContent = locRes?.error || "Location permission is required before logging in.";
          errBox.classList.remove("hidden");
          return;
        }
      }
      await actions.signIn();
    } catch (err) {
      btn.disabled = false;
      errBox.textContent = err?.message || "Google Sign-In failed. Check Firebase Authorized Domains.";
      errBox.classList.remove("hidden");
    }
  });
}

export function renderOnboardingOrEdit(container, actions, existingProfile = null) {
  const isEditing = Boolean(existingProfile);
  const identity = store.googleIdentity;
  const lockedFirstName = existingProfile?.firstName || identity.firstName;
  const lockedLastName = existingProfile?.lastName || identity.lastName;
  const lockedAge = existingProfile?.age || identity.age;
  const lockedBirthDate = existingProfile?.birthDate || identity.birthDate;
  const lockedGender = existingProfile?.gender || identity.gender;
  const preLoc = store.preLoginLocation;

  let draft = {
    bio: existingProfile?.bio || "Looking for genuine friendships and meaningful connections ✨",
    interests: [...(existingProfile?.interests?.length ? existingProfile.interests : ["Music", "Travel", "Coffee"])],
    photoBase64: existingProfile?.photoBase64 || "avatar_rose",
    galleryPhotos: [...(existingProfile?.galleryPhotos || [])],
    latitude: existingProfile?.latitude ?? (preLoc?.granted && preLoc.latitude != null ? preLoc.latitude : 23.0225),
    longitude: existingProfile?.longitude ?? (preLoc?.granted && preLoc.longitude != null ? preLoc.longitude : 72.5714),
    locationName: existingProfile?.locationName || (preLoc?.granted && preLoc.locationName ? preLoc.locationName : "Ahmedabad, Gujarat"),
    visibility: existingProfile?.visibility || "public",
    allowRandomMeet: existingProfile?.allowRandomMeet !== false
  };

  let isSaving = false;

  const paint = () => {
    const fullName = `${lockedFirstName} ${lockedLastName}`.trim();
    container.innerHTML = `
      <div class="${isEditing ? "" : "auth-page"}" style="${isEditing ? "max-width:740px;margin:0 auto;" : ""}">
        <div class="card" style="width:100%;max-width:720px;margin:0 auto;">
          <div class="row-between" style="margin-bottom:14px;">
            <div>
              <h2>${isEditing ? "Edit Your Milan Profile" : "Complete Your Milan Profile"}</h2>
              <p class="muted">${isEditing ? "Update your photos, bio, interests, and location." : "Your profile will be visible immediately on both the Milan Android App and Website."}</p>
            </div>
            ${
              isEditing
                ? `<button id="cancel-edit-btn" class="btn btn-sm btn-outline">Cancel</button>`
                : `<button id="onboarding-signout-btn" class="btn btn-sm btn-outline">Sign Out</button>`
            }
          </div>

          <div class="stack">
            <!-- Locked Google Identity -->
            <div class="card-soft" style="background:color-mix(in srgb, var(--primary-container) 55%, var(--surface));">
              <div class="row-between" style="margin-bottom:8px;">
                <strong style="color:var(--primary);">✓ Synced from Google Account</strong>
                <span class="chip chip-static">Locked 🔒</span>
              </div>
              <div class="row-between">
                <span class="muted">Full Name</span>
                <strong>${escapeHtml(fullName)}</strong>
              </div>
              <div class="row-between">
                <span class="muted">Age (Auto-detected)</span>
                <strong>${Number(lockedAge)} years</strong>
              </div>
              <div class="row-between">
                <span class="muted">Gender (Auto-detected)</span>
                <strong>${escapeHtml(lockedGender)}</strong>
              </div>
            </div>

            <!-- Main Profile Photo & Avatar Presets -->
            <div class="stack-sm">
              <span class="field-label">Main Profile Photo</span>
              <div class="row-gap">
                ${renderAvatarHtml(draft.photoBase64, fullName, 84)}
                <div class="stack-sm" style="flex:1;min-width:200px;">
                  <label class="btn btn-tonal btn-sm" style="width:fit-content;cursor:pointer;">
                    📷 Upload Real Photo
                    <input id="main-photo-input" type="file" accept="image/*" hidden />
                  </label>
                  <div class="chips-row">
                    ${AVATAR_PRESETS.map(
                      (a) => `
                      <button type="button" class="chip ${draft.photoBase64 === a.id ? "selected" : ""}" data-avatar-preset="${a.id}">
                        ${escapeHtml(a.label)}
                      </button>
                    `
                    ).join("")}
                  </div>
                </div>
              </div>
            </div>

            <!-- 6-Photo Gallery -->
            <div class="stack-sm">
              <div class="row-between">
                <span class="field-label">Additional Gallery Photos (${draft.galleryPhotos.length}/6)</span>
                <label class="btn btn-sm btn-outline" style="cursor:pointer;">
                  + Add Photo
                  <input id="gallery-photo-input" type="file" accept="image/*" hidden ${draft.galleryPhotos.length >= 6 ? "disabled" : ""} />
                </label>
              </div>
              ${
                draft.galleryPhotos.length
                  ? `<div class="gallery-grid">
                      ${draft.galleryPhotos
                        .map(
                          (g, idx) => `
                        <div class="gallery-slot" style="border-style:solid;">
                          <img src="${getPhotoSrc(g)}" alt="Gallery ${idx + 1}" />
                          <button type="button" class="gallery-remove" data-remove-gallery="${idx}" title="Remove">✕</button>
                        </div>
                      `
                        )
                        .join("")}
                    </div>`
                  : `<p class="muted">Add up to 6 extra photos for your profile gallery.</p>`
              }
            </div>

            <!-- Bio -->
            <div class="field-group">
              <label class="field-label" for="ob-bio">About You (Bio)</label>
              <textarea id="ob-bio" class="textarea" maxlength="500" placeholder="Tell people a bit about yourself...">${escapeHtml(draft.bio)}</textarea>
            </div>

            <!-- Interests -->
            <div class="stack-sm">
              <span class="field-label">Interests (pick 1 to 6)</span>
              <div class="chips-row">
                ${ALL_INTERESTS.map(
                  (item) => `
                  <button type="button" class="chip ${draft.interests.includes(item) ? "selected" : ""}" data-interest="${escapeHtml(item)}">
                    ${escapeHtml(item)}
                  </button>
                `
                ).join("")}
              </div>
            </div>

            <!-- Location -->
            <div class="stack-sm">
              <div class="row-between">
                <label class="field-label" for="ob-location">Your City / Area</label>
                <button type="button" id="detect-gps-btn" class="btn btn-sm btn-tonal">📍 Use Browser GPS</button>
              </div>
              <input id="ob-location" class="input" maxlength="100" value="${escapeHtml(draft.locationName)}" placeholder="e.g. Ahmedabad, Gujarat" />
              <span class="muted">Coordinates: ${Number(draft.latitude).toFixed(4)}, ${Number(draft.longitude).toFixed(4)}</span>
            </div>

            <div class="row-between card-soft">
              <div>
                <strong>Allow Random Meet</strong>
                <p class="muted">Let other users match with you in Random Chat</p>
              </div>
              <input id="ob-random-meet" type="checkbox" style="width:22px;height:22px;accent-color:var(--primary);" ${draft.allowRandomMeet ? "checked" : ""} />
            </div>

            <button id="save-profile-btn" class="btn btn-primary" ${isSaving ? "disabled" : ""}>
              ${isSaving ? "Saving Profile..." : isEditing ? "Save Profile Changes" : "Save & Enter Milan"}
            </button>
          </div>
        </div>
      </div>
    `;

    container.querySelector("#cancel-edit-btn")?.addEventListener("click", () => {
      store.editProfileOpen = false;
      notify();
    });

    container.querySelector("#onboarding-signout-btn")?.addEventListener("click", () => {
      actions.signOut();
    });

    const bioEl = container.querySelector("#ob-bio");
    bioEl?.addEventListener("input", () => {
      draft.bio = bioEl.value;
    });

    const locEl = container.querySelector("#ob-location");
    locEl?.addEventListener("input", () => {
      draft.locationName = locEl.value;
    });

    const rndEl = container.querySelector("#ob-random-meet");
    rndEl?.addEventListener("change", () => {
      draft.allowRandomMeet = rndEl.checked;
    });

    container.querySelectorAll("[data-avatar-preset]").forEach((btn) => {
      btn.addEventListener("click", () => {
        draft.photoBase64 = btn.getAttribute("data-avatar-preset");
        paint();
      });
    });

    container.querySelector("#main-photo-input")?.addEventListener("change", async (e) => {
      const file = e.target.files?.[0];
      if (!file) return;
      try {
        draft.photoBase64 = await compressImageToRawBase64(file, 480, 0.72, 650000);
        paint();
      } catch (err) {
        pushToast("Photo Error", err.message, "error");
      }
    });

    container.querySelector("#gallery-photo-input")?.addEventListener("change", async (e) => {
      const file = e.target.files?.[0];
      if (!file || draft.galleryPhotos.length >= 6) return;
      try {
        const raw = await compressImageToRawBase64(file, 360, 0.65, 140000);
        draft.galleryPhotos.push(raw);
        paint();
      } catch (err) {
        pushToast("Photo Error", err.message, "error");
      }
    });

    container.querySelectorAll("[data-remove-gallery]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const idx = Number(btn.getAttribute("data-remove-gallery"));
        draft.galleryPhotos.splice(idx, 1);
        paint();
      });
    });

    container.querySelectorAll("[data-interest]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const val = btn.getAttribute("data-interest");
        if (draft.interests.includes(val)) {
          if (draft.interests.length > 1) {
            draft.interests = draft.interests.filter((x) => x !== val);
            paint();
          }
        } else if (draft.interests.length < 6) {
          draft.interests.push(val);
          paint();
        }
      });
    });

    container.querySelector("#detect-gps-btn")?.addEventListener("click", () => {
      if (!navigator.geolocation) {
        pushToast("GPS Unavailable", "Your browser does not support geolocation.", "error");
        return;
      }
      navigator.geolocation.getCurrentPosition(
        async (pos) => {
          draft.latitude = pos.coords.latitude;
          draft.longitude = pos.coords.longitude;
          try {
            const resp = await fetch(
              `https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=${draft.latitude}&lon=${draft.longitude}`
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
              if (city) {
                draft.locationName = state && city !== state ? `${city}, ${state}` : city;
              }
            }
          } catch {
            // Keep existing locationName if reverse geocoding is blocked
          }
          pushToast("Location Updated", `Synced coordinates (${draft.locationName})`);
          paint();
        },
        (err) => {
          pushToast("Location Permission", err.message || "Could not read GPS location.", "error");
        },
        { enableHighAccuracy: true, timeout: 10000 }
      );
    });

    container.querySelector("#save-profile-btn")?.addEventListener("click", async () => {
      isSaving = true;
      paint();
      try {
        const today = todayDateString();
        await actions.saveProfile(
          {
            ...existingProfile,
            firstName: store.isAdmin ? "admin" : lockedFirstName,
            lastName: lockedLastName,
            birthDate: lockedBirthDate,
            age: lockedAge,
            gender: lockedGender,
            bio: draft.bio.trim(),
            interests: draft.interests,
            photoBase64: draft.photoBase64,
            galleryPhotos: draft.galleryPhotos,
            latitude: draft.latitude,
            longitude: draft.longitude,
            locationName: draft.locationName.trim() || "Ahmedabad",
            isPremium: existingProfile?.isPremium || false,
            premiumPlan: existingProfile?.premiumPlan || "Free",
            premiumExpiresAt: existingProfile?.premiumExpiresAt || 0,
            dailyMessageCount: existingProfile?.dailyMessageCount || 0,
            dailyRandomFindCount: existingProfile?.dailyRandomFindCount || 0,
            lastRandomFindDate: existingProfile?.lastRandomFindDate || today,
            streakDays: existingProfile?.streakDays || 0,
            todayUsageSeconds: existingProfile?.todayUsageSeconds || 0,
            lastUsageDate: existingProfile?.lastUsageDate || today,
            lastStreakCompletedDate: existingProfile?.lastStreakCompletedDate || "",
            role: store.isAdmin ? "admin" : "user",
            visibility: draft.visibility,
            allowRandomMeet: draft.allowRandomMeet
          },
          isEditing
        );
        store.editProfileOpen = false;
        notify();
      } catch (err) {
        isSaving = false;
        paint();
        pushToast("Save Failed", err.message, "error");
      }
    });
  };

  paint();
}
