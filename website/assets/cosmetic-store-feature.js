(() => {
  "use strict";
  const KEY="voidcraft.feature-installed.cosmetic-store";
  const EQUIPPED="voidcraft.cosmetic-store.equipped";
  const install=document.querySelector("[data-cosmetic-store-install]");
  const open=document.querySelector("[data-cosmetic-store-open]");
  const uninstall=document.querySelector("[data-cosmetic-store-uninstall]");
  const status=document.querySelector("[data-cosmetic-store-status]");
  const message=document.querySelector("#cosmetic-store-feature-message");
  if(!install||!open||!uninstall||!status||!message)return;
  const installed=()=>{try{return localStorage.getItem(KEY)==="true"}catch{return false}};
  function render(){const yes=installed();install.disabled=yes;install.textContent=yes?"Installed":"Install";open.disabled=!yes;uninstall.disabled=!yes;status.textContent=yes?"Installed in this browser":"Not installed";status.dataset.installed=String(yes)}
  install.addEventListener("click",()=>{try{localStorage.setItem(KEY,"true");message.textContent="Cosmetic Store installed in this browser. Open it to preview profile effects; use the launcher Cosmetics screen for in-game Fabric equip handoff.";render()}catch{message.textContent="Could not save installation state. Check browser site-storage settings."}});
  uninstall.addEventListener("click",()=>{try{localStorage.removeItem(KEY);localStorage.removeItem(EQUIPPED);message.textContent="Cosmetic Store uninstalled from this browser. Its browser preview selection was cleared; any game config file is unchanged.";render()}catch{message.textContent="Could not uninstall. Check browser site-storage settings."}});
  open.addEventListener("click",()=>{if(!installed()){message.textContent="Install Cosmetic Store before opening it.";return}window.location.href="cosmetic-store.html"});
  render();
})();