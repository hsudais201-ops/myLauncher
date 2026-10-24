(() => {
  "use strict";
  const STORAGE_KEY = "voidcraft.server-state.saved.v1";
  const API_BASE = "https://api.mcsrvstat.us/3/";
  const form = document.querySelector("#server-form");
  const addressInput = document.querySelector("#server-address");
  const list = document.querySelector("#server-list");
  const notice = document.querySelector("#server-notice");
  const refreshAll = document.querySelector("#refresh-all");
  if (!form || !list) return;

  let servers = loadServers();
  const busy = new Set();

  function loadServers() {
    try {
      const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || "[]");
      if (!Array.isArray(parsed)) return [];
      const saved = parsed.filter(x => typeof x === "string").slice(0, 30);
      // Automatically remove this known failing address from saved browser lists.
      const cleaned = saved.filter(address => !/^eventmc\.xyz(?::|$)/i.test(address.trim()));
      if (cleaned.length !== saved.length) {
        try { localStorage.setItem(STORAGE_KEY, JSON.stringify(cleaned)); } catch (_) {}
      }
      return cleaned;
    } catch { return []; }
  }
  function saveServers() {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(servers)); }
    catch { setNotice("This browser could not save the server list. Check its site storage settings."); }
  }
  function setNotice(message) { if (notice) notice.textContent = message; }
  function validAddress(raw) {
    const value = raw.trim();
    if (!value || value.length > 253 || /[\s/\\?#@]/.test(value)) return false;
    const host = value.replace(/^\[|\]$/g, "").split(":")[0];
    return host.length > 0 && /^[a-zA-Z0-9.\-:[\]]+$/.test(value);
  }
  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function render() {
    list.replaceChildren();
    if (!servers.length) {
      list.append(el("article", "card server-empty", "No servers saved yet. Add a Java or Bedrock server address above to check its live state."));
      return;
    }
    servers.forEach(address => {
      const card = el("article", "card server-status-card");
      const head = el("div", "server-status-head");
      const title = el("h3", "", address);
      const state = el("span", "server-state-badge", busy.has(address) ? "Checking…" : "Not checked");
      state.dataset.state = "unknown";
      head.append(title, state);
      const detail = el("p", "server-detail", "Tap Check to fetch live status, player count, version and MOTD.");
      detail.dataset.detail = address;
      const actions = el("div", "actions");
      const check = el("button", "btn secondary", busy.has(address) ? "Checking…" : "Check status");
      check.type = "button";
      check.disabled = busy.has(address);
      check.addEventListener("click", () => checkServer(address));
      const remove = el("button", "btn secondary", "Remove");
      remove.type = "button";
      remove.addEventListener("click", () => {
        servers = servers.filter(x => x !== address);
        saveServers(); render(); setNotice("Server removed from this browser.");
      });
      actions.append(check, remove);
      card.append(head, detail, actions);
      list.append(card);
    });
  }
  async function checkServer(address) {
    if (busy.has(address)) return;
    busy.add(address); render(); setNotice("Checking " + address + "…");
    try {
      const response = await fetch(API_BASE + encodeURIComponent(address), {
        method: "GET", cache: "no-store", headers: { "Accept": "application/json" }
      });
      if (!response.ok) throw new Error("Status service returned HTTP " + response.status);
      const data = await response.json();
      const cards = [...list.querySelectorAll(".server-status-card")];
      const card = cards.find(node => node.querySelector("h3")?.textContent === address);
      if (!card) return;
      const badge = card.querySelector(".server-state-badge");
      const detail = card.querySelector(".server-detail");
      const online = data.online === true;
      badge.textContent = online ? "ONLINE" : "OFFLINE";
      badge.dataset.state = online ? "online" : "offline";
      if (online) {
        const players = data.players ? data.players.online + " / " + data.players.max : "Player count unavailable";
        const version = data.version || "Version unavailable";
        let motd = "MOTD unavailable";
        if (data.motd && Array.isArray(data.motd.clean)) motd = data.motd.clean.join(" ").trim() || motd;
        detail.textContent = players + " players · " + version + " · " + motd;
      } else {
        detail.textContent = data.ip ? "Server answered as offline. Try again later or check the address and port." : "No live response for this address. It may be offline, filtered, or mistyped.";
      }
      setNotice("Last checked " + new Date().toLocaleTimeString() + ". Status is supplied by mcsrvstat.us, not guaranteed by VoidCraft.");
    } catch (error) {
      const card = [...list.querySelectorAll(".server-status-card")].find(node => node.querySelector("h3")?.textContent === address);
      if (card) {
        const badge = card.querySelector(".server-state-badge");
        badge.textContent = "CHECK FAILED"; badge.dataset.state = "error";
        card.querySelector(".server-detail").textContent = error.message || "Could not reach the status service.";
      }
      setNotice("Could not check " + address + ". Check your connection and retry.");
    } finally {
      busy.delete(address);
      const card = [...list.querySelectorAll(".server-status-card")].find(node => node.querySelector("h3")?.textContent === address);
      if (card) {
        const button = card.querySelector("button");
        if (button) { button.disabled = false; button.textContent = "Check status"; }
      }
    }
  }
  form.addEventListener("submit", event => {
    event.preventDefault();
    const address = addressInput.value.trim();
    if (!validAddress(address)) { setNotice("Enter a valid server host or host:port, such as play.example.net or play.example.net:25565."); addressInput.focus(); return; }
    if (servers.some(x => x.toLowerCase() === address.toLowerCase())) { setNotice("That server is already saved."); return; }
    if (servers.length >= 30) { setNotice("You can save up to 30 servers in this browser."); return; }
    servers.push(address); saveServers(); render(); addressInput.value = "";
    setNotice("Server saved in this browser. Checking live status…"); checkServer(address);
  });
  refreshAll?.addEventListener("click", () => servers.slice().forEach(checkServer));
  render();
})();