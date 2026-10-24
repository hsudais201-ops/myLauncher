(() => {
  "use strict";

  const STORAGE_KEY = "voidcraft.server-state.saved.v2";
  const LEGACY_STORAGE_KEY = "voidcraft.server-state.saved.v1";
  const STATUS_API_PATH = "/api/server-status";
  const form = document.querySelector("#server-form");
  const addressInput = document.querySelector("#server-address");
  const editionInput = document.querySelector("#server-edition");
  const list = document.querySelector("#server-list");
  const notice = document.querySelector("#server-notice");
  const refreshAll = document.querySelector("#refresh-all");

  if (!form || !list) return;

  const busy = new Set();
  const results = new Map();
  let servers = loadServers();

  function keyFor(server) {
    return server.edition + ":" + server.address.toLowerCase();
  }

  function normalizeEdition(value) {
    return value === "bedrock" ? "bedrock" : "java";
  }

  function normalizeServer(value) {
    if (typeof value === "string") {
      const address = value.trim();
      return address ? { address, edition: "java" } : null;
    }
    if (!value || typeof value !== "object" || typeof value.address !== "string") return null;
    const address = value.address.trim();
    if (!address || !validAddress(address)) return null;
    return { address, edition: normalizeEdition(value.edition) };
  }

  function loadServers() {
    try {
      let raw = localStorage.getItem(STORAGE_KEY);
      let migrating = false;
      if (!raw) {
        raw = localStorage.getItem(LEGACY_STORAGE_KEY) || "[]";
        migrating = true;
      }
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) return [];
      const normalized = parsed.map(normalizeServer).filter(Boolean);
      const unique = [];
      const seen = new Set();
      for (const server of normalized) {
        const key = keyFor(server);
        if (!seen.has(key) && unique.length < 30) {
          unique.push(server);
          seen.add(key);
        }
      }
      if (migrating) {
        try { localStorage.setItem(STORAGE_KEY, JSON.stringify(unique)); } catch (_) {}
      }
      return unique;
    } catch (_) {
      return [];
    }
  }

  function saveServers() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(servers));
    } catch (_) {
      setNotice("This browser could not save the server list. Check its site storage settings.");
    }
  }

  function setNotice(message) {
    if (notice) notice.textContent = message;
  }

  function validAddress(raw) {
    const value = String(raw || "").trim();
    if (!value || value.length > 253 || /[\s/\\?#@]/.test(value)) return false;
    if (!/^[a-zA-Z0-9.\-:[\]]+$/.test(value)) return false;
    const host = value.replace(/^\[|\]$/g, "").split(":")[0];
    return host.length > 0;
  }

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function setServerIcon(image, server, data) {
    image.alt = server.address + " server icon";
    image.loading = "lazy";
    image.width = 64;
    image.height = 64;
    image.decoding = "async";
    image.onerror = () => {
      image.onerror = null;
      image.src = "assets/logo.svg";
      image.alt = "VoidCraft server placeholder icon";
    };
    if (data && typeof data.icon === "string" && /^data:image\/png;base64,[a-zA-Z0-9+/=]+$/.test(data.icon)) {
      image.src = data.icon;
    } else {
      // Load the icon independently from the status response. This lets the
      // card still show a server icon when the status lookup is temporarily
      // unavailable or the provider omits an inline icon.
      image.src = STATUS_API_PATH + "?resource=icon&address=" + encodeURIComponent(server.address) + "&edition=" + encodeURIComponent(server.edition);
      image.alt = server.address + " server icon";
    }
  }

  function statusFor(server) {
    return results.get(keyFor(server)) || { state: "unknown", data: null, checkedAt: null, message: "" };
  }

  function renderCard(server) {
    const key = keyFor(server);
    const busyNow = busy.has(key);
    const result = statusFor(server);
    const card = el("article", "card server-status-card");
    card.dataset.serverKey = key;

    const head = el("div", "server-status-head");
    const identity = el("div", "server-identity");
    const iconWrap = el("div", "server-icon-wrap");
    const icon = el("img", "server-icon");
    setServerIcon(icon, server, result.data);
    iconWrap.append(icon);

    const nameBlock = el("div", "server-name-block");
    nameBlock.append(
      el("h3", "server-address-title", server.address),
      el("span", "server-edition-label", server.edition === "bedrock" ? "Minecraft Bedrock" : "Minecraft Java")
    );
    identity.append(iconWrap, nameBlock);

    const badge = el("span", "server-state-badge", busyNow ? "CHECKING…" :
      result.state === "online" ? "ONLINE" :
      result.state === "offline" ? "OFFLINE" :
      result.state === "error" ? "CHECK FAILED" : "NOT CHECKED");
    badge.dataset.state = busyNow ? "checking" : result.state;
    head.append(identity, badge);

    const details = el("div", "server-detail");
    if (busyNow && !result.data) {
      details.append(el("p", "server-hint", "Contacting the Minecraft status service…"));
    } else if (result.state === "unknown") {
      details.append(el("p", "server-hint", "Check this server to load its icon, live state, player count, version and MOTD."));
    } else if (result.state === "error") {
      details.append(el("p", "server-error-text", result.message || "The status service could not check this server."));
      // Keep the card's information layout stable after a failed status query.
      // Users can still see where player count/version/MOTD data would appear.
      const metrics = el("div", "server-metrics");
      metrics.append(
        el("span", "server-metric", "Players: unavailable"),
        el("span", "server-metric", "Version: unavailable")
      );
      details.append(metrics);
      const motdBox = el("div", "server-motd");
      motdBox.append(
        el("div", "server-section-label", "MESSAGE OF THE DAY"),
        el("p", "server-hint", "MOTD unavailable because this status check failed.")
      );
      details.append(motdBox);
      if (result.checkedAt) {
        details.append(el("p", "server-checked-at", "Last checked " + new Date(result.checkedAt).toLocaleTimeString()));
      }
    } else {
      const data = result.data || {};
      const online = result.state === "online";
      const metrics = el("div", "server-metrics");
      const players = data.players && typeof data.players === "object" ? data.players : null;
      if (online && players && Number.isFinite(Number(players.online))) {
        const countText = players.max !== undefined && Number.isFinite(Number(players.max))
          ? String(Number(players.online)) + " / " + String(Number(players.max)) + " players"
          : String(Number(players.online)) + " players online";
        metrics.append(el("span", "server-metric server-player-count", countText));
      } else {
        metrics.append(el("span", "server-metric", online ? "Player count unavailable" : "Players unavailable while offline"));
      }

      const version = data.version || (data.protocol && data.protocol.name) || "";
      metrics.append(el("span", "server-metric", "Version: " + (version || (online ? "unreported" : "unavailable"))));
      if (data.port !== undefined && data.port !== null && data.port !== "") {
        metrics.append(el("span", "server-metric", "Port: " + String(data.port)));
      }
      if (data.ip) metrics.append(el("span", "server-metric", "IP: " + String(data.ip)));
      details.append(metrics);

      if (online && players && Number.isFinite(Number(players.online)) && Number.isFinite(Number(players.max)) && Number(players.max) > 0) {
        const count = Math.max(0, Number(players.online));
        const maximum = Math.max(1, Number(players.max));
        const progress = el("div", "server-player-progress");
        progress.setAttribute("role", "progressbar");
        progress.setAttribute("aria-label", "Online players");
        progress.setAttribute("aria-valuemin", "0");
        progress.setAttribute("aria-valuemax", String(maximum));
        progress.setAttribute("aria-valuenow", String(Math.min(count, maximum)));
        const fill = el("span", "server-player-progress-fill");
        fill.style.width = Math.max(0, Math.min(100, (count / maximum) * 100)) + "%";
        progress.append(fill);
        details.append(progress);
      }

      const motdBox = el("div", "server-motd");
      motdBox.append(el("div", "server-section-label", "MESSAGE OF THE DAY"));
      const lines = data.motd && Array.isArray(data.motd.clean) ? data.motd.clean :
        data.motd && typeof data.motd.clean === "string" ? [data.motd.clean] : [];
      if (online && lines.length && lines.some(line => String(line).trim())) {
        const motdLines = el("div", "server-motd-lines");
        lines.forEach(line => motdLines.append(el("span", "", String(line))));
        motdBox.append(motdLines);
      } else {
        motdBox.append(el("p", "server-hint", online ? "This server did not publish an MOTD." : "MOTD is unavailable while the server is offline."));
      }
      details.append(motdBox);
      if (result.checkedAt) {
        details.append(el("p", "server-checked-at", "Last checked " + new Date(result.checkedAt).toLocaleTimeString()));
      }
    }

    const actions = el("div", "actions server-card-actions");
    const check = el("button", "btn secondary", busyNow ? "Checking…" : "Check status");
    check.type = "button";
    check.disabled = busyNow;
    check.addEventListener("click", () => checkServer(server));

    const remove = el("button", "btn secondary", "Remove");
    remove.type = "button";
    remove.addEventListener("click", () => {
      servers = servers.filter(item => keyFor(item) !== key);
      results.delete(key);
      saveServers();
      render();
      setNotice("Server removed from this browser.");
    });
    actions.append(check, remove);
    card.append(head, details, actions);
    return card;
  }

  function render() {
    list.replaceChildren();
    if (!servers.length) {
      list.append(el("article", "card server-empty", "No servers saved yet. Add a Java or Bedrock server address above to check its live state."));
      return;
    }
    servers.forEach(server => list.append(renderCard(server)));
  }

  async function checkServer(server) {
    const key = keyFor(server);
    if (busy.has(key)) return;
    busy.add(key);
    render();
    setNotice("Checking " + server.address + " (" + server.edition + ")…");

    try {
      const endpoint = STATUS_API_PATH + "?address=" + encodeURIComponent(server.address) + "&edition=" + encodeURIComponent(server.edition);
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 12000);
      let response;
      try {
        response = await fetch(endpoint, {
          method: "GET",
          cache: "no-store",
          credentials: "same-origin",
          headers: { "Accept": "application/json" },
          signal: controller.signal
        });
      } finally {
        clearTimeout(timeoutId);
      }
      let data = null;
      try { data = await response.json(); } catch (_) {}
      if (!response.ok) {
        if (data && typeof data.error === "string") throw new Error(data.error);
        if (response.status === 429) throw new Error("The status service rate-limited this check. Wait a little before retrying.");
        throw new Error("Status service returned HTTP " + response.status + ".");
      }
      if (!data || typeof data.online !== "boolean") {
        throw new Error("The status service returned an unexpected response. Please retry later.");
      }

      results.set(key, {
        state: data.online ? "online" : "offline",
        data,
        checkedAt: Date.now(),
        message: ""
      });
      render();
      setNotice("Checked " + server.address + ". Status comes from mcsrvstat.us and may be cached by the provider.");
    } catch (error) {
      const message = error && error.name === "AbortError"
        ? "The status check timed out after 12 seconds. The provider may be slow or unreachable; please retry."
        : (error && error.message ? error.message :
          (navigator.onLine === false ? "You appear to be offline. Reconnect and try again." : "Could not reach the status service."));
      results.set(key, { state: "error", data: null, checkedAt: Date.now(), message });
      render();
      setNotice("Could not check " + server.address + ". " + message);
    } finally {
      busy.delete(key);
      render();
    }
  }

  form.addEventListener("submit", event => {
    event.preventDefault();
    const address = addressInput.value.trim();
    const edition = normalizeEdition(editionInput ? editionInput.value : "java");
    if (!validAddress(address)) {
      setNotice("Enter a valid server host or host:port, such as play.example.net or play.example.net:25565.");
      addressInput.focus();
      return;
    }
    const server = { address, edition };
    const key = keyFor(server);
    if (servers.some(item => keyFor(item) === key)) {
      setNotice("That server is already saved for the selected edition.");
      return;
    }
    if (servers.length >= 30) {
      setNotice("You can save up to 30 servers in this browser.");
      return;
    }
    servers.push(server);
    saveServers();
    render();
    addressInput.value = "";
    setNotice("Server saved in this browser. Checking live status…");
    checkServer(server);
  });

  refreshAll && refreshAll.addEventListener("click", () => {
    servers.slice().forEach(server => checkServer(server));
  });

  render();
  if (servers.length) {
    setNotice("Saved servers are stored in this browser. Select the correct Java or Bedrock edition, then use Check status to refresh.");
  }
})();