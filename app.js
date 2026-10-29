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
const storageKey = "hyper-launcher-mobile-settings-v1";

function readSettings() {
  try {
    const saved = JSON.parse(localStorage.getItem(storageKey) || "{}");
    if (saved.version && Array.from(versionSelect.options).some(option => option.value === saved.version)) {
      versionSelect.value = saved.version;
    }
    if (Number.isInteger(saved.memory) && saved.memory >= 2 && saved.memory <= 8) {
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
});
memorySlider.addEventListener("input", () => {
  updateMemorySummary();
  saveSettings();
});

document.querySelector("#play-button").addEventListener("click", () => {
  const version = versionSelect.value === "snapshot" ? "Latest Snapshot" : versionSelect.value;
  showDialog(
    "Launcher engine not connected",
    "Your setup is saved on this device. Selected version: " + version + ". Memory target: " + memorySlider.value + " GB. This is still a browser UI prototype; it cannot download or launch Minecraft. The next major step is the Android app shell, compatible Java runtime, game libraries, and process launch integration.",
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
    if (tab === "Home") window.scrollTo({top: 0, behavior: "smooth"});
    else showFeature(tab);
  });
});

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
  versions.forEach(version => {
    const option = document.createElement("option");
    option.value = version.id;
    option.textContent = version.id + (version.type === "snapshot" ? " — Snapshot" : " — Release");
    versionSelect.appendChild(option);
  });

  const preserved = versions.some(version => version.id === previousVersion);
  if (preserved) {
    versionSelect.value = previousVersion;
  } else {
    const latestRelease = versions.find(version => version.type === "release");
    versionSelect.value = latestRelease ? latestRelease.id : versions[0].id;
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
