const dialog = document.querySelector("#app-dialog");
const dialogTitle = document.querySelector("#dialog-title");
const dialogMessage = document.querySelector("#dialog-message");
const dialogAction = document.querySelector("#dialog-action");

function showDialog(title, message, action = "GOT IT") {
  dialogTitle.textContent = title;
  dialogMessage.textContent = message;
  dialogAction.firstChild.textContent = action + " ";
  if (typeof dialog.showModal === "function") dialog.showModal();
  else window.alert(title + "\n\n" + message);
}
function closeDialog() { if (dialog.open) dialog.close(); }

document.querySelector("#dialog-close").addEventListener("click", closeDialog);
dialogAction.addEventListener("click", closeDialog);
dialog.addEventListener("click", event => {
  if (event.target === dialog) closeDialog();
});

const versionSelect = document.querySelector("#version-select");
const memorySlider = document.querySelector("#memory-slider");
memorySlider.addEventListener("input", () => {
  document.querySelector("#memory-value").textContent = memorySlider.value + " GB";
});

function playPreview() {
  const version = versionSelect.value === "snapshot" ? "Latest Snapshot" : versionSelect.value;
  showDialog(
    "Your adventure is queued.",
    "Selected version: " + version + ". Memory allocation: " + memorySlider.value + " GB. This is currently a UI prototype: the Minecraft runtime, downloads, Microsoft sign-in, and offline profile flow still need to be implemented before Play can launch the game.",
    "UNDERSTOOD"
  );
}
document.querySelector("#hero-play").addEventListener("click", playPreview);
document.querySelector("#card-play").addEventListener("click", playPreview);

const pageNames = {
  home: "HOME",
  instances: "INSTANCES",
  mods: "MODS & PACKS",
  settings: "SETTINGS"
};
const pageCopy = {
  instances: ["Your instances.", "Manage separate game versions, mod loaders, and profiles here. Instance creation and installation are planned for a later build."],
  mods: ["Make it your world.", "A dedicated place for mods and modpacks is coming. Mod downloads and loader integration are not connected yet."],
  settings: ["Tune your setup.", "Choose a game version and adjust the preview memory setting. System detection and persistent launcher preferences are not implemented yet."]
};
function selectPage(page) {
  document.querySelectorAll(".nav-item").forEach(item => item.classList.toggle("active", item.dataset.page === page));
  document.querySelector("#crumb-page").textContent = pageNames[page] || "HOME";
  if (page !== "home") {
    const copy = pageCopy[page];
    showDialog(copy[0], copy[1], "BACK TO HOME");
    dialogAction.onclick = () => {
      dialogAction.onclick = null;
      closeDialog();
      selectPage("home");
    };
  } else {
    dialogAction.onclick = null;
  }
}
document.querySelectorAll(".nav-item").forEach(item => {
  item.addEventListener("click", () => selectPage(item.dataset.page));
});
document.querySelector("#explore-button").addEventListener("click", () => selectPage("instances"));
document.querySelector("#all-instances").addEventListener("click", () => selectPage("instances"));

document.querySelector("#account-button").addEventListener("click", () => {
  showDialog(
    "Choose your player profile.",
    "The current UI shows a local offline profile named Offline Player. You can use a local profile in a future build, but authentication and offline profile saving are not wired up yet.",
    "OK"
  );
});
document.querySelector("#about-button").addEventListener("click", () => {
  showDialog(
    "Hyper Launcher · Preview 0.1.0",
    "A desktop-first Minecraft Java launcher UI prototype. This build includes responsive navigation, version selection, a memory slider, and preview dialogs. It does not yet download or launch Minecraft.",
    "NICE"
  );
});
