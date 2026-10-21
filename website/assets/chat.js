(() => {
  const $ = (selector) => document.querySelector(selector);
  const state = {
    user: null,
    friends: [],
    requests: [],
    conversations: [],
    blocked: [],
    peer: null,
    messages: [],
    messageRequestBusy: false,
    pollTimer: null,
    reportLoading: false
  };

  function el(tag, className, text) {
    const item = document.createElement(tag);
    if (className) item.className = className;
    if (text !== undefined && text !== null) item.textContent = String(text);
    return item;
  }

  function button(label, className, action, disabled = false) {
    const item = el("button", className || "btn", label);
    item.type = "button";
    item.disabled = Boolean(disabled);
    item.addEventListener("click", action);
    return item;
  }

  function feedback(target, message, kind = "info") {
    if (!target) return;
    target.textContent = message || "";
    target.className = "form-feedback" + (message ? " is-" + kind : "");
  }

  function setStatus(message, kind = "info") {
    feedback($("#chat-status"), message, kind);
  }

  async function api(path, options = {}) {
    if (!path.startsWith("/api/chat/") || path.startsWith("//") || path.includes("..")) {
      throw new Error("Invalid Chat request.");
    }
    const headers = new Headers({ Accept: "application/json" });
    const init = {
      method: options.method || "GET",
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error",
      headers
    };
    if (options.body !== undefined) {
      headers.set("Content-Type", "application/json");
      init.body = JSON.stringify(options.body);
    }
    let response;
    try {
      response = await fetch(path, init);
    } catch {
      throw new Error("Could not reach VoidCraft Chat. Check the connection and try again.");
    }
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(typeof payload.error === "string" ? payload.error : "Chat request failed (HTTP " + response.status + ").");
      error.status = response.status;
      throw error;
    }
    return payload;
  }

  function friendlyDate(value) {
    const date = new Date(value);
    if (!Number.isFinite(date.getTime())) return "";
    return date.toLocaleString([], { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });
  }

  function idPath(value) {
    return encodeURIComponent(String(value));
  }

  function showSignedOut(message) {
    $("#chat-login").hidden = false;
    $("#chat-app").hidden = true;
    if (message) setStatus(message, "info");
  }

  async function loadFriendsAndRequests() {
    const [friendData, requestData, conversationData, blockedData] = await Promise.all([
      api("/api/chat/friends"),
      api("/api/chat/requests"),
      api("/api/chat/conversations"),
      api("/api/chat/blocks")
    ]);
    state.friends = Array.isArray(friendData.friends) ? friendData.friends : [];
    state.requests = Array.isArray(requestData.requests) ? requestData.requests : [];
    state.conversations = Array.isArray(conversationData.conversations) ? conversationData.conversations : [];
    state.blocked = Array.isArray(blockedData.blocks) ? blockedData.blocks : [];
    renderFriends();
    renderRequests();
    renderConversations();
    renderBlocked();
  }

  function renderPlayers(players) {
    const root = $("#player-results");
    root.replaceChildren();
    if (!players.length) {
      root.append(el("div", "chat-empty-small", "No matching players found."));
      return;
    }
    for (const player of players) {
      const row = el("article", "chat-list-item");
      const copy = el("div", "chat-list-copy");
      copy.append(el("strong", "chat-list-name", player.username));
      copy.append(el("span", "chat-list-caption", player.role === "owner" ? "VoidCraft owner" : "VoidCraft member"));
      row.append(copy, button("Add friend", "btn chat-small-button", async () => {
        try {
          await api("/api/chat/requests", { method: "POST", body: { username: player.username } });
          feedback($("#chat-status"), "Friend request sent to " + player.username + ".", "success");
          await loadFriendsAndRequests();
        } catch (error) {
          setStatus(error.message, "error");
        }
      }));
      root.append(row);
    }
  }

  async function searchPlayers(query) {
    const clean = String(query || "").trim();
    if (!/^[A-Za-z0-9_]{3,20}$/.test(clean)) {
      setStatus("Enter at least 3 letters, numbers, or underscores to search.", "error");
      return;
    }
    $("#player-results").replaceChildren(el("div", "chat-empty-small", "Searching players…"));
    try {
      const result = await api("/api/chat/players?q=" + encodeURIComponent(clean));
      renderPlayers(Array.isArray(result.players) ? result.players : []);
    } catch (error) {
      $("#player-results").replaceChildren(el("div", "chat-empty-small", error.message));
    }
  }

  function renderFriends() {
    const root = $("#friend-list");
    root.replaceChildren();
    if (!state.friends.length) {
      root.append(el("div", "chat-empty-small", "No friends yet. Search for a player to begin."));
      return;
    }
    for (const friend of state.friends) {
      const row = el("article", "chat-list-item");
      const copy = el("div", "chat-list-copy");
      copy.append(el("strong", "chat-list-name", friend.username));
      copy.append(el("span", "chat-list-caption", "Accepted friend"));
      row.append(copy);
      row.append(button("Chat", "btn chat-small-button", () => openConversation(friend)));
      row.append(button("×", "text-button chat-remove-friend", async () => {
        if (!window.confirm("Remove " + friend.username + " from your friends?")) return;
        try {
          await api("/api/chat/friends/" + idPath(friend.id), { method: "DELETE" });
          if (state.peer && state.peer.id === friend.id) clearConversation();
          await loadFriendsAndRequests();
          setStatus("Friend removed.", "success");
        } catch (error) {
          setStatus(error.message, "error");
        }
      }, false));
      root.append(row);
    }
  }

  function renderRequests() {
    const root = $("#request-list");
    root.replaceChildren();
    if (!state.requests.length) {
      root.append(el("div", "chat-empty-small", "No pending requests."));
      return;
    }
    for (const request of state.requests) {
      const row = el("article", "chat-list-item request-item");
      const copy = el("div", "chat-list-copy");
      copy.append(el("strong", "chat-list-name", request.username));
      copy.append(el("span", "chat-list-caption", request.direction === "incoming" ? "Wants to be friends" : "Request sent"));
      row.append(copy);
      if (request.direction === "incoming") {
        row.append(button("Accept", "btn chat-small-button", async () => {
          try {
            await api("/api/chat/requests/" + idPath(request.id) + "/accept", { method: "POST", body: {} });
            await loadFriendsAndRequests();
            setStatus("You and " + request.username + " are now friends.", "success");
          } catch (error) {
            setStatus(error.message, "error");
          }
        }));
      }
      row.append(button(request.direction === "incoming" ? "Decline" : "Cancel", "text-button", async () => {
        try {
          await api("/api/chat/requests/" + idPath(request.id), { method: "DELETE" });
          await loadFriendsAndRequests();
          setStatus("Friend request removed.", "success");
        } catch (error) {
          setStatus(error.message, "error");
        }
      }));
      root.append(row);
    }
  }

  function renderConversations() {
    const root = $("#conversation-list");
    root.replaceChildren();
    if (!state.conversations.length) {
      root.append(el("div", "chat-empty-small", "Send a message to a friend to start a conversation."));
      return;
    }
    for (const conversation of state.conversations) {
      const row = button("", "chat-conversation-item" + (state.peer && state.peer.id === conversation.id ? " is-selected" : ""), () => {
        const friend = state.friends.find((item) => item.id === conversation.id) ||
          { id: conversation.id, username: conversation.username };
        openConversation(friend);
      });
      row.append(el("strong", "chat-list-name", conversation.username));
      row.append(el("span", "chat-list-caption chat-conversation-preview", conversation.lastMessage));
      row.append(el("time", "chat-list-time", friendlyDate(conversation.createdAt)));
      root.append(row);
    }
  }

  function renderBlocked() {
    const root = $("#blocked-list");
    root.replaceChildren();
    if (!state.blocked.length) {
      root.append(el("div", "chat-empty-small", "No blocked players."));
      return;
    }
    for (const player of state.blocked) {
      const row = el("article", "chat-list-item");
      row.append(el("strong", "chat-list-name", player.username));
      row.append(button("Unblock", "text-button", async () => {
        try {
          await api("/api/chat/blocks/" + idPath(player.id), { method: "DELETE" });
          await loadFriendsAndRequests();
          setStatus(player.username + " was unblocked.", "success");
        } catch (error) {
          setStatus(error.message, "error");
        }
      }));
      root.append(row);
    }
  }

  function clearConversation() {
    state.peer = null;
    state.messages = [];
    $("#chat-thread-empty").hidden = false;
    $("#chat-thread-active").hidden = true;
    renderConversations();
  }

  async function openConversation(friend) {
    state.peer = { id: String(friend.id), username: String(friend.username) };
    $("#chat-thread-empty").hidden = true;
    $("#chat-thread-active").hidden = false;
    $("#thread-title").textContent = state.peer.username;
    $("#thread-subtitle").textContent = "Private chat · Accepted friends only";
    $("#message-input").disabled = false;
    $("#message-submit").disabled = false;
    renderConversations();
    await loadMessages();
  }

  async function loadMessages() {
    if (!state.peer || state.messageRequestBusy) return;
    state.messageRequestBusy = true;
    const peerAtRequest = state.peer.id;
    try {
      const result = await api("/api/chat/conversations/" + idPath(peerAtRequest) + "/messages?limit=40");
      if (!state.peer || state.peer.id !== peerAtRequest) return;
      state.messages = Array.isArray(result.messages) ? result.messages : [];
      renderMessages();
      feedback($("#message-feedback"), "");
    } catch (error) {
      feedback($("#message-feedback"), error.message, "error");
    } finally {
      state.messageRequestBusy = false;
    }
  }

  function renderMessages() {
    const root = $("#message-list");
    const stickToBottom = root.scrollHeight - root.scrollTop - root.clientHeight < 80;
    root.replaceChildren();
    if (!state.messages.length) {
      root.append(el("div", "chat-message-empty", "No messages yet. Say hello to start the conversation."));
      return;
    }
    for (const message of state.messages) {
      const mine = String(message.senderId) === String(state.user.id);
      const row = el("article", "chat-message" + (mine ? " is-mine" : ""));
      const meta = el("div", "chat-message-meta");
      meta.append(el("strong", "", mine ? "You" : message.senderUsername));
      const time = el("time", "", friendlyDate(message.createdAt));
      time.dateTime = message.createdAt;
      meta.append(time);
      row.append(meta, el("p", "chat-message-body", message.body));
      root.append(row);
    }
    if (stickToBottom) root.scrollTop = root.scrollHeight;
  }

  async function sendMessage(event) {
    event.preventDefault();
    if (!state.peer) return;
    const input = $("#message-input");
    const content = input.value.trim();
    if (!content) return;
    const submit = $("#message-submit");
    submit.disabled = true;
    feedback($("#message-feedback"), "");
    const peerId = state.peer.id;
    try {
      await api("/api/chat/conversations/" + idPath(peerId) + "/messages", {
        method: "POST",
        body: { content }
      });
      input.value = "";
      await loadMessages();
      await loadFriendsAndRequests();
      feedback($("#message-feedback"), "Message sent.", "success");
    } catch (error) {
      feedback($("#message-feedback"), error.message, "error");
    } finally {
      submit.disabled = !state.peer || state.peer.id !== peerId;
    }
  }

  async function blockCurrentPlayer() {
    if (!state.peer || !window.confirm("Block " + state.peer.username + "? This removes the friendship and prevents messaging in either direction.")) return;
    const peer = state.peer;
    try {
      await api("/api/chat/blocks/" + idPath(peer.id), { method: "POST", body: {} });
      clearConversation();
      await loadFriendsAndRequests();
      setStatus(peer.username + " has been blocked.", "success");
    } catch (error) {
      setStatus(error.message, "error");
    }
  }

  async function reportCurrentPlayer() {
    if (!state.peer) return;
    const peer = state.peer;
    const category = String(window.prompt(
      "Report reason: harassment, spam, impersonation, inappropriate-content, or other",
      "other"
    ) || "").trim().toLowerCase();
    if (!["harassment", "spam", "impersonation", "inappropriate-content", "other"].includes(category)) {
      setStatus("Choose one of the listed report reasons.", "error");
      return;
    }
    const details = String(window.prompt("Optional details (do not include passwords or private account information)", "") || "").slice(0, 1000);
    try {
      await api("/api/chat/reports", { method: "POST", body: { targetUserId: peer.id, category, details } });
      setStatus("Report submitted to the VoidCraft owner queue.", "success");
    } catch (error) {
      setStatus(error.message, "error");
    }
  }

  async function loadModerationQueue() {
    if (!state.user || state.user.role !== "owner") return;
    const status = $("#moderation-filter").value;
    $("#moderation-reports").replaceChildren(el("div", "chat-empty-small", "Loading report queue…"));
    try {
      const result = await api("/api/chat/moderation/reports?status=" + encodeURIComponent(status));
      renderModerationReports(Array.isArray(result.reports) ? result.reports : []);
      feedback($("#moderation-feedback"), "");
    } catch (error) {
      $("#moderation-reports").replaceChildren(el("div", "chat-empty-small", error.message));
    }
  }

  function renderModerationReports(reports) {
    const root = $("#moderation-reports");
    root.replaceChildren();
    if (!reports.length) {
      root.append(el("div", "chat-empty-small", "No reports in this queue."));
      return;
    }
    for (const report of reports) {
      const card = el("article", "chat-report-item");
      const heading = el("div", "chat-report-heading");
      heading.append(el("strong", "", "#" + report.id + " · " + report.category));
      heading.append(el("time", "", friendlyDate(report.createdAt)));
      card.append(heading, el("p", "", "From @" + report.reporterUsername + " about @" + report.targetUsername));
      if (report.details) card.append(el("p", "chat-report-details", report.details));
      const controls = el("div", "chat-input-row");
      const select = el("select", "chat-report-status");
      select.setAttribute("aria-label", "Report status for report " + report.id);
      for (const value of ["open", "reviewing", "resolved", "dismissed"]) {
        const option = el("option", "", value);
        option.value = value;
        option.selected = value === report.status;
        select.append(option);
      }
      controls.append(select, button("Save status", "btn chat-small-button", async () => {
        try {
          await api("/api/chat/moderation/reports/" + idPath(report.id), {
            method: "PATCH",
            body: { status: select.value }
          });
          feedback($("#moderation-feedback"), "Report #" + report.id + " updated.", "success");
          await loadModerationQueue();
        } catch (error) {
          feedback($("#moderation-feedback"), error.message, "error");
        }
      }));
      card.append(controls);
      root.append(card);
    }
  }

  async function refreshAll() {
    try {
      await loadFriendsAndRequests();
      if (state.peer) await loadMessages();
      if (state.user && state.user.role === "owner") await loadModerationQueue();
      setStatus("");
    } catch (error) {
      setStatus(error.message, "error");
    }
  }

  $("#player-search-form").addEventListener("submit", (event) => {
    event.preventDefault();
    searchPlayers($("#player-query").value);
  });
  $("#message-form").addEventListener("submit", sendMessage);
  $("#thread-block").addEventListener("click", blockCurrentPlayer);
  $("#thread-report").addEventListener("click", reportCurrentPlayer);
  $("#chat-refresh").addEventListener("click", refreshAll);
  $("#moderation-refresh").addEventListener("click", loadModerationQueue);
  $("#moderation-filter").addEventListener("change", loadModerationQueue);

  async function start() {
    try {
      const result = await api("/api/chat/me");
      state.user = result.user;
      $("#chat-account-name").textContent = (state.user.role === "owner" ? "♛ " : "") + state.user.username;
      $("#chat-login").hidden = true;
      $("#chat-app").hidden = false;
      $("#moderation-panel").hidden = state.user.role !== "owner";
      await refreshAll();
      state.pollTimer = window.setInterval(async () => {
        if (document.hidden) return;
        await refreshAll();
      }, 5000);
      window.addEventListener("pagehide", () => window.clearInterval(state.pollTimer), { once: true });
    } catch (error) {
      if (error.status === 401) {
        showSignedOut("Sign in with your existing VoidCraft website account to access private Chat.");
      } else {
        showSignedOut(error.message);
      }
    }
  }

  start();
})();
