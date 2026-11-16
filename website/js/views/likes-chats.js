import {
  ADMIN_SUPPORT_USER_ID,
  FREE_DAILY_MESSAGE_LIMIT
} from "../firebase-config.js";
import {
  store,
  notify,
  pushToast,
  getConversations,
  getActiveChatPartnerProfile,
  buildAdminSupportProfile,
  isAdminSupportPartner
} from "../state.js";
import {
  buildChatId,
  compressImageToRawBase64,
  escapeHtml,
  formatMessageDateTime,
  fullNameOf,
  getPhotoSrc,
  isCurrentlyOnline,
  isVerifiedWithBlueTick,
  renderAvatarHtml
} from "../utils.js";
import { showPhotoLightbox, showProfileDetailModal } from "./modals.js";

export function renderLikesView(container, actions) {
  const sub = store.likesSubTab;
  const profileMap = new Map(store.publicProfiles.map((p) => [p.userId, p]));
  const sentSet = new Set(store.sentLikes.map((l) => l.toUserId));

  const items = sub === "received" ? store.receivedLikes : store.sentLikes;

  container.innerHTML = `
    <div class="stack">
      <div class="row-between">
        <div class="chips-row">
          <button id="likes-tab-received" class="chip ${sub === "received" ? "selected" : ""}">
            ❤️ Liked You (${store.receivedLikes.length})
          </button>
          <button id="likes-tab-sent" class="chip ${sub === "sent" ? "selected" : ""}">
            💌 Sent Likes (${store.sentLikes.length})
          </button>
        </div>
      </div>

      ${
        items.length === 0
          ? `<div class="empty-state">
              <span class="empty-icon">${sub === "received" ? "💝" : "🤍"}</span>
              <strong>${sub === "received" ? "No likes received yet" : "You haven't liked anyone yet"}</strong>
              <p>Explore profiles in Discover and tap Like to connect with people near you.</p>
            </div>`
          : `<div class="discover-list">
              ${items
                .map((like) => {
                  const otherId = sub === "received" ? like.fromUserId : like.toUserId;
                  const otherName = sub === "received" ? like.fromUserName : like.toUserName;
                  const p = profileMap.get(otherId) || {
                    userId: otherId,
                    firstName: (otherName || "Milan").split(" ")[0],
                    lastName: (otherName || "").split(" ").slice(1).join(" "),
                    photoBase64: "avatar_rose",
                    age: 24,
                    locationName: "Nearby"
                  };
                  const isMutual = sub === "received" && sentSet.has(otherId);
                  const online = isCurrentlyOnline(p);

                  return `
                    <article class="card profile-row-card" data-like-profile="${escapeHtml(otherId)}">
                      <div class="profile-row-main">
                        ${renderAvatarHtml(p, fullNameOf(p), 64)}
                        <div class="profile-row-info">
                          <div class="profile-title-line">
                            <span class="profile-name">${escapeHtml(otherName || fullNameOf(p))}</span>
                            ${isVerifiedWithBlueTick(p) ? `<span class="badge badge-verified">✓ Verified</span>` : ""}
                            ${isMutual ? `<span class="badge badge-gold">✨ Mutual Match</span>` : ""}
                            <span class="badge ${online ? "badge-online" : "badge-offline"}">
                              <span class="dot ${online ? "dot-online" : "dot-offline"}"></span>
                              ${online ? "Online" : "Offline"}
                            </span>
                          </div>
                          <p class="muted">📍 ${escapeHtml(p.locationName || "Nearby")} • ${escapeHtml(formatMessageDateTime(like.createdAt))}</p>
                        </div>
                      </div>

                      <div class="row-gap">
                        ${
                          sub === "sent"
                            ? `<button class="btn btn-sm btn-outline" data-unlike-uid="${escapeHtml(otherId)}">Remove</button>`
                            : !isMutual
                              ? `<button class="btn btn-sm btn-tonal" data-likeback-uid="${escapeHtml(otherId)}">❤️ Like Back</button>`
                              : ""
                        }
                        <button class="btn btn-sm btn-primary" data-chat-uid="${escapeHtml(otherId)}">💬 Message</button>
                      </div>
                    </article>
                  `;
                })
                .join("")}
            </div>`
      }
    </div>
  `;

  container.querySelector("#likes-tab-received")?.addEventListener("click", () => {
    store.likesSubTab = "received";
    notify();
  });
  container.querySelector("#likes-tab-sent")?.addEventListener("click", () => {
    store.likesSubTab = "sent";
    notify();
  });

  const resolveProfile = (uid, fallbackName) =>
    profileMap.get(uid) || {
      userId: uid,
      firstName: (fallbackName || "Milan").split(" ")[0],
      lastName: (fallbackName || "").split(" ").slice(1).join(" "),
      photoBase64: "avatar_rose",
      age: 24,
      locationName: "Nearby"
    };

  container.querySelectorAll("[data-like-profile]").forEach((card) => {
    card.addEventListener("click", (e) => {
      if (e.target.closest("button")) return;
      const uid = card.getAttribute("data-like-profile");
      const p = profileMap.get(uid);
      if (p) showProfileDetailModal(p, null, actions);
    });
  });

  container.querySelectorAll("[data-unlike-uid]").forEach((btn) => {
    btn.addEventListener("click", () => {
      actions.removeLike(btn.getAttribute("data-unlike-uid"));
    });
  });

  container.querySelectorAll("[data-likeback-uid]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const uid = btn.getAttribute("data-likeback-uid");
      const like = store.receivedLikes.find((l) => l.fromUserId === uid);
      actions.toggleLike(resolveProfile(uid, like?.fromUserName));
    });
  });

  container.querySelectorAll("[data-chat-uid]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const uid = btn.getAttribute("data-chat-uid");
      const like = items.find((l) => (sub === "received" ? l.fromUserId : l.toUserId) === uid);
      const name = sub === "received" ? like?.fromUserName : like?.toUserName;
      actions.openDirectChat(resolveProfile(uid, name));
    });
  });
}

export function renderChatsView(container, actions) {
  const my = store.myProfile;
  const uid = store.user?.uid;
  const convos = getConversations();
  const partner = getActiveChatPartnerProfile();
  const isPrem = Boolean(my?.isPremium);
  const dailySent = Number(my?.dailyMessageCount || 0);
  const remainingFree = Math.max(0, FREE_DAILY_MESSAGE_LIMIT - dailySent);

  const activeChatId = partner ? buildChatId(uid, partner.userId) : null;
  const threadMessages = activeChatId
    ? store.messages.filter((m) => m.chatId === activeChatId)
    : [];

  const mobileClass = partner ? "mobile-in-thread" : "mobile-in-inbox";

  container.innerHTML = `
    <div class="chat-layout ${mobileClass}">
      <!-- Left Pane: Conversations -->
      <section class="chat-inbox-pane">
        <div class="chat-pane-header">
          <div>
            <h3>Messages</h3>
            <span class="muted">App &amp; Web real-time sync</span>
          </div>
          ${
            !store.isAdmin
              ? `<button id="chat-admin-support-btn" class="btn btn-sm btn-tonal" title="Message Admin Support">🛟 Support</button>`
              : ""
          }
        </div>
        <div class="chat-inbox-list">
          ${
            convos.length === 0
              ? `<div class="empty-state" style="margin:12px;">
                  <span class="empty-icon">💬</span>
                  <strong>No conversations yet</strong>
                  <p>Like or message someone in Discover to start chatting across App &amp; Web.</p>
                </div>`
              : convos
                  .map((c) => {
                    const p = c.otherUserProfile || {
                      userId: c.otherUserId,
                      firstName: c.otherUserName,
                      lastName: "",
                      photoBase64: "avatar_rose"
                    };
                    const active = partner?.userId === c.otherUserId;
                    const online = isCurrentlyOnline(p);
                    return `
                      <button class="chat-convo-item ${active ? "active" : ""}" data-open-convo="${escapeHtml(c.otherUserId)}">
                        ${renderAvatarHtml(p, c.otherUserName, 48, { circle: true })}
                        <div style="flex:1;min-width:0;">
                          <div class="row-between">
                            <strong style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                              ${escapeHtml(c.otherUserName)}
                            </strong>
                            ${
                              online
                                ? `<span class="dot dot-online" title="Online"></span>`
                                : ""
                            }
                          </div>
                          <p class="muted" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:2px;">
                            ${escapeHtml(c.lastMessageText)}
                          </p>
                        </div>
                      </button>
                    `;
                  })
                  .join("")
          }
        </div>
      </section>

      <!-- Right Pane: Active Thread -->
      <section class="chat-thread-pane">
        ${
          !partner
            ? `<div class="empty-state" style="margin:auto;max-width:380px;border:none;background:transparent;">
                <span class="empty-icon">💌</span>
                <h3>Select a conversation</h3>
                <p>Pick a match on the left or start a chat from Discover to send messages and photos seamlessly between Website and Android App.</p>
              </div>`
            : `
              <div class="chat-pane-header">
                <div class="row-gap" style="min-width:0;">
                  <button id="chat-mobile-back" class="icon-btn" title="Back to Inbox" aria-label="Back to Inbox">←</button>
                  ${renderAvatarHtml(partner, fullNameOf(partner), 44, { circle: true })}
                  <div style="min-width:0;">
                    <div class="row-gap" style="gap:6px;">
                      <strong>${escapeHtml(fullNameOf(partner))}</strong>
                      ${isVerifiedWithBlueTick(partner) ? `<span class="badge badge-verified">✓</span>` : ""}
                    </div>
                    <span class="muted">
                      ${
                        partner.userId === ADMIN_SUPPORT_USER_ID
                          ? "Official Milan Support"
                          : isCurrentlyOnline(partner)
                            ? "🟢 Online"
                            : `📍 ${escapeHtml(partner.locationName || "Nearby")}`
                      }
                    </span>
                  </div>
                </div>
                <div class="row-gap">
                  <button id="chat-delete-thread" class="icon-btn" title="Delete Conversation" aria-label="Delete Conversation">🗑️</button>
                  ${
                    !isAdminSupportPartner(partner)
                      ? `<button id="chat-block-user" class="icon-btn" title="Block User" aria-label="Block User">🚫</button>`
                      : ""
                  }
                </div>
              </div>

              ${
                !isPrem && !store.isAdmin && !isAdminSupportPartner(partner)
                  ? `<div class="row-between" style="padding:8px 16px;background:var(--surface-variant);border-bottom:1px solid var(--outline);font-size:12px;">
                      <span>Free daily messages left: <strong>${remainingFree}/${FREE_DAILY_MESSAGE_LIMIT}</strong></span>
                      <button id="chat-go-gold" class="btn btn-sm btn-gold" style="min-height:30px;padding:4px 10px;font-size:11px;">👑 Unlimited</button>
                    </div>`
                  : ""
              }

              <div id="chat-messages-scroll" class="chat-messages-scroll">
                ${
                  threadMessages.length === 0
                    ? `<div class="empty-state" style="margin:auto;border:none;background:transparent;">
                        <span class="empty-icon">👋</span>
                        <strong>Say hello to ${escapeHtml(partner.firstName || "your match")}!</strong>
                        <p>Your messages appear instantly on both their Android App and Web browser.</p>
                      </div>`
                    : threadMessages
                        .map((msg) => {
                          const isMine =
                            msg.senderId === uid ||
                            (store.isAdmin && msg.senderId === ADMIN_SUPPORT_USER_ID);
                          const photoSrc = msg.photoBase64 ? getPhotoSrc(msg.photoBase64) : null;
                          return `
                            <div class="msg-row ${isMine ? "mine" : "theirs"}">
                              <div class="msg-bubble">
                                ${
                                  photoSrc
                                    ? `<img class="msg-photo" src="${photoSrc}" alt="Shared photo" data-zoom-photo="${escapeHtml(msg.id)}" />`
                                    : ""
                                }
                                <div>${escapeHtml(msg.text)}</div>
                              </div>
                              <span class="msg-time">${escapeHtml(formatMessageDateTime(msg.createdAt))}</span>
                            </div>
                          `;
                        })
                        .join("")
                }
              </div>

              <form id="chat-send-form" class="chat-composer">
                <label class="icon-btn" title="Send Photo" style="cursor:pointer;">
                  📷
                  <input id="chat-photo-input" type="file" accept="image/*" hidden />
                </label>
                <input id="chat-text-input" class="input" type="text" maxlength="1000" placeholder="Type a message..." autocomplete="off" />
                <button type="submit" class="btn btn-primary">Send</button>
              </form>
            `
        }
      </section>
    </div>
  `;

  container.querySelector("#chat-admin-support-btn")?.addEventListener("click", () => {
    const adminProfile = buildAdminSupportProfile();
    store.activeChatPartnerId = adminProfile.userId;
    store.activeChatPartnerSnapshot = adminProfile;
    notify();
  });

  container.querySelectorAll("[data-open-convo]").forEach((btn) => {
    btn.addEventListener("click", () => {
      const targetId = btn.getAttribute("data-open-convo");
      const convo = convos.find((c) => c.otherUserId === targetId);
      store.activeChatPartnerId = targetId;
      store.activeChatPartnerSnapshot = convo?.otherUserProfile || {
        userId: targetId,
        firstName: (convo?.otherUserName || "Milan").split(" ")[0],
        lastName: (convo?.otherUserName || "").split(" ").slice(1).join(" "),
        photoBase64: "avatar_rose"
      };
      notify();
    });
  });

  if (partner) {
    const scrollBox = container.querySelector("#chat-messages-scroll");
    if (scrollBox) scrollBox.scrollTop = scrollBox.scrollHeight;

    container.querySelector("#chat-mobile-back")?.addEventListener("click", () => {
      store.activeChatPartnerId = null;
      store.activeChatPartnerSnapshot = null;
      notify();
    });

    container.querySelector("#chat-go-gold")?.addEventListener("click", () => {
      store.activeTab = "profile";
      notify();
    });

    container.querySelector("#chat-delete-thread")?.addEventListener("click", () => {
      if (confirm(`Delete your chat history with ${fullNameOf(partner)}?`)) {
        actions.deleteChatThread(partner.userId);
      }
    });

    container.querySelector("#chat-block-user")?.addEventListener("click", () => {
      if (confirm(`Block ${fullNameOf(partner)}?`)) {
        actions.blockUser(partner.userId, fullNameOf(partner));
      }
    });

    container.querySelectorAll("[data-zoom-photo]").forEach((img) => {
      img.addEventListener("click", () => {
        const msgId = img.getAttribute("data-zoom-photo");
        const found = threadMessages.find((m) => m.id === msgId);
        if (found?.photoBase64) showPhotoLightbox(found.photoBase64, found.text || "Shared Photo");
      });
    });

    const form = container.querySelector("#chat-send-form");
    const input = container.querySelector("#chat-text-input");
    form?.addEventListener("submit", async (e) => {
      e.preventDefault();
      const text = input.value.trim();
      if (!text) return;
      input.value = "";
      await actions.sendDirectMessage(partner, text);
    });

    const photoInput = container.querySelector("#chat-photo-input");
    photoInput?.addEventListener("change", async (e) => {
      const file = e.target.files?.[0];
      if (!file) return;
      try {
        const rawBase64 = await compressImageToRawBase64(file, 480, 0.68, 600000);
        await actions.sendPhotoMessage(partner, rawBase64, "");
      } catch (err) {
        pushToast("Photo Error", err.message, "error");
      }
    });
  }
}
