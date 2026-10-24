(() => {
  "use strict";
  const open = document.querySelector("[data-cosmetic-store-open]");
  const status = document.querySelector("[data-cosmetic-store-status]");
  const message = document.querySelector("#cosmetic-store-feature-message");
  if (open) {
    open.addEventListener("click", function() {
      window.location.href = "cosmetic-store.html";
    });
  }
  if (status) {
    status.textContent = "Purchase to unlock Equip";
    status.dataset.installed = "false";
  }
  if (message) {
    message.textContent = "No separate installation is needed. Buy a released cosmetic, then Equip appears after payment is confirmed.";
  }
})();