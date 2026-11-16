import {
  COLOR_THEMES,
  DAILY_STREAK_TARGET_SECONDS,
  MILAN_UPI_ID,
  STREAK_REQUIRED_DAYS,
  UPI_RECEIVER_NAME
} from "../firebase-config.js";
import { store, notify, setTheme, pushToast } from "../state.js";
import {
  compressImageToRawBase64,
  escapeHtml,
  formatMillisDateTime,
  fullNameOf,
  getPhotoSrc,
  isPaidPremium,
  isValidUpiUtr12Digits,
  isVerifiedWithBlueTick,
  renderAvatarHtml
} from "../utils.js";
import { openModal, closeModal, showPhotoLightbox } from "./modals.js";

export function renderProfileAndPremiumView(container, actions) {
  const my = store.myProfile;
  if (!my) return;

  const verified = isVerifiedWithBlueTick(my);
  const paidPrem = isPaidPremium(my);
  const todaySeconds = Number(my.todayUsageSeconds || 0);
  const progressPct = Math.min(100, Math.round((todaySeconds / DAILY_STREAK_TARGET_SECONDS) * 100));
  const minsDone = Math.floor(todaySeconds / 60);
  const gallery = my.galleryPhotos || [];

  container.innerHTML = `
    <div class="stack" style="max-width:860px;margin:0 auto;">
      <!-- Profile Summary Card -->
      <section class="card">
        <div class="row-between">
          <div class="row-gap">
            ${renderAvatarHtml(my, fullNameOf(my), 86)}
            <div class="stack-sm" style="gap:4px;">
              <div class="row-gap">
                <h2>${escapeHtml(fullNameOf(my))}, ${Number(my.age) || 22}</h2>
                ${verified ? `<span class="badge badge-verified">✓ Verified User</span>` : ""}
                ${my.isPremium ? `<span class="badge badge-gold">👑 ${escapeHtml(my.premiumPlan)} Gold</span>` : ""}
              </div>
              <p class="muted">📍 ${escapeHtml(my.locationName || "Nearby")} • ${escapeHtml(my.gender)} • ${escapeHtml(store.user?.email || "")}</p>
              <p>${escapeHtml(my.bio || "")}</p>
            </div>
          </div>

          <div class="row-gap">
            <button id="profile-edit-btn" class="btn btn-sm btn-primary">✏️ Edit Profile</button>
            <button id="profile-signout-btn" class="btn btn-sm btn-outline">Sign Out</button>
          </div>
        </div>

        <hr class="divider" style="margin:14px 0;" />

        <div class="row-between">
          <div>
            <strong>Additional Gallery Photos (${gallery.length}/6)</strong>
            <p class="muted">Synced directly with your Android app profile</p>
          </div>
          <label class="btn btn-sm btn-tonal" style="cursor:pointer;">
            + Add Photo
            <input id="profile-add-gallery" type="file" accept="image/*" hidden ${gallery.length >= 6 ? "disabled" : ""} />
          </label>
        </div>

        ${
          gallery.length
            ? `<div class="gallery-grid" style="margin-top:12px;">
                ${gallery
                  .map(
                    (g, idx) => `
                  <div class="gallery-slot" style="border-style:solid;" data-view-gal="${idx}">
                    <img src="${getPhotoSrc(g)}" alt="My photo ${idx + 1}" />
                    <button type="button" class="gallery-remove" data-del-gal="${idx}" title="Remove">✕</button>
                  </div>
                `
                  )
                  .join("")}
              </div>`
            : `<p class="muted" style="margin-top:8px;">No gallery photos uploaded yet.</p>`
        }
      </section>

      <!-- Milan Gold Hero & Streak -->
      <section class="gold-hero-card">
        <div style="font-size:34px;">👑</div>
        <h2>Milan Gold • Premium</h2>
        <p>
          ${
            my.isPremium
              ? `Your <strong>${escapeHtml(my.premiumPlan)} Gold</strong> plan is Active!${my.premiumExpiresAt > 0 ? ` Valid until ${escapeHtml(formatMillisDateTime(my.premiumExpiresAt))}.` : ""}`
              : "Unlock Unlimited Likes, Unlimited Messages, Worldwide Distance & Unlimited Random Finds."
          }
        </p>
      </section>

      ${
        my.paymentStatus === "PendingVerification"
          ? `<div class="card-soft" style="border-left:4px solid var(--primary);">
              <strong>⏳ UPI Payment Verification Pending</strong>
              <p class="muted">${escapeHtml(my.pendingPlan)} Gold (₹${Number(my.lastPaymentAmountInr || 199)}) • 12-Digit UTR: <strong>${escapeHtml(my.lastUpiTxnId)}</strong></p>
            </div>`
          : my.paymentStatus === "Rejected"
            ? `<div class="card-soft" style="background:var(--error-container);color:var(--on-error-container);">
                <strong>❌ Previous UPI Payment Not Verified</strong>
                <p>UTR (${escapeHtml(my.lastUpiTxnId)}) was rejected by Admin. You can submit a valid UTR below or contact Admin Support.</p>
              </div>`
            : ""
      }

      ${
        !paidPrem
          ? `<section class="card stack-sm">
              <div class="row-between">
                <h3>🔥 6-Day Active Usage Streak</h3>
                <strong>${Number(my.streakDays || 0)}/${STREAK_REQUIRED_DAYS} Days</strong>
              </div>
              <p class="muted">Spend 15 minutes active on Milan Web or Android App for 6 consecutive days to automatically unlock 24 Hours of Free Milan Gold!</p>
              <div class="progress-track">
                <div class="progress-fill" style="width:${progressPct}%;"></div>
              </div>
              <span class="muted">Today's active usage: <strong>${minsDone}/15 minutes</strong> (${progressPct}%)</span>
            </section>`
          : ""
      }

      <!-- Subscription Plans -->
      <section class="plans-grid">
        <div class="card stack-sm">
          <h3>Free Plan</h3>
          <div style="font-size:24px;font-weight:800;">₹0</div>
          <p class="muted">• 50 km max Discover range<br/>• 5 daily direct messages<br/>• 15 total likes &amp; 25 profile views<br/>• 2 Random Chat finds/day</p>
          <span class="chip chip-static" style="margin-top:auto;">${!my.isPremium ? "✓ Current Plan" : "Basic"}</span>
        </div>

        <div class="card stack-sm" style="border-color:var(--primary);">
          <div class="row-between">
            <h3>Monthly Gold</h3>
            <span class="badge badge-gold">Popular</span>
          </div>
          <div style="font-size:24px;font-weight:800;">₹199 <span class="muted">/ 30 days</span></div>
          <p class="muted">• Unlimited Likes &amp; Direct Messages<br/>• Worldwide Discover Distance<br/>• Unlimited Random Chat Finds</p>
          <button class="btn btn-primary" style="margin-top:auto;" data-buy-plan="Monthly" data-buy-amt="199">
            Upgrade Monthly (₹199)
          </button>
        </div>

        <div class="card stack-sm" style="border-color:var(--gold);">
          <div class="row-between">
            <h3>Yearly Gold</h3>
            <span class="badge badge-gold">Best Value</span>
          </div>
          <div style="font-size:24px;font-weight:800;">₹999 <span class="muted">/ 365 days</span></div>
          <p class="muted">• All Monthly Gold perks for a full year<br/>• Save ₹1,389/year</p>
          <button class="btn btn-gold" style="margin-top:auto;" data-buy-plan="Yearly" data-buy-amt="999">
            Upgrade Yearly (₹999)
          </button>
        </div>
      </section>

      <!-- Appearance & Theme -->
      <section class="card stack">
        <div class="row-between">
          <div>
            <h3>🎨 Appearance &amp; Theme</h3>
            <p class="muted">Pick your favorite Milan color theme and Dark/Light mode</p>
          </div>
          <button id="profile-dark-toggle" class="btn btn-sm ${store.darkMode ? "btn-primary" : "btn-outline"}">
            ${store.darkMode ? "🌙 Dark Mode" : "☀️ Light Mode"}
          </button>
        </div>
        <div class="theme-swatches">
          ${COLOR_THEMES.map(
            (t) => `
            <button class="theme-swatch-btn ${store.colorTheme === t.id ? "active" : ""}" data-profile-theme="${t.id}">
              <span class="swatch-dot" style="background:${t.swatch}"></span>
              <span>${escapeHtml(t.name)}</span>
            </button>
          `
          ).join("")}
        </div>
      </section>

      <!-- Blocked Users -->
      <section class="card stack-sm">
        <h3>🚫 Blocked Users (${store.blockedUsers.length})</h3>
        ${
          store.blockedUsers.length === 0
            ? `<p class="muted">You have not blocked anyone.</p>`
            : store.blockedUsers
                .map(
                  (b) => `
                <div class="row-between card-soft">
                  <strong>${escapeHtml(b.blockedUserName || b.blockedUserId)}</strong>
                  <button class="btn btn-sm btn-outline" data-unblock-uid="${escapeHtml(b.blockedUserId)}">Unblock</button>
                </div>
              `
                )
                .join("")
        }
      </section>

      ${store.isAdmin ? renderAdminPanelHtml() : ""}
    </div>
  `;

  container.querySelector("#profile-edit-btn")?.addEventListener("click", () => {
    store.editProfileOpen = true;
    notify();
  });
  container.querySelector("#profile-signout-btn")?.addEventListener("click", () => {
    actions.signOut();
  });

  container.querySelector("#profile-add-gallery")?.addEventListener("change", async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const raw = await compressImageToRawBase64(file, 360, 0.65, 140000);
      await actions.updateGallery([...gallery, raw].slice(0, 6));
    } catch (err) {
      pushToast("Photo Error", err.message, "error");
    }
  });

  container.querySelectorAll("[data-del-gal]").forEach((btn) => {
    btn.addEventListener("click", (e) => {
      e.stopPropagation();
      const idx = Number(btn.getAttribute("data-del-gal"));
      const updated = gallery.filter((_, i) => i !== idx);
      actions.updateGallery(updated);
    });
  });

  container.querySelectorAll("[data-view-gal]").forEach((slot) => {
    slot.addEventListener("click", () => {
      const idx = Number(slot.getAttribute("data-view-gal"));
      showPhotoLightbox(gallery[idx], `Gallery Photo ${idx + 1}`);
    });
  });

  container.querySelectorAll("[data-buy-plan]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const plan = btn.getAttribute("data-buy-plan");
      const amt = Number(btn.getAttribute("data-buy-amt"));
      openUpiPaymentModal(plan, amt, actions);
    });
  });

  container.querySelector("#profile-dark-toggle")?.addEventListener("click", () => {
    setTheme(null, !store.darkMode);
  });
  container.querySelectorAll("[data-profile-theme]").forEach((btn) => {
    btn.addEventListener("click", () => {
      setTheme(btn.getAttribute("data-profile-theme"), null);
    });
  });

  container.querySelectorAll("[data-unblock-uid]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const uid = btn.getAttribute("data-unblock-uid");
      const rec = store.blockedUsers.find((b) => b.blockedUserId === uid);
      actions.unblockUser(uid, rec?.blockedUserName || "User");
    });
  });

  if (store.isAdmin) {
    bindAdminEvents(container, actions);
  }
}

function openUpiPaymentModal(planName, amountInr, actions) {
  const upiLink = `upi://pay?pa=${encodeURIComponent(MILAN_UPI_ID)}&pn=${encodeURIComponent(UPI_RECEIVER_NAME)}&tn=${encodeURIComponent(`Milan ${planName} Gold`)}&am=${amountInr}&cu=INR`;

  openModal(
    `
    <div class="modal-header">
      <h3>Verify UPI Payment • ${escapeHtml(planName)} Gold</h3>
      <button class="icon-btn" data-close-modal aria-label="Close">✕</button>
    </div>
    <div class="stack">
      <div class="card-soft">
        <div><strong>Plan:</strong> Milan ${escapeHtml(planName)} Gold</div>
        <div><strong>Amount:</strong> ₹${amountInr} INR</div>
        <div class="row-between" style="margin-top:8px;">
          <span><strong>UPI ID:</strong> ${escapeHtml(MILAN_UPI_ID)}</span>
          <button id="copy-upi-id" class="btn btn-sm btn-outline">Copy UPI ID</button>
        </div>
      </div>

      <a href="${upiLink}" class="btn btn-tonal">📱 Open UPI App (on Mobile Browser)</a>

      <div class="field-group">
        <label class="field-label" for="utr-input">Step 2: Enter 12-Digit UPI UTR / Reference No.</label>
        <input id="utr-input" class="input" type="text" inputmode="numeric" maxlength="12" placeholder="e.g. 431289451023" />
        <span id="utr-hint" class="muted">Enter all 12 numeric digits from your GPay / PhonePe / Paytm receipt.</span>
      </div>

      <button id="submit-utr-btn" class="btn btn-primary" disabled>Submit UTR for Admin Verification</button>
    </div>
  `,
    (root) => {
      root.querySelector("#copy-upi-id")?.addEventListener("click", async () => {
        try {
          await navigator.clipboard.writeText(MILAN_UPI_ID);
          pushToast("Copied", `UPI ID ${MILAN_UPI_ID} copied to clipboard.`);
        } catch {
          pushToast("UPI ID", MILAN_UPI_ID);
        }
      });

      const utrInput = root.querySelector("#utr-input");
      const hint = root.querySelector("#utr-hint");
      const submitBtn = root.querySelector("#submit-utr-btn");

      utrInput?.addEventListener("input", () => {
        utrInput.value = utrInput.value.replace(/\D/g, "").slice(0, 12);
        const valid = isValidUpiUtr12Digits(utrInput.value);
        submitBtn.disabled = !valid;
        hint.textContent = valid
          ? "✓ Valid 12-digit UTR format. Ready to submit."
          : `Enter all 12 digits (${utrInput.value.length}/12).`;
      });

      submitBtn?.addEventListener("click", async () => {
        const cleanUtr = utrInput.value.trim();
        if (!isValidUpiUtr12Digits(cleanUtr)) return;
        closeModal();
        await actions.submitUpiUtr(planName, amountInr, cleanUtr);
      });
    }
  );
}

function renderAdminPanelHtml() {
  const users = store.publicProfiles;
  const pendingUpi = users.filter((u) => u.paymentStatus === "PendingVerification");
  const pendingFace = users.filter((u) => u.verificationStatus === "Pending" && (u.verificationVideoFrames || []).length > 0);

  return `
    <section class="card stack" style="border:2px solid var(--primary);">
      <div class="row-between">
        <div>
          <h3>🛡️ Admin Control Panel</h3>
          <p class="muted">Logged in as Admin (${escapeHtml(store.user?.email)}) • ${users.length} total public profiles</p>
        </div>
      </div>

      <div class="stack-sm">
        <h4>Pending UPI Payments (${pendingUpi.length})</h4>
        ${
          pendingUpi.length === 0
            ? `<p class="muted">No pending UPI UTR verification requests.</p>`
            : pendingUpi
                .map(
                  (u) => `
                <div class="card-soft row-between">
                  <div>
                    <strong>${escapeHtml(fullNameOf(u))}</strong>
                    <p class="muted">${escapeHtml(u.pendingPlan)} (₹${Number(u.lastPaymentAmountInr || 0)}) • UTR: <strong>${escapeHtml(u.lastUpiTxnId)}</strong></p>
                  </div>
                  <div class="row-gap">
                    <button class="btn btn-sm btn-danger" data-admin-upi="reject" data-uid="${escapeHtml(u.userId)}">Reject</button>
                    <button class="btn btn-sm btn-primary" data-admin-upi="approve" data-uid="${escapeHtml(u.userId)}">Approve</button>
                  </div>
                </div>
              `
                )
                .join("")
        }
      </div>

      <div class="stack-sm">
        <h4>Pending Face Verifications (${pendingFace.length})</h4>
        ${
          pendingFace.length === 0
            ? `<p class="muted">No pending live face verification videos.</p>`
            : pendingFace
                .map(
                  (u) => `
                <div class="card-soft stack-sm">
                  <div class="row-between">
                    <strong>${escapeHtml(fullNameOf(u))} (${Number(u.age)} yrs)</strong>
                    <div class="row-gap">
                      <button class="btn btn-sm btn-danger" data-admin-face="reject" data-uid="${escapeHtml(u.userId)}">Unverified</button>
                      <button class="btn btn-sm btn-primary" data-admin-face="approve" data-uid="${escapeHtml(u.userId)}">Approve Blue Tick</button>
                    </div>
                  </div>
                  <div class="row-gap">
                    ${(u.verificationVideoFrames || [])
                      .slice(0, 6)
                      .map((f) => `<img src="${getPhotoSrc(f)}" alt="Face frame" style="width:58px;height:58px;object-fit:cover;border-radius:10px;" />`)
                      .join("")}
                  </div>
                </div>
              `
                )
                .join("")
        }
      </div>
    </section>
  `;
}

function bindAdminEvents(container, actions) {
  const map = new Map(store.publicProfiles.map((u) => [u.userId, u]));
  container.querySelectorAll("[data-admin-upi]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const target = map.get(btn.getAttribute("data-uid"));
      const approve = btn.getAttribute("data-admin-upi") === "approve";
      if (target) actions.adminVerifyUpi(target, approve);
    });
  });
  container.querySelectorAll("[data-admin-face]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const target = map.get(btn.getAttribute("data-uid"));
      const approve = btn.getAttribute("data-admin-face") === "approve";
      if (target) actions.adminVerifyFace(target, approve);
    });
  });
}
