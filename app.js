const dialog = document.querySelector("#app-dialog");
const dialogTitle = document.querySelector("#dialog-title");
const dialogMessage = document.querySelector("#dialog-message");
const dialogAction = document.querySelector("#dialog-action");
let actionAfterDialog = null;

function showDialog(title, message, action = "GOT IT", callback = null) {
  dialogTitle.textContent = title;
  dialogMessage.textContent = message;
  dialogAction.firstChild.textContent = action + " ";
  actionAfterDialog = callback;
  if (typeof dialog.showModal === "function") dialog.showModal();
  else window.alert(title + "\n\n" + message);
}
function closeDialog() {
  if (dialog.open) dialog.close();
}
document.querySelector("#dialog-close").addEventListener("click", closeDialog);
dialogAction.addEventListener("click", () => {
  closeDialog();
  if (actionAfterDialog) {
    const callback = actionAfterDialog;
    actionAfterDialog = null;
    callback();
  }
});
dialog.addEventListener("click", event => {
  if (event.target === dialog) closeDialog();
});

const versionSelect = document.querySelector("#version-select");
const memorySlider = document.querySelector("#memory-slider");
const memoryValue = document.querySelector("#memory-value");
const versionTitle = document.querySelector(".world-title-row h2");
const versionPill = document.querySelector(".version-pill");
const profileName = document.querySelector(".profile-name");
const deviceInfoStatus = document.querySelector("#device-info");
const runtimeInstallStatus = document.querySelector("#runtime-install-status");
let runtimeInstallRunning = false;
const memoryRangeMin = document.querySelector("#memory-range-min");
const memoryRangeMax = document.querySelector("#memory-range-max");
const storageKey = "hyper-launcher-mobile-settings-v1";

function readSettings() {
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) || "{}");
    if (saved.version && Array.from(versionSelect.options).some(option => option.value === saved.version)) {
      versionSelect.value = saved.version;
    }
    if (Number.isInteger(saved.memory) && saved.memory >= 1 && saved.memory <= 8) {
      memorySlider.value = String(saved.memory);
    }
  } catch (error) {
    // Storage can be unavailable in private or restricted browser contexts.
  }
}
function saveSettings() {
  try {
    localStorage.setItem(storageKey, JSON.stringify({
      version: versionSelect.value,
      memory: Number(memorySlider.value)
    }));
  } catch (error) {
    // Keep the controls usable even when storage is unavailable.
  }
}
function updateVersionSummary() {
  const selected = versionSelect.options[versionSelect.selectedIndex];
  const isSnapshot = versionSelect.value === "snapshot" ||
    (selected && selected.textContent.toLowerCase().includes("snapshot"));
  const isOfficialManifestOption = selected &&
    (selected.textContent.endsWith(" — Release") || selected.textContent.endsWith(" — Snapshot"));
  const summary = isSnapshot ? "Snapshot" :
    (isOfficialManifestOption ? "Java Release" : (selected.textContent.split(" — ")[1] || "Minecraft Java"));
  versionTitle.textContent = summary;
  versionPill.textContent = versionSelect.value === "snapshot" ? "SNAPSHOT" : versionSelect.value;
}
function updateMemorySummary() {
  memoryValue.textContent = memorySlider.value + " GB";
}
readSettings();
updateVersionSummary();
updateMemorySummary();

versionSelect.addEventListener("change", () => {
  updateVersionSummary();
  saveSettings();
  if (window.HyperLauncherNative && typeof window.HyperLauncherNative.updateSelectedInstanceVersion === "function") {
    window.HyperLauncherNative.updateSelectedInstanceVersion(versionSelect.value);
  }
});
memorySlider.addEventListener("input", () => {
  updateMemorySummary();
  saveSettings();
});


window.onNativeDeviceInfo = function (info) {
  if (!info || !info.ok) {
    deviceInfoStatus.textContent = info && info.error
      ? info.error
      : "Could not inspect this device.";
    return;
  }
  const min = Math.max(1, Number(info.minimumMemoryGb) || 1);
  const max = Math.max(min, Math.min(8, Number(info.maximumMemoryGb) || min));
  memorySlider.min = String(min);
  memorySlider.max = String(max);
  const current = Number(memorySlider.value) || Number(info.recommendedMemoryGb) || min;
  memorySlider.value = String(Math.min(max, Math.max(min, current)));
  memoryRangeMin.textContent = min + " GB";
  memoryRangeMax.textContent = max + " GB";
  updateMemorySummary();
  const gib = 1024 * 1024 * 1024;
  const totalRam = (Number(info.totalMemoryBytes) || 0) / gib;
  const availableRam = (Number(info.availableMemoryBytes) || 0) / gib;
  const freeStorage = (Number(info.availableStorageBytes) || 0) / gib;
  const model = String(info.deviceModel || "Android device");
  const arch = String(info.architecture || "Unknown CPU");
  const runtimeWarning = String(info.runtimeWarning || "");
  if (!runtimeInstallRunning) {
    runtimeInstallStatus.textContent = info.runtimeReady
      ? "Android Java " + String(info.runtimeJavaMajor || "runtime") + " is installed. Graphics and game launch remain separate."
      : runtimeWarning || "No verified Android Java runtime is installed yet.";
  }
  const runtimeText = info.runtimeReady
    ? " Android Java runtime detected (" + String(info.runtimeName || "unnamed") +
      ", Java " + String(info.runtimeJavaMajor || "unknown") +
      "). Graphics and game launch are not yet integrated."
    : " Minecraft runtime: " + (runtimeWarning || "not ready");
  deviceInfoStatus.textContent = model + " · " + arch + " · " +
    totalRam.toFixed(1) + " GB RAM (" + availableRam.toFixed(1) + " GB available) · " +
    freeStorage.toFixed(1) + " GB free launcher storage. Suggested target: " +
    Number(info.recommendedMemoryGb || min) + " GB. This target is saved only until game launching is connected." +
    runtimeText;
  saveSettings();
};

if (window.HyperLauncherNative && typeof window.HyperLauncherNative.refreshDeviceInfo === "function") {
  window.HyperLauncherNative.refreshDeviceInfo();
}

const runtimeInstallButton = document.querySelector("#install-runtime-button");
const runtimeInstallPanel = document.querySelector("#runtime-install-panel");
const runtimeInstallMessage = document.querySelector("#runtime-install-message");
const runtimeInstallProgress = document.querySelector("#runtime-install-progress-bar");
const cancelRuntimeInstallButton = document.querySelector("#cancel-runtime-install-button");
function setRuntimeInstallRunning(running) {
  runtimeInstallRunning = running;
  runtimeInstallButton.disabled = running;
  runtimeInstallButton.textContent = running
    ? "JAVA RUNTIME INSTALLING…"
    : "INSTALL JAVA FOR SELECTED VERSION ↓";
  cancelRuntimeInstallButton.hidden = !running;
}

runtimeInstallButton.addEventListener("click", () => {
  if (!window.HyperLauncherNative ||
      typeof window.HyperLauncherNative.installRuntimeForVersion !== "function") {
    showDialog(
      "Android app required",
      "Android Java runtime installation is available in the installed Android app, not the browser preview.",
      "GOT IT"
    );
    return;
  }
  runtimeInstallPanel.hidden = false;
  runtimeInstallMessage.textContent = "Checking official Java requirements for " + versionSelect.value + "…";
  runtimeInstallProgress.style.width = "0%";
  setRuntimeInstallRunning(true);
  window.HyperLauncherNative.installRuntimeForVersion(versionSelect.value);
});

cancelRuntimeInstallButton.addEventListener("click", () => {
  if (window.HyperLauncherNative &&
      typeof window.HyperLauncherNative.cancelInstallation === "function") {
    window.HyperLauncherNative.cancelInstallation();
    runtimeInstallMessage.textContent = "Cancelling Android Java runtime installation…";
    cancelRuntimeInstallButton.disabled = true;
  }
});

window.onNativeRuntimeInstall = function (event) {
  if (!event || typeof event.state !== "string") return;
  runtimeInstallPanel.hidden = false;
  if (event.state === "planning") {
    runtimeInstallMessage.textContent = "Reading Java requirements for " + event.version + "…";
    return;
  }
  if (event.state === "downloading") {
    const total = Number(event.totalBytes) || 0;
    const done = Number(event.downloadedBytes) || 0;
    const percent = total > 0 ? Math.min(100, Math.floor(done / total * 100)) : 0;
    runtimeInstallProgress.style.width = percent + "%";
    runtimeInstallMessage.textContent = "Downloading Java " +
      String(event.selectedJavaMajor || "") + " for " + String(event.abi || "Android") +
      " · " + percent + "%";
    return;
  }
  if (event.state === "extracting") {
    runtimeInstallMessage.textContent = "Verifying and safely extracting the Android Java runtime…";
    return;
  }
  if (event.state === "publishing") {
    runtimeInstallMessage.textContent = "Final checks passed. Activating the installed Java runtime…";
    return;
  }
  if (event.state === "complete") {
    runtimeInstallProgress.style.width = "100%";
    runtimeInstallMessage.textContent = "Java " + event.javaMajor + " installed for " +
      String(event.abi || "Android") + ". This does not yet enable Minecraft graphics or game launch.";
    runtimeInstallStatus.textContent = "Verified Android Java runtime installed: " + event.runtimeName;
    setRuntimeInstallRunning(false);
    cancelRuntimeInstallButton.disabled = false;
    if (window.HyperLauncherNative &&
        typeof window.HyperLauncherNative.refreshDeviceInfo === "function") {
      window.HyperLauncherNative.refreshDeviceInfo();
    }
    return;
  }
  if (event.state === "cancelled") {
    runtimeInstallMessage.textContent = "Java runtime installation cancelled. Incomplete files were removed.";
    setRuntimeInstallRunning(false);
    cancelRuntimeInstallButton.disabled = false;
    return;
  }
  if (event.state === "error") {
    runtimeInstallMessage.textContent = "Java runtime installation failed: " +
      (event.error || "Unknown error");
    setRuntimeInstallRunning(false);
    cancelRuntimeInstallButton.disabled = false;
  }
};


document.querySelector("#play-button").addEventListener("click", () => {
  const version = versionSelect.value === "snapshot" ? "Latest Snapshot" : versionSelect.value;
  showDialog(
    "Launcher engine not connected",
    "Your setup is saved on this device. Selected version: " + version + ". Memory target: " + memorySlider.value + " GB. The client JAR and indexed assets can be downloaded with the Download & Install button, but this build still cannot launch Minecraft. Version libraries, native files, Microsoft sign-in, a compatible Java runtime, and process launch integration are still missing.",
    "GOT IT"
  );
});

document.querySelector("#account-button").addEventListener("click", () => {
  showDialog(
    "Your player profile",
    "Offline Player is a local preview label only. It is not a Minecraft account and does not authenticate with Microsoft. Account sign-in and profile storage need to be implemented in the Android app.",
    "OK"
  );
});

function showFeature(feature) {
  if (feature === "Mods" || feature === "Mods & packs") { openModBrowser(); return; }
  if (feature === "Instances") { openInstancesBrowser(); return; }
  const messages = {
    "Instances": "Instance management is planned. The current preview does not install game versions or create separate game directories.",
    "Mods & packs": "Mod and modpack management is planned. No mod downloads or loader installation are connected yet.",
    "Mods": "Mod and modpack management is planned. No mod downloads or loader installation are connected yet.",
    "Settings": "Choose a version and memory target on Home. These preferences are saved in this browser when local storage is available. Actual device RAM detection and Android settings are not implemented yet.",
    "Home": "You're already on the Home screen."
  };
  showDialog(feature === "Home" ? "Welcome home." : feature, messages[feature] || "This section is planned for a future build.", feature === "Home" ? "GOT IT" : "BACK TO HOME", () => {
    document.querySelectorAll(".bottom-item").forEach(item => item.classList.toggle("active", item.dataset.tab === "Home"));
    window.scrollTo({top: 0, behavior: "smooth"});
  });
}
document.querySelectorAll(".feature-row").forEach(button => {
  button.addEventListener("click", () => showFeature(button.dataset.feature));
});
document.querySelectorAll(".bottom-item").forEach(button => {
  button.addEventListener("click", () => {
    const tab = button.dataset.tab;
    document.querySelectorAll(".bottom-item").forEach(item => item.classList.toggle("active", item === button));
    if (tab === "Home") {
      if (typeof modsBrowser !== "undefined") modsBrowser.hidden = true;
      if (typeof instancesBrowser !== "undefined") instancesBrowser.hidden = true;
      window.scrollTo({top: 0, behavior: "smooth"});
    } else showFeature(tab);
  });
});

const installButton = document.querySelector("#install-button");
const installPanel = document.querySelector("#install-panel");
const installStatus = document.querySelector("#install-status");
const installProgressBar = document.querySelector("#install-progress-bar");
const cancelInstallButton = document.querySelector("#cancel-install-button");
let installationRunning = false;

function setInstallationRunning(running) {
  installationRunning = running;
  installButton.disabled = running;
  installButton.textContent = running ? "INSTALLATION IN PROGRESS" : "DOWNLOAD & INSTALL VERSION ↓";
  cancelInstallButton.hidden = !running;
}

installButton.addEventListener("click", () => {
  if (!window.HyperLauncherNative || typeof window.HyperLauncherNative.installVersion !== "function") {
    showDialog("Android app required", "Version installation is available in the Android app. The browser preview cannot write Minecraft files to device storage.", "GOT IT");
    return;
  }
  const versionId = versionSelect.value;
  installPanel.hidden = false;
  installStatus.textContent = "Preparing " + versionId + " installation plan…";
  installProgressBar.style.width = "0%";
  setInstallationRunning(true);
  window.HyperLauncherNative.installVersion(versionId);
});

cancelInstallButton.addEventListener("click", () => {
  if (window.HyperLauncherNative && typeof window.HyperLauncherNative.cancelInstallation === "function") {
    window.HyperLauncherNative.cancelInstallation();
    installStatus.textContent = "Cancelling after the current network read…";
    cancelInstallButton.disabled = true;
  }
});

window.onNativeMinecraftInstall = function (event) {
  if (!event || typeof event.state !== "string") return;
  installPanel.hidden = false;
  if (event.state === "planning") {
    installStatus.textContent = "Reading official metadata for " + event.version + "…";
    return;
  }
  if (event.state === "downloading") {
    const total = Number(event.totalBytes) || 0;
    const done = Number(event.completedBytes) || 0;
    const percent = total > 0 ? Math.min(100, Math.floor(done / total * 100)) : 0;
    installProgressBar.style.width = percent + "%";
    const current = event.currentPath ? " — " + event.currentPath : "";
    installStatus.textContent = percent + "% · " + event.completedFiles + "/" + event.totalFiles + " files" + current;
    return;
  }
  if (event.state === "complete") {
    installProgressBar.style.width = "100%";
    installStatus.textContent = "Downloaded " + event.installedFiles + " verified files for " + event.version + ". Native libraries and Java runtime are not included yet.";
    setInstallationRunning(false);
    cancelInstallButton.disabled = false;
    return;
  }
  if (event.state === "cancelled") {
    installStatus.textContent = "Installation cancelled. Verified files already completed are kept.";
    setInstallationRunning(false);
    cancelInstallButton.disabled = false;
    return;
  }
  if (event.state === "error") {
    installStatus.textContent = "Installation failed: " + (event.error || "Unknown error");
    setInstallationRunning(false);
    cancelInstallButton.disabled = false;
  }
};

const refreshVersionsButton = document.querySelector("#refresh-versions");
const versionStatus = document.querySelector("#version-status");

window.onNativeMinecraftVersions = function (result) {
  refreshVersionsButton.disabled = false;
  if (!result || !result.ok || !Array.isArray(result.versions) || result.versions.length === 0) {
    versionStatus.textContent = "Could not sync official versions. Built-in options remain available.";
    return;
  }

  const previousVersion = versionSelect.value;
  const versions = result.versions
    .filter(version => version && typeof version.id === "string" &&
      /^[0-9][A-Za-z0-9._-]{0,63}$/.test(version.id) &&
      (version.type === "release" || version.type === "snapshot"))
    .slice(0, 120);

  if (!versions.length) {
    versionStatus.textContent = "The version service returned no supported entries.";
    return;
  }

  versionSelect.replaceChildren();
  const latestReleaseOption = document.createElement("option");
  latestReleaseOption.value = "release";
  latestReleaseOption.textContent = "Latest Release";
  versionSelect.appendChild(latestReleaseOption);
  const latestSnapshotOption = document.createElement("option");
  latestSnapshotOption.value = "snapshot";
  latestSnapshotOption.textContent = "Latest Snapshot";
  versionSelect.appendChild(latestSnapshotOption);
  versions.forEach(version => {
    const option = document.createElement("option");
    option.value = version.id;
    option.textContent = version.id + (version.type === "snapshot" ? " — Snapshot" : " — Release");
    versionSelect.appendChild(option);
  });

  const preserved = previousVersion === "release" || previousVersion === "snapshot" ||
    versions.some(version => version.id === previousVersion);
  if (preserved) {
    versionSelect.value = previousVersion;
  } else {
    versionSelect.value = "release";
  }
  updateVersionSummary();
  saveSettings();
  versionStatus.textContent = "Synced " + versions.length + " official versions from Mojang.";
};

function requestOfficialVersions() {
  if (!window.HyperLauncherNative || typeof window.HyperLauncherNative.refreshVersions !== "function") {
    versionStatus.textContent = "Official version sync is available in the Android app.";
    return;
  }
  refreshVersionsButton.disabled = true;
  versionStatus.textContent = "Contacting Mojang version service…";
  window.HyperLauncherNative.refreshVersions();
}
refreshVersionsButton.addEventListener("click", requestOfficialVersions);
if (window.HyperLauncherNative) requestOfficialVersions();

const modsBrowser = document.querySelector("#mods-browser");
const modSource = document.querySelector("#mod-source");
const modSearchInput = document.querySelector("#mod-search");
const modType = document.querySelector("#mod-type");
const modVersion = document.querySelector("#mod-version");
const modLoader = document.querySelector("#mod-loader");
const modSort = document.querySelector("#mod-sort");
const modSearchButton = document.querySelector("#search-mods");
const modStatus = document.querySelector("#mod-status");
const modResults = document.querySelector("#mod-results");
const loadMoreModsButton = document.querySelector("#load-more-mods");
let activeModSearchOffset = 0;
let activeModSearchTotal = 0;
const curseForgeKeyPanel = document.querySelector("#curseforge-key-panel");
const curseForgeKeyInput = document.querySelector("#curseforge-api-key");
const curseForgeKeyStatus = document.querySelector("#curseforge-key-status");
let curseForgeKeyReady = false;
let modRequestCounter = 0;
let activeModRequest = "";

function updateModSourcePanel() {
  const isCurseForge = modSource.value === "curseforge";
  curseForgeKeyPanel.hidden = !isCurseForge;
  const worldsOption = modType.querySelector('option[value="world"]');
  if (worldsOption) worldsOption.disabled = !isCurseForge;
  if (!isCurseForge && modType.value === "world") modType.value = "mod";
  const loaderNotApplicable = ["plugin", "world"].includes(modType.value);
  modLoader.disabled = loaderNotApplicable;
  if (loaderNotApplicable) modLoader.value = "any";
  if (isCurseForge && !curseForgeKeyReady) {
    curseForgeKeyStatus.textContent = "CurseForge requires an approved API key. The key stays in memory for this app session and is not saved to disk.";
  }
}
function openModBrowser() {
  modsBrowser.hidden = false;
  if (typeof instancesBrowser !== "undefined") instancesBrowser.hidden = true;
  if (!modVersion.value) modVersion.value = versionSelect.value === "snapshot" ? "" : versionSelect.value;
  updateModSourcePanel();
  document.querySelectorAll(".bottom-item").forEach(item => item.classList.toggle("active", item.dataset.tab === "Mods"));
  modsBrowser.scrollIntoView({behavior: "smooth", block: "start"});
}
function formatDownloadCount(value) {
  const count = Number(value) || 0;
  try { return new Intl.NumberFormat(undefined, {notation: "compact", maximumFractionDigits: 1}).format(count); }
  catch (error) { return String(count); }
}
function createModCard(project) {
  const card = document.createElement("article");
  card.className = "mod-card";
  const top = document.createElement("div");
  top.className = "mod-card-top";
  const iconUrl = String(project.iconUrl || "");
  if (iconUrl.startsWith("https://")) {
    const image = document.createElement("img");
    image.className = "mod-project-icon";
    image.src = iconUrl;
    image.alt = "";
    image.loading = "lazy";
    image.referrerPolicy = "no-referrer";
    image.onerror = () => image.remove();
    top.appendChild(image);
  } else {
    const placeholder = document.createElement("span");
    placeholder.className = "mod-project-icon mod-project-placeholder";
    placeholder.textContent = "✦";
    top.appendChild(placeholder);
  }
  const titleArea = document.createElement("div");
  titleArea.className = "mod-title-area";
  const title = document.createElement("h3");
  title.textContent = String(project.title || "Untitled project");
  const subtitle = document.createElement("p");
  subtitle.textContent = String(project.author || project.source || "Minecraft creator");
  titleArea.append(title, subtitle);
  top.appendChild(titleArea);
  const source = document.createElement("span");
  source.className = "mod-source-chip";
  source.textContent = String(project.source || "Catalog");
  top.appendChild(source);
  const description = document.createElement("p");
  description.className = "mod-description";
  description.textContent = String(project.description || "No description provided.");
  const meta = document.createElement("div");
  meta.className = "mod-card-meta";
  const downloads = document.createElement("span");
  downloads.textContent = formatDownloadCount(project.downloads) + " downloads";
  const kind = document.createElement("span");
  const kinds = {mod: "MOD", modpack: "MODPACK", resourcepack: "RESOURCE PACK", shader: "SHADER", datapack: "DATA PACK", plugin: "SERVER PLUGIN", world: "WORLD"};
  kind.textContent = kinds[project.type] || "PROJECT";
  meta.append(downloads, kind);
  const link = document.createElement("a");
  link.className = "mod-open-link";
  link.href = String(project.url || "");
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.textContent = "VIEW PROJECT ↗";
  link.addEventListener("click", event => {
    if (window.HyperLauncherNative && typeof window.HyperLauncherNative.openProjectUrl === "function") {
      event.preventDefault();
      window.HyperLauncherNative.openProjectUrl(link.href);
    }
  });
  const versionsButton = document.createElement("button");
  versionsButton.type = "button";
  versionsButton.className = "mod-versions-button";
  versionsButton.textContent = "BROWSE FILES ↓";
  versionsButton.addEventListener("click", () => loadProjectVersions(project));
  card.append(top, description, meta, link, versionsButton);
  return card;
}
function renderModResults(projects, append = false) {
  if (!append) modResults.replaceChildren();
  if (!Array.isArray(projects) || projects.length === 0) {
    if (append) return;
    const empty = document.createElement("div");
    empty.className = "mod-empty";
    empty.textContent = "No matching projects were found. Try a different search, version, or content type.";
    modResults.appendChild(empty);
    return;
  }
  projects.forEach(project => modResults.appendChild(createModCard(project)));
}
async function searchModrinthInBrowser(requestId, query, type, gameVersion, loader, sort, offset, append) {
  const groups = [[type === "datapack" ? "all_project_types:datapack" : "project_type:" + type]];
  if (gameVersion) groups.push(["versions:" + gameVersion]);
  if (loader !== "any") groups.push(["categories:" + loader]);
  const order = sort === "downloads" || sort === "updated" ? sort : "relevance";
  const url = "https://api.modrinth.com/v2/search?query=" + encodeURIComponent(query) +
    "&facets=" + encodeURIComponent(JSON.stringify(groups)) +
    "&index=" + order + "&limit=24&offset=" + offset;
  const response = await fetch(url, {headers: {"Accept": "application/json"}});
  if (!response.ok) throw new Error("Modrinth returned HTTP " + response.status);
  const body = await response.json();
  const projects = (Array.isArray(body.hits) ? body.hits : []).map(item => ({
    id: item.project_id,
    title: item.title,
    description: item.description,
    author: item.author,
    downloads: item.downloads,
    iconUrl: item.icon_url,
    url: "https://modrinth.com/" + (type === "modpack" ? "modpack" : type === "resourcepack" ? "resourcepack" : type === "datapack" ? "datapack" : type === "plugin" ? "plugin" : type) + "/" + item.slug,
    source: "Modrinth",
    type
  }));
  if (requestId !== activeModRequest) return;
  renderModResults(projects, append);
  activeModSearchOffset = offset + projects.length;
  activeModSearchTotal = Number(body.total_hits) || activeModSearchOffset;
  loadMoreModsButton.hidden = activeModSearchOffset >= activeModSearchTotal || projects.length === 0;
  modStatus.textContent = "Showing " + activeModSearchOffset + " of " + activeModSearchTotal + " Modrinth projects.";
}
function runModSearch(offset = 0, append = false) {
  const source = modSource.value;
  const query = modSearchInput.value.trim();
  const type = modType.value;
  const gameVersion = modVersion.value.trim();
  const loader = modLoader.value;
  const sort = modSort.value;
  if (gameVersion && !/^[0-9][A-Za-z0-9._-]{0,31}$/.test(gameVersion)) {
    modStatus.textContent = "Enter a valid Minecraft version, for example 1.20.1, or leave it blank.";
    return;
  }
  const requestId = String(++modRequestCounter);
  activeModRequest = requestId;
  if (!append) { activeModSearchOffset = 0; activeModSearchTotal = 0; loadMoreModsButton.hidden = true; }
  modSearchButton.disabled = true;
  modStatus.textContent = "Searching " + (source === "modrinth" ? "Modrinth" : "CurseForge") + "…";
  if (!append) modResults.replaceChildren();
  loadMoreModsButton.disabled = true;
  if (!window.HyperLauncherNative || typeof window.HyperLauncherNative.searchMods !== "function") {
    if (source === "curseforge") {
      modSearchButton.disabled = false;
      loadMoreModsButton.disabled = false;
      modStatus.textContent = "CurseForge API search is available in the Android app, where the API key is kept in app memory. Modrinth search works in this browser preview.";
      return;
    }
    searchModrinthInBrowser(requestId, query, type, gameVersion, loader, sort, offset, append)
      .catch(error => {
        if (requestId === activeModRequest) {
          modStatus.textContent = "Could not load Modrinth: " + error.message;
          loadMoreModsButton.disabled = false;
        }
      })
      .finally(() => {
        if (requestId === activeModRequest) {
          modSearchButton.disabled = false;
          loadMoreModsButton.disabled = false;
        }
      });
    return;
  }
  window.HyperLauncherNative.searchMods(source, query, type, gameVersion, loader, sort, offset, requestId);
}
window.onNativeModSearch = function (result) {
  if (!result || result.requestId !== activeModRequest) return;
  modSearchButton.disabled = false;
  if (!result.ok) {
    loadMoreModsButton.disabled = false;
    modStatus.textContent = result.error || "The catalog search failed.";
    return;
  }
  const projects = result.results && typeof result.results.length === "number" ? Array.from(result.results) : [];
  const offset = Number(result.offset) || 0;
  renderModResults(projects, offset > 0);
  activeModSearchOffset = offset + projects.length;
  activeModSearchTotal = Number(result.total) || activeModSearchOffset;
  loadMoreModsButton.disabled = false;
  loadMoreModsButton.hidden = activeModSearchOffset >= activeModSearchTotal || projects.length === 0;
  modStatus.textContent = "Showing " + activeModSearchOffset + " of " + activeModSearchTotal + " " +
    (result.source === "curseforge" ? "CurseForge" : "Modrinth") + " projects.";
};
window.onNativeCurseForgeKey = function (result) {
  if (!result || !curseForgeKeyStatus) return;
  if (result.ok) {
    curseForgeKeyReady = !result.cleared;
    curseForgeKeyStatus.textContent = result.cleared
      ? "CurseForge key cleared from app memory."
      : "CurseForge API key connected for this app session. It is not saved to disk.";
    if (result.cleared) modResults.replaceChildren();
    else runModSearch();
  } else {
    curseForgeKeyReady = false;
    curseForgeKeyStatus.textContent = result.error || "Could not accept that API key.";
  }
};
modSource.addEventListener("change", updateModSourcePanel);
modType.addEventListener("change", updateModSourcePanel);
modSearchButton.addEventListener("click", () => runModSearch(0, false));
loadMoreModsButton.addEventListener("click", () => runModSearch(activeModSearchOffset, true));
modSearchInput.addEventListener("keydown", event => {
  if (event.key === "Enter") runModSearch();
});
document.querySelector("#save-curseforge-key").addEventListener("click", () => {
  const key = curseForgeKeyInput.value.trim();
  if (!key) {
    curseForgeKeyStatus.textContent = "Paste your CurseForge API key first.";
    return;
  }
  if (!window.HyperLauncherNative || typeof window.HyperLauncherNative.setCurseForgeApiKey !== "function") {
    curseForgeKeyStatus.textContent = "Connect a key inside the Hyper Launcher Android app.";
    return;
  }
  curseForgeKeyStatus.textContent = "Checking key format…";
  window.HyperLauncherNative.setCurseForgeApiKey(key);
  curseForgeKeyInput.value = "";
});
document.querySelector("#clear-curseforge-key").addEventListener("click", () => {
  if (window.HyperLauncherNative && typeof window.HyperLauncherNative.clearCurseForgeApiKey === "function") {
    window.HyperLauncherNative.clearCurseForgeApiKey();
  } else {
    curseForgeKeyReady = false;
    curseForgeKeyStatus.textContent = "CurseForge key cleared from this preview.";
  }
});
document.querySelectorAll("[data-native-open]").forEach(link => {
  link.addEventListener("click", event => {
    if (window.HyperLauncherNative && typeof window.HyperLauncherNative.openProjectUrl === "function") {
      event.preventDefault();
      window.HyperLauncherNative.openProjectUrl(link.href);
    }
  });
});

const projectVersionsPanel = document.querySelector("#project-versions");
const versionsProjectTitle = document.querySelector("#versions-project-title");
const versionsStatus = document.querySelector("#versions-status");
const projectVersionList = document.querySelector("#project-version-list");
const modDownloadPanel = document.querySelector("#mod-download-panel");
const modDownloadStatus = document.querySelector("#mod-download-status");
const modDownloadProgressBar = document.querySelector("#mod-download-progress-bar");
const cancelModDownloadButton = document.querySelector("#cancel-mod-download");
let currentVersionsProject = null;
let modVersionsRequestCounter = 0;
let activeModVersionsRequest = "";
let modInstallRequestCounter = 0;
let activeModInstallRequest = "";
let activeModInstallButton = null;

function readableFileSize(value) {
  const bytes = Number(value) || 0;
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + " MB";
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + " GB";
}
function renderProjectVersions(versions) {
  projectVersionList.replaceChildren();
  if (!Array.isArray(versions) || versions.length === 0) {
    versionsStatus.textContent = "No compatible files were found for the selected game version and loader.";
    return;
  }
  versions.forEach(file => {
    const card = document.createElement("article");
    card.className = "project-version-card";
    const details = document.createElement("div");
    details.className = "project-version-details";
    const title = document.createElement("strong");
    title.textContent = String(file.name || file.versionNumber || "Project file");
    const sub = document.createElement("p");
    sub.textContent = String(file.fileName || "Download file") + " · " + readableFileSize(file.size);
    const date = document.createElement("small");
    date.textContent = file.datePublished ? new Date(file.datePublished).toLocaleDateString() : "Compatible file";
    details.append(title, sub, date);
    const installButton = document.createElement("button");
    installButton.type = "button";
    installButton.className = "install-mod-file";
    installButton.textContent = window.HyperLauncherNative && typeof window.HyperLauncherNative.installModFile === "function"
      ? (currentVersionsProject && currentVersionsProject.type === "modpack" ? "SAVE PACK" : currentVersionsProject && currentVersionsProject.type === "world" ? "SAVE WORLD" : currentVersionsProject && currentVersionsProject.type === "plugin" ? "SAVE PLUGIN" : currentVersionsProject && currentVersionsProject.type === "datapack" ? "SAVE DATA PACK" : "INSTALL")
      : "GET FILE ↗";
    installButton.addEventListener("click", () => requestModFile(file, installButton));
    card.append(details, installButton);
    projectVersionList.appendChild(card);
  });
  versionsStatus.textContent = "Showing " + versions.length + " compatible file" + (versions.length === 1 ? "" : "s") + ".";
}
function loadProjectVersions(project) {
  currentVersionsProject = project;
  projectVersionsPanel.hidden = false;
  modDownloadPanel.hidden = true;
  versionsProjectTitle.textContent = String(project.title || "Project") + " files";
  versionsStatus.textContent = "Finding files for " + (modVersion.value.trim() || "all game versions") + "…";
  projectVersionList.replaceChildren();
  projectVersionsPanel.scrollIntoView({behavior: "smooth", block: "start"});
  const requestId = "versions-" + (++modVersionsRequestCounter);
  activeModVersionsRequest = requestId;
  const gameVersion = modVersion.value.trim();
  const loader = modLoader.value;
  if (window.HyperLauncherNative && typeof window.HyperLauncherNative.listModVersions === "function") {
    window.HyperLauncherNative.listModVersions(
      project.sourceId || (project.source === "CurseForge" ? "curseforge" : "modrinth"),
      String(project.id),
      project.type,
      gameVersion,
      loader,
      requestId
    );
    return;
  }
  if (project.source !== "Modrinth") {
    versionsStatus.textContent = "CurseForge file browsing needs the Hyper Launcher Android app and a valid API key.";
    return;
  }
  const params = new URLSearchParams();
  if (gameVersion) params.set("game_versions", JSON.stringify([gameVersion]));
  if (loader !== "any") params.set("loaders", JSON.stringify([loader]));
  const endpoint = "https://api.modrinth.com/v2/project/" + encodeURIComponent(project.id) + "/version" +
    (params.toString() ? "?" + params.toString() : "");
  fetch(endpoint, {headers: {"Accept": "application/json"}})
    .then(response => {
      if (!response.ok) throw new Error("Modrinth returned HTTP " + response.status);
      return response.json();
    })
    .then(data => {
      if (requestId !== activeModVersionsRequest) return;
      const files = [];
      (Array.isArray(data) ? data : []).forEach(version => {
        (Array.isArray(version.files) ? version.files : []).forEach(file => {
          files.push({
            fileId: version.id,
            fileName: file.filename,
            name: version.name || version.version_number,
            versionNumber: version.version_number,
            datePublished: version.date_published,
            size: file.size,
            sha1: file.hashes && file.hashes.sha1,
            downloadUrl: file.url,
            primary: !!file.primary
          });
        });
      });
      renderProjectVersions(files);
    })
    .catch(error => {
      if (requestId === activeModVersionsRequest) versionsStatus.textContent = "Could not load files: " + error.message;
    });
}
function requestModFile(file, button) {
  if (!currentVersionsProject) return;
  if (!window.HyperLauncherNative || typeof window.HyperLauncherNative.installModFile !== "function") {
    const url = String(file.downloadUrl || "");
    if (url.startsWith("https://cdn.modrinth.com/") || url.startsWith("https://cdn-raw.modrinth.com/")) {
      const link = document.createElement("a");
      link.href = url;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      link.click();
      return;
    }
    versionsStatus.textContent = "File downloads into Minecraft storage are available in the Android app.";
    return;
  }
  if (activeModInstallButton && activeModInstallButton !== button) {
    versionsStatus.textContent = "Finish or cancel the current file download first.";
    return;
  }
  const sourceId = currentVersionsProject.source === "CurseForge" ? "curseforge" : "modrinth";
  const requestId = "install-" + (++modInstallRequestCounter);
  activeModInstallRequest = requestId;
  activeModInstallButton = button;
  button.disabled = true;
  button.textContent = "PREPARING…";
  modDownloadPanel.hidden = false;
  modDownloadProgressBar.style.width = "0%";
  modDownloadStatus.textContent = "Checking file metadata and compatibility…";
  cancelModDownloadButton.disabled = false;
  window.HyperLauncherNative.installModFile(
    sourceId,
    String(currentVersionsProject.id),
    String(file.fileId),
    String(file.fileName),
    currentVersionsProject.type,
    modVersion.value.trim(),
    modLoader.value,
    requestId
  );
}
window.onNativeModVersions = function (result) {
  if (!result || result.requestId !== activeModVersionsRequest) return;
  if (!result.ok) {
    versionsStatus.textContent = result.error || "Could not load project files.";
    return;
  }
  const versions = result.versions && typeof result.versions.length === "number"
    ? Array.from(result.versions) : [];
  renderProjectVersions(versions);
};
window.onNativeModInstall = function (event) {
  if (!event || event.requestId !== activeModInstallRequest) return;
  modDownloadPanel.hidden = false;
  if (event.state === "resolving") {
    modDownloadStatus.textContent = "Checking the official file record for " + (event.fileName || "this file") + "…";
    return;
  }
  if (event.state === "downloading") {
    const total = Number(event.totalBytes) || 0;
    const done = Number(event.downloadedBytes) || 0;
    const percent = total > 0 ? Math.min(100, Math.floor(done / total * 100)) : 0;
    modDownloadProgressBar.style.width = percent + "%";
    modDownloadStatus.textContent = "Downloading " + (event.fileName || "file") + " · " + percent + "%";
    if (activeModInstallButton) activeModInstallButton.textContent = "DOWNLOADING…";
    return;
  }
  if (event.state === "complete") {
    modDownloadProgressBar.style.width = "100%";
    modDownloadStatus.textContent = "Verified and saved " + event.fileName + " to " + event.path + ".";
    if (activeModInstallButton) {
      activeModInstallButton.textContent = "SAVED ✓";
      activeModInstallButton.disabled = false;
    }
    activeModInstallButton = null;
    return;
  }
  if (event.state === "cancelled") {
    modDownloadStatus.textContent = "Download cancelled. The incomplete file was removed.";
  } else if (event.state === "error") {
    modDownloadStatus.textContent = "Could not install file: " + (event.error || "Unknown error");
  }
  if (activeModInstallButton) {
    activeModInstallButton.textContent = "RETRY";
    activeModInstallButton.disabled = false;
  }
  activeModInstallButton = null;
};
document.querySelector("#close-project-versions").addEventListener("click", () => {
  projectVersionsPanel.hidden = true;
  activeModVersionsRequest = "";
});
cancelModDownloadButton.addEventListener("click", () => {
  if (window.HyperLauncherNative && typeof window.HyperLauncherNative.cancelModInstall === "function") {
    cancelModDownloadButton.disabled = true;
    modDownloadStatus.textContent = "Cancelling download…";
    window.HyperLauncherNative.cancelModInstall();
  }
});


const instancesBrowser = document.querySelector("#instances-browser");
const instanceNameInput = document.querySelector("#instance-name");
const createInstanceButton = document.querySelector("#create-instance");
const instancesStatus = document.querySelector("#instances-status");
const instanceList = document.querySelector("#instance-list");

function renderInstancesState(state) {
  instanceList.replaceChildren();
  const records = state.instances && typeof state.instances.length === "number"
    ? Array.from(state.instances) : [];
  const selected = records.find(item => item.id === state.selectedId);
  if (selected) {
    let option = Array.from(versionSelect.options).find(item => item.value === selected.version);
    if (!option && /^[0-9][A-Za-z0-9._-]{0,63}$/.test(String(selected.version))) {
      option = document.createElement("option");
      option.value = selected.version;
      option.textContent = selected.version + " — Saved instance version";
      versionSelect.appendChild(option);
    }
    if (option && versionSelect.value !== selected.version) {
      versionSelect.value = selected.version;
      updateVersionSummary();
      saveSettings();
    }
  }
  records.forEach(item => {
    const card = document.createElement("article");
    card.className = "instance-card";
    const details = document.createElement("div");
    details.className = "instance-card-details";
    const titleRow = document.createElement("div");
    titleRow.className = "instance-title-row";
    const title = document.createElement("strong");
    title.textContent = String(item.name || "Unnamed instance");
    titleRow.appendChild(title);
    if (item.id === state.selectedId) {
      const active = document.createElement("span");
      active.className = "instance-active-chip";
      active.textContent = "ACTIVE";
      titleRow.appendChild(active);
    }
    const meta = document.createElement("p");
    meta.textContent = "Minecraft " + String(item.version || "release") +
      (item.id === "default" ? " · Existing storage" : " · Separate storage");
    details.append(titleRow, meta);
    const actions = document.createElement("div");
    actions.className = "instance-actions";
    if (item.id !== state.selectedId) {
      const selectButton = document.createElement("button");
      selectButton.type = "button";
      selectButton.className = "instance-select-button";
      selectButton.textContent = "SELECT";
      selectButton.addEventListener("click", () => selectLauncherInstance(String(item.id)));
      actions.appendChild(selectButton);
    }
    if (item.id !== "default") {
      const renameButton = document.createElement("button");
      renameButton.type = "button";
      renameButton.className = "instance-select-button";
      renameButton.textContent = "RENAME";
      renameButton.addEventListener("click", () => showInstanceRenameEditor(item, card));
      actions.appendChild(renameButton);
      const deleteButton = document.createElement("button");
      deleteButton.type = "button";
      deleteButton.className = "instance-delete-button";
      deleteButton.textContent = "DELETE";
      deleteButton.addEventListener("click", () => confirmDeleteLauncherInstance(item));
      actions.appendChild(deleteButton);
    }
    card.append(details, actions);
    instanceList.appendChild(card);
  });
  if (!selected) {
    instancesStatus.textContent = "The active instance could not be found. Refresh the instance list.";
    return;
  }
  instancesStatus.textContent = records.length + " instance" + (records.length === 1 ? "" : "s") +
    " · Active: " + selected.name + ". Game and content downloads now target this instance.";
}

function showInstanceRenameEditor(item, card) {
  const existing = card.querySelector(".instance-rename-editor");
  if (existing) {
    existing.remove();
    return;
  }
  instanceList.querySelectorAll(".instance-rename-editor").forEach(editor => editor.remove());
  const editor = document.createElement("div");
  editor.className = "instance-rename-editor";
  const label = document.createElement("label");
  label.textContent = "RENAME INSTANCE";
  const input = document.createElement("input");
  input.type = "text";
  input.maxLength = 32;
  input.autocomplete = "off";
  input.value = String(item.name || "");
  input.setAttribute("aria-label", "New instance name");
  const save = document.createElement("button");
  save.type = "button";
  save.textContent = "SAVE NAME";
  const cancel = document.createElement("button");
  cancel.type = "button";
  cancel.textContent = "CANCEL";
  const actions = document.createElement("div");
  actions.className = "instance-rename-actions";
  actions.append(save, cancel);
  editor.append(label, input, actions);
  card.appendChild(editor);
  cancel.addEventListener("click", () => editor.remove());
  const submit = () => {
    const name = input.value.trim();
    if (!name) {
      instancesStatus.textContent = "Enter a name for this instance.";
      input.focus();
      return;
    }
    if (!window.HyperLauncherNative || typeof window.HyperLauncherNative.renameInstance !== "function") {
      instancesStatus.textContent = "Rename instances in the Hyper Launcher Android app.";
      return;
    }
    save.disabled = true;
    cancel.disabled = true;
    instancesStatus.textContent = "Saving instance name…";
    window.HyperLauncherNative.renameInstance(String(item.id), name);
  };
  save.addEventListener("click", submit);
  input.addEventListener("keydown", event => {
    if (event.key === "Enter") submit();
    if (event.key === "Escape") editor.remove();
  });
  input.focus();
  input.select();
}
function openInstancesBrowser() {
  instancesBrowser.hidden = false;
  if (typeof modsBrowser !== "undefined") modsBrowser.hidden = true;
  document.querySelectorAll(".bottom-item").forEach(item => {
    item.classList.toggle("active", item.dataset.tab === "Instances");
  });
  instancesBrowser.scrollIntoView({behavior: "smooth", block: "start"});
  loadLauncherInstances();
}
function loadLauncherInstances() {
  if (window.HyperLauncherNative && typeof window.HyperLauncherNative.refreshInstances === "function") {
    instancesStatus.textContent = "Loading saved instances…";
    createInstanceButton.disabled = true;
    window.HyperLauncherNative.refreshInstances();
  } else {
    instancesStatus.textContent = "Instance directories are available in the Android app. The browser preview cannot create app-private game folders.";
    createInstanceButton.disabled = true;
  }
}
function createLauncherInstance() {
  const name = instanceNameInput.value.trim();
  if (!name) {
    instancesStatus.textContent = "Enter a name for your new instance.";
    instanceNameInput.focus();
    return;
  }
  if (!window.HyperLauncherNative || typeof window.HyperLauncherNative.createInstance !== "function") {
    instancesStatus.textContent = "Create instances in the Android app to get separate game folders.";
    return;
  }
  createInstanceButton.disabled = true;
  instancesStatus.textContent = "Creating a separate game folder…";
  window.HyperLauncherNative.createInstance(name, versionSelect.value);
}
function selectLauncherInstance(id) {
  if (!window.HyperLauncherNative || typeof window.HyperLauncherNative.selectInstance !== "function") return;
  createInstanceButton.disabled = true;
  instancesStatus.textContent = "Switching active instance…";
  window.HyperLauncherNative.selectInstance(id);
}
function confirmDeleteLauncherInstance(item) {
  showDialog(
    "Delete " + String(item.name || "instance") + "?",
    "This permanently removes this instance's app-private game files and mod folders. Select another instance first.",
    "DELETE INSTANCE",
    () => {
      if (window.HyperLauncherNative && typeof window.HyperLauncherNative.deleteInstance === "function") {
        createInstanceButton.disabled = true;
        instancesStatus.textContent = "Removing instance files…";
        window.HyperLauncherNative.deleteInstance(String(item.id));
      }
    }
  );
}
window.onNativeInstances = function (state) {
  createInstanceButton.disabled = false;
  if (!state || !state.ok) {
    instancesStatus.textContent = state && state.error ? state.error : "Could not load launcher instances.";
    return;
  }
  if (!Array.isArray(state.instances)) {
    instancesStatus.textContent = "The instance registry returned an invalid response.";
    return;
  }
  renderInstancesState(state);
  instanceNameInput.value = "";
};
createInstanceButton.addEventListener("click", createLauncherInstance);
instanceNameInput.addEventListener("keydown", event => {
  if (event.key === "Enter") createLauncherInstance();
});
if (window.HyperLauncherNative && typeof window.HyperLauncherNative.refreshInstances === "function") {
  window.HyperLauncherNative.refreshInstances();
}
