(() => {
  "use strict";
  const DB_NAME = "voidcraft-advanced-mod-manager";
  const DB_VERSION = 1;
  const STORE = "mods";
  const MAX_BYTES = 100 * 1024 * 1024;
  const el = (id) => document.getElementById(id);
  const list = el("mod-list");
  const status = el("mod-status");
  let db;
  let mods = [];

  function setStatus(message, kind = "info") {
    status.textContent = message;
    status.className = "mod-status" + (kind === "error" ? " is-error" : kind === "success" ? " is-success" : "");
  }

  function openDb() {
    return new Promise((resolve, reject) => {
      if (!("indexedDB" in window)) return reject(new Error("This browser does not support IndexedDB."));
      const request = indexedDB.open(DB_NAME, DB_VERSION);
      request.onupgradeneeded = () => {
        const database = request.result;
        if (!database.objectStoreNames.contains(STORE)) {
          const objectStore = database.createObjectStore(STORE, { keyPath: "id" });
          objectStore.createIndex("addedAt", "addedAt");
        }
      };
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error || new Error("Could not open local storage."));
    });
  }

  function request(storeMode, operation) {
    return new Promise((resolve, reject) => {
      const tx = db.transaction(STORE, storeMode);
      const req = operation(tx.objectStore(STORE));
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error || new Error("Local library operation failed."));
      tx.onabort = () => reject(tx.error || new Error("Local library transaction failed."));
    });
  }

  async function load() {
    mods = (await request("readonly", (store) => store.getAll()))
      .sort((a, b) => String(a.name).localeCompare(String(b.name)));
    render();
  }

  function render() {
    list.replaceChildren();
    const enabled = mods.filter((mod) => mod.enabled);
    const disabled = mods.filter((mod) => !mod.enabled);
    el("total-count").textContent = String(mods.length);
    el("enabled-count").textContent = String(enabled.length);
    el("disabled-count").textContent = String(disabled.length);
    const query = el("mod-search").value.trim().toLowerCase();
    const filter = el("mod-filter").value;
    const shown = mods.filter((mod) =>
      mod.name.toLowerCase().includes(query) &&
      (filter === "all" || (filter === "enabled" ? mod.enabled : !mod.enabled))
    );
    el("empty-state").hidden = mods.length > 0;
    list.hidden = mods.length === 0;
    if (mods.length > 0 && shown.length === 0) {
      const empty = document.createElement("p");
      empty.className = "mod-no-results";
      empty.textContent = "No mods match this search or filter.";
      list.appendChild(empty);
      return;
    }
    shown.forEach((mod) => {
      const card = document.createElement("article");
      card.className = "mod-card";
      const icon = document.createElement("div");
      icon.className = "mod-file-icon";
      icon.textContent = "JAR";
      const details = document.createElement("div");
      details.className = "mod-file-details";
      const name = document.createElement("h3");
      name.textContent = mod.name;
      const meta = document.createElement("p");
      meta.textContent = formatBytes(mod.size) + " · Added " + new Date(mod.addedAt).toLocaleDateString();
      const state = document.createElement("span");
      state.className = "mod-state" + (mod.enabled ? " enabled" : "");
      state.textContent = mod.enabled ? "ENABLED IN LIBRARY" : "DISABLED";
      details.append(name, meta, state);
      const actions = document.createElement("div");
      actions.className = "mod-card-actions";
      const toggle = document.createElement("button");
      toggle.className = "btn secondary";
      toggle.type = "button";
      toggle.textContent = mod.enabled ? "Disable" : "Enable";
      toggle.addEventListener("click", () => toggleMod(mod));
      const exportButton = document.createElement("button");
      exportButton.className = "btn secondary";
      exportButton.type = "button";
      exportButton.textContent = "Export";
      exportButton.addEventListener("click", () => exportMod(mod));
      const remove = document.createElement("button");
      remove.className = "btn danger";
      remove.type = "button";
      remove.textContent = "Remove";
      remove.addEventListener("click", () => removeMod(mod));
      actions.append(toggle, exportButton, remove);
      card.append(icon, details, actions);
      list.appendChild(card);
    });
  }

  function formatBytes(bytes) {
    if (bytes < 1024) return bytes + " B";
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + " KB";
    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  }

  async function importFiles(files) {
    if (!files.length) return;
    setStatus("Checking selected files…");
    let added = 0;
    const problems = [];
    for (const file of files) {
      try {
        if (!file.name.toLowerCase().endsWith(".jar")) throw new Error(file.name + ": choose a .jar file.");
        if (file.size === 0) throw new Error(file.name + ": file is empty.");
        if (file.size > MAX_BYTES) throw new Error(file.name + ": exceeds the 100 MB safety limit.");
        const header = new Uint8Array(await file.slice(0, 4).arrayBuffer());
        const isZip = header.length >= 4 && header[0] === 0x50 && header[1] === 0x4b &&
          ((header[2] === 0x03 && header[3] === 0x04) ||
           (header[2] === 0x05 && header[3] === 0x06) ||
           (header[2] === 0x07 && header[3] === 0x08));
        if (!isZip) throw new Error(file.name + ": not a valid ZIP/JAR archive.");
        const id = (crypto.randomUUID ? crypto.randomUUID() : Date.now().toString(36) + Math.random().toString(36).slice(2));
        await request("readwrite", (store) => store.put({
          id, name: file.name, size: file.size, type: file.type || "application/java-archive",
          enabled: true, addedAt: Date.now(), blob: file
        }));
        added++;
      } catch (error) {
        problems.push(error.message || "Could not import a selected file.");
      }
    }
    await load();
    if (problems.length) setStatus("Imported " + added + " file(s). " + problems.join(" "), added ? "info" : "error");
    else setStatus("Imported " + added + " mod file(s) into this browser library.", "success");
    el("mod-files").value = "";
  }

  async function toggleMod(mod) {
    mod.enabled = !mod.enabled;
    try {
      await request("readwrite", (store) => store.put(mod));
      await load();
      setStatus(mod.name + (mod.enabled ? " enabled in this library." : " disabled in this library."), "success");
    } catch (error) {
      mod.enabled = !mod.enabled;
      setStatus(error.message || "Could not update this mod.", "error");
    }
  }

  function exportMod(mod) {
    if (!(mod.blob instanceof Blob)) return setStatus("The stored file is unavailable. Remove and import it again.", "error");
    const url = URL.createObjectURL(mod.blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = mod.name;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    setStatus("Export started for " + mod.name + ".", "success");
  }

  async function removeMod(mod) {
    if (!window.confirm("Remove " + mod.name + " from this browser's library? This cannot be undone.")) return;
    try {
      await request("readwrite", (store) => store.delete(mod.id));
      await load();
      setStatus("Removed " + mod.name + " from this browser library.", "success");
    } catch (error) {
      setStatus(error.message || "Could not remove this mod.", "error");
    }
  }

  el("choose-files").addEventListener("click", () => el("mod-files").click());
  el("empty-import").addEventListener("click", () => el("mod-files").click());
  el("mod-files").addEventListener("change", (event) => importFiles(Array.from(event.target.files || [])));
  el("refresh-library").addEventListener("click", () => load().then(() => setStatus("Local library refreshed.", "success")).catch((e) => setStatus(e.message, "error")));
  el("mod-search").addEventListener("input", render);
  el("mod-filter").addEventListener("change", render);

  openDb().then(async (opened) => {
    db = opened;
    await load();
    setStatus("Your local library is ready. Files stay in this browser.", "success");
  }).catch((error) => {
    setStatus((error.message || "Could not open local library.") + " Try a current browser with site storage enabled.", "error");
  });
})();
