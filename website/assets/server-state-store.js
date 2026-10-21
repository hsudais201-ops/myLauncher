(() => {
  "use strict";
  const KEY = "voidcraft.feature-installed.server-state";
  const install = document.querySelector("[data-server-state-install]");
  const open = document.querySelector("[data-server-state-open]");
  const uninstall = document.querySelector("[data-server-state-uninstall]");
  const status = document.querySelector("[data-server-state-status]");
  const message = document.querySelector("#server-state-store-message");
  if (!install || !open || !uninstall || !status) return;
  function isInstalled() { try { return localStorage.getItem(KEY) === "true"; } catch { return false; } }
  function render() {
    const installed = isInstalled();
    install.disabled = installed;
    install.textContent = installed ? "Installed" : "Install";
    open.disabled = !installed;
    uninstall.disabled = !installed;
    status.textContent = installed ? "Installed in this browser" : "Not installed";
    status.dataset.installed = String(installed);
  }
  install.addEventListener("click", () => {
    try { localStorage.setItem(KEY, "true"); message.textContent = "Server State installed for this browser. Your server list stays local to this browser."; render(); }
    catch { message.textContent = "Could not save the install state. Check browser site-storage settings."; }
  });
  uninstall.addEventListener("click", () => {
    try { localStorage.removeItem(KEY); message.textContent = "Server State uninstalled from this browser. Saved server addresses were kept; remove them on the Server State page if desired."; render(); }
    catch { message.textContent = "Could not update the install state. Check browser site-storage settings."; }
  });
  open.addEventListener("click", () => {
    if (!isInstalled()) { message.textContent = "Install Server State before opening it."; return; }
    window.location.href = "servers.html";
  });
  render();
})();