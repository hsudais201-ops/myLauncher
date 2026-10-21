(() => {
  "use strict";
  const KEY = "voidcraft.feature-installed.theme-studio";
  const install = document.querySelector("[data-theme-studio-install]");
  const open = document.querySelector("[data-theme-studio-open]");
  const uninstall = document.querySelector("[data-theme-studio-uninstall]");
  const status = document.querySelector("[data-theme-studio-status]");
  const message = document.querySelector("#theme-studio-store-message");
  if (!install || !open || !uninstall || !status) return;
  function isInstalled() { try { return localStorage.getItem(KEY) === "true"; } catch { return false; } }
  function render() {
    const installed=isInstalled();
    install.disabled=installed; install.textContent=installed?"Installed":"Install";
    open.disabled=!installed; uninstall.disabled=!installed;
    status.textContent=installed?"Installed in this browser":"Not installed";
    status.dataset.installed=String(installed);
  }
  install.addEventListener("click",()=>{
    try { localStorage.setItem(KEY,"true"); message.textContent="Theme Studio installed for this browser. Choose Open to customize your website colors."; render(); }
    catch { message.textContent="Could not save install state. Check browser site-storage settings."; }
  });
  uninstall.addEventListener("click",()=>{
    try {
      localStorage.removeItem(KEY);
      localStorage.removeItem("voidcraft.theme-studio.palette.v1");
      document.documentElement.removeAttribute("style");
      document.getElementById("voidcraft-user-theme")?.remove();
      message.textContent="Theme Studio uninstalled and its saved custom palette was cleared. Default website colors are restored.";
      render();
    } catch { message.textContent="Could not uninstall Theme Studio. Check browser site-storage settings."; }
  });
  open.addEventListener("click",()=>{
    if(!isInstalled()){message.textContent="Install Theme Studio before opening it.";return;}
    window.location.href="theme-studio.html";
  });
  render();
})();