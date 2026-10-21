/**
 * Browser client for the shared VoidCraft social API.
 *
 * Call from the website only after copying the Pages proxy to
 * functions/api/[[path]].js and configuring the Worker/D1 environment.
 * Browser sessions are HttpOnly cookies; this module deliberately never
 * reads or writes localStorage/sessionStorage for credentials.
 */
export class VoidCraftSocialApi {
  async register({ email, username, password }) {
    return this.request("/auth/register", {
      method: "POST",
      body: { email, username, password }
    });
  }

  async login({ identifier, password }) {
    return this.request("/auth/login", {
      method: "POST",
      body: { identifier, password }
    });
  }

  async logout() {
    return this.request("/auth/logout", { method: "POST", body: {} });
  }

  async me() {
    return this.request("/auth/me");
  }

  async updateProfile({ displayName, bio }) {
    return this.request("/profile", {
      method: "PATCH",
      body: { displayName, bio }
    });
  }

  async searchPlayers(query) {
    return this.request("/players?q=" + encodeURIComponent(String(query || "").trim()));
  }

  async friends() {
    return this.request("/friends");
  }

  async friendRequests() {
    return this.request("/friends/requests");
  }

  async sendFriendRequest(username) {
    return this.request("/friends/requests", {
      method: "POST",
      body: { username }
    });
  }

  async acceptFriendRequest(requestId) {
    return this.request("/friends/requests/" + encodeURIComponent(requestId) + "/accept", {
      method: "POST",
      body: {}
    });
  }

  async declineFriendRequest(requestId) {
    return this.request("/friends/requests/" + encodeURIComponent(requestId), {
      method: "DELETE"
    });
  }

  async removeFriend(userId) {
    return this.request("/friends/" + encodeURIComponent(userId), {
      method: "DELETE"
    });
  }

  async conversations() {
    return this.request("/conversations");
  }

  async messages(userId, { before, limit = 40 } = {}) {
    const query = new URLSearchParams({ limit: String(limit) });
    if (before != null) query.set("before", String(before));
    return this.request(
      "/conversations/" + encodeURIComponent(userId) + "/messages?" + query.toString()
    );
  }

  async sendMessage(userId, content) {
    return this.request("/conversations/" + encodeURIComponent(userId) + "/messages", {
      method: "POST",
      body: { content }
    });
  }

  async blockedPlayers() {
    return this.request("/blocks");
  }

  async blockPlayer(userId) {
    return this.request("/blocks/" + encodeURIComponent(userId), {
      method: "POST",
      body: {}
    });
  }

  async unblockPlayer(userId) {
    return this.request("/blocks/" + encodeURIComponent(userId), {
      method: "DELETE"
    });
  }

  async reportPlayer({ targetUserId, category, details = "" }) {
    return this.request("/reports", {
      method: "POST",
      body: { targetUserId, category, details }
    });
  }

  async moderationReports({ status, limit = 50 } = {}) {
    const query = new URLSearchParams({ limit: String(limit) });
    if (status) query.set("status", status);
    return this.request("/moderation/reports?" + query.toString());
  }

  async updateReportStatus(reportId, status) {
    return this.request("/moderation/reports/" + encodeURIComponent(reportId), {
      method: "PATCH",
      body: { status }
    });
  }

  async request(path, { method = "GET", body } = {}) {
    if (!path.startsWith("/") || path.startsWith("//") || path.includes("..")) {
      throw new Error("Invalid VoidCraft API path.");
    }
    const headers = new Headers({ Accept: "application/json" });
    const init = {
      method,
      headers,
      credentials: "same-origin",
      cache: "no-store",
      redirect: "error"
    };
    if (body !== undefined) {
      headers.set("Content-Type", "application/json");
      init.body = JSON.stringify(body);
    }
    let response;
    try {
      response = await fetch("/api" + path, init);
    } catch {
      throw new Error("Could not reach the VoidCraft account service.");
    }
    let payload = {};
    try {
      payload = await response.json();
    } catch {
      // A missing JSON body is represented by a controlled generic error below.
    }
    if (!response.ok) {
      const message = typeof payload.error === "string"
        ? payload.error
        : "VoidCraft account service returned HTTP " + response.status + ".";
      const error = new Error(message);
      error.status = response.status;
      throw error;
    }
    return payload;
  }
}
