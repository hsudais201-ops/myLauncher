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
memorySlider.addEventListener("input", () => {
  document.querySelector("#memory-value").textContent = memorySlider.value + " GB";
});

document.querySelector("#play-button").addEventListener("click", () => {
  const version = versionSelect.value === "snapshot" ? "Latest Snapshot" : versionSelect.value;
  showDialog(
    "Your adventure is queued.",
    "Selected version: " + version + ". Memory allocation: " + memorySlider.value + " GB. This mobile UI is a prototype: it cannot download or run Minecraft yet. The Android launcher engine, Java runtime support, Microsoft sign-in, and local profile flow still need to be built.",
    "UNDERSTOOD"
  );
});

document.querySelector("#account-button").addEventListener("click", () => {
  showDialog(
    "Your player profile",
    "Offline Player is a profile placeholder for this preview. Profile creation and saving, and Microsoft account authentication, are not connected yet.",
    "OK"
  );
});

function showFeature(feature) {
  const messages = {
    "Instances": "Your game instances will live here. Creating, importing, and installing separate Minecraft versions is planned for a later build.",
    "Mods & packs": "A place for mods and modpacks is planned. Mod loader support and safe downloads are not connected yet.",
    "Mods": "A place for mods and modpacks is planned. Mod loader support and safe downloads are not connected yet.",
    "Settings": "This preview lets you select a version and adjust the memory setting. Device detection and saving preferences are not implemented yet.",
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
