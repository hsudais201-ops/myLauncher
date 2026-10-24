(() => {
  "use strict";

  const EQUIPPED_KEY = "voidcraft.cosmetic-store.equipped";
  const grid = document.getElementById("cosmetic-grid");
  if (!grid || window.__voidcraftNewCosmeticStoreLoaded) return;
  window.__voidcraftNewCosmeticStoreLoaded = true;

  const search = document.getElementById("cosmetic-query");
  const filter = document.getElementById("cosmetic-filter");
  const count = document.getElementById("cosmetic-result-count");
  const feedStatus = document.getElementById("cosmetic-feed-status");
  const equipState = document.getElementById("cosmetic-equip-state");
  const refreshButton = document.getElementById("cosmetic-refresh");
  const dialog = document.getElementById("cosmetic-details");
  const dialogTitle = document.getElementById("cosmetic-details-title");
  const dialogBody = document.getElementById("cosmetic-details-body");
  const dialogClose = document.getElementById("cosmetic-details-close");

  // Store products become equipable only when the server confirms ownership.

  let onlineItems = [];
  let catalogError = "";
  let isLoading = false;
  let items = [];
  let ownedItems = new Map();
  let entitlementsUnavailable = false;

  function make(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = String(text);
    return node;
  }

  function getLocal(key) {
    try { return localStorage.getItem(key) || ""; } catch { return ""; }
  }

  function isOwned(item) {
    return Boolean(item && item.kind === "cosmetic" && ownedItems.has(item.id));
  }

  function getEquipped() {
    return getLocal(EQUIPPED_KEY);
  }

  function safeAssetUrl(value) {
    if (typeof value !== "string" || !value.trim()) return "";
    try {
      const url = new URL(value.trim(), window.location.origin);
      if (url.protocol !== "https:" && url.origin !== window.location.origin) return "";
      if (url.protocol !== "https:" && url.protocol !== "http:") return "";
      return url.href;
    } catch {
      return "";
    }
  }

  function isAvailable(item) {
    return !item.localProfile && item.availability === "available" && Boolean(item.packageAvailable);
  }

  function availabilityLabel(item) {
    if (isOwned(item)) return "Owned · equip unlocked";
    if (isAvailable(item)) return "Available now";
    if (item.availability === "coming_soon") return "Coming soon";
    if (item.availability === "paused") return "Temporarily unavailable";
    return "Preview";
  }

  function priceLabel(item) {
    if (isOwned(item)) return "Purchased";
    if (item.priceCents === null || item.priceCents === undefined) return "Preview · not for sale";
    if (Number(item.priceCents) === 0) {
      return safeAssetUrl(item.packageUrl) && item.availability === "available" ? "Free download" : "Free · no package yet";
    }
    try {
      return new Intl.NumberFormat("en-US", { style: "currency", currency: item.currency || "USD" }).format(Number(item.priceCents) / 100);
    } catch {
      return "$" + (Number(item.priceCents) / 100).toFixed(2);
    }
  }

  function hasPackageDownload(item) {
    return !item.localProfile && item.availability === "available" && Number(item.priceCents) === 0 && Boolean(safeAssetUrl(item.packageUrl));
  }

  function canBuy(item) {
    return !item.localProfile && item.availability === "available" && Boolean(item.packageAvailable) &&
      item.priceCents !== null && item.priceCents !== undefined && Number(item.priceCents) > 0;
  }

  function getSearchText(item) {
    return [item.name, item.tagline, item.description, item.license, item.compatibility, item.creatorUsername, availabilityLabel(item)]
      .filter(Boolean).join(" ").toLowerCase();
  }

  function renderEquipState() {
    equipState.replaceChildren();
    const message = make("div", "cosmetics-equip-message");
    const equippedId = getEquipped();
    const equippedItem = items.find(function(item) { return item.id === equippedId && isOwned(item); });
    const ownedCosmetics = [...ownedItems.values()].filter(function(item) { return item.kind === "cosmetic"; });

    if (equippedItem) {
      message.append(make("span", "", equippedItem.name + " is equipped for this website profile. This is not an in-game Minecraft cosmetic."));
    } else if (ownedCosmetics.length) {
      message.append(make("span", "", "You own " + ownedCosmetics.length + " cosmetic" + (ownedCosmetics.length === 1 ? "" : "s") + ". Choose Equip on an owned item below."));
    } else if (entitlementsUnavailable) {
      message.append(make("span", "", "Sign in to check purchases. Equip appears after a cosmetic purchase is confirmed."));
    } else {
      message.append(make("span", "", "Purchase a released cosmetic to unlock its Equip option. Preview listings cannot be equipped."));
    }
    equipState.append(message);

    if (equippedItem) {
      const clear = make("button", "cosmetics-unequip", "Unequip");
      clear.type = "button";
      clear.addEventListener("click", function() {
        try {
          localStorage.removeItem(EQUIPPED_KEY);
          render();
          feedStatus.textContent = "Cosmetic unequipped from this website profile.";
          feedStatus.dataset.error = "false";
        } catch {
          feedStatus.textContent = "Could not clear the equipped cosmetic. Check browser storage settings.";
          feedStatus.dataset.error = "true";
        }
      });
      equipState.append(clear);
    } else if (equippedId) {
      // Never keep an equipped ID that isn't an owned cosmetic for this account.
      try { localStorage.removeItem(EQUIPPED_KEY); } catch {}
    }
  }

  function renderArt(item) {
    const art = make("div", "cos-card-art");
    art.style.setProperty("--cos-color", item.color || "#a58bff");
    const tag = make("span", "cos-card-kind", isOwned(item) ? "OWNED COSMETIC" : "COSMETIC LISTING");
    art.append(tag);

    const imageUrl = safeAssetUrl(item.imageUrl);
    if (imageUrl) {
      const image = make("img", "cos-card-art-image");
      image.src = imageUrl;
      image.alt = item.name + " cosmetic preview";
      image.loading = "lazy";
      image.decoding = "async";
      image.addEventListener("error", function() { image.remove(); }, { once: true });
      art.append(image);
    } else {
      const orbit = make("span", "cos-card-orbit");
      const orb = make("span", "cos-card-orb");
      orb.setAttribute("aria-hidden", "true");
      art.append(orbit, orb);
    }
    return art;
  }

  function renderActions(item, card) {
    const actions = make("div", "cos-card-actions");
    if (isOwned(item)) {
      const active = getEquipped() === item.id;
      const equip = make("button", "btn" + (active ? " is-equipped" : ""), active ? "✓ Equipped" : "Equip");
      equip.type = "button";
      equip.disabled = active;
      equip.setAttribute("aria-pressed", String(active));
      equip.addEventListener("click", function() {
        if (!isOwned(item)) {
          feedStatus.textContent = "This cosmetic is not owned by the signed-in account.";
          feedStatus.dataset.error = "true";
          return;
        }
        try {
          localStorage.setItem(EQUIPPED_KEY, item.id);
          render();
          feedStatus.textContent = item.name + " equipped for this website profile.";
          feedStatus.dataset.error = "false";
        } catch {
          feedStatus.textContent = "Could not save the equipped cosmetic. Check browser site-storage settings.";
          feedStatus.dataset.error = "true";
        }
      });
      actions.append(equip);
    } else {
      const freeDownload = hasPackageDownload(item);
      if (freeDownload) {
        const download = make("a", "btn", "Download free");
        download.href = safeAssetUrl(item.packageUrl);
        download.rel = "noopener noreferrer";
        download.setAttribute("aria-label", "Download free package: " + item.name);
        actions.append(download);
      }

      // Keep commerce actions visible; enable them only for a released paid SKU.
      const purchasable = canBuy(item);
      const add = make("button", "btn secondary", "Add to cart");
      add.type = "button";
      add.setAttribute("aria-label", "Add " + item.name + " to cart");
      const buy = make("button", "btn", "Buy now");
      buy.type = "button";
      buy.setAttribute("aria-label", "Buy " + item.name + " now");
      if (purchasable) {
        add.dataset.cartAdd = item.id;
        buy.dataset.buyNow = item.id;
      } else {
        add.disabled = true;
        buy.disabled = true;
        add.setAttribute("aria-disabled", "true");
        buy.setAttribute("aria-disabled", "true");
        const reason = freeDownload
          ? "This listing is free. Use Download free; checkout is not required."
          : item.availability === "coming_soon"
            ? "This listing is coming soon and cannot be purchased yet."
            : "This is a preview or has no released purchasable package yet.";
        add.title = reason;
        buy.title = reason;
      }
      actions.append(add, buy);
    }

    const details = make("button", "cos-details", "Details");
    details.type = "button";
    details.addEventListener("click", function() { showDetails(item); });
    actions.append(details);
    card.append(actions);
  }

  function showDetails(item) {
    dialogTitle.textContent = item.name || "Cosmetic details";
    dialogBody.replaceChildren();
    dialogBody.append(make("p", "", item.description || item.tagline || "No description was supplied."));
    const facts = make("dl", "cosmetics-dialog-facts");
    const pairs = [
      ["Type", item.kind === "cosmetic" ? "Cosmetic listing" : "Launcher feature"],
      ["Status", availabilityLabel(item)],
      ["Version", item.version || "Not specified"],
      ["Compatibility", item.compatibility || "Not supplied"],
      ["License", item.license || "Not supplied"],
      ["Creator", item.creatorUsername || "VoidCraft catalog"]
    ];
    pairs.forEach(function(pair) {
      facts.append(make("dt", "", pair[0]), make("dd", "", pair[1]));
    });
    dialogBody.append(facts);
    if (!item.localProfile && safeAssetUrl(item.sourceUrl)) {
      const source = make("a", "", "Open source repository ↗");
      source.href = safeAssetUrl(item.sourceUrl);
      source.target = "_blank";
      source.rel = "noopener noreferrer";
      dialogBody.append(source);
    }
    if (isOwned(item)) {
      const caveat = make("p", "", "This purchased cosmetic is unlocked on your website profile. Minecraft in-game rendering and multiplayer visibility require a compatible client/server integration.");
      dialogBody.append(caveat);
    } else if (!hasPackageDownload(item) && !canBuy(item)) {
      dialogBody.append(make("p", "", "This listing is currently informational only. A preview does not mean a Minecraft cosmetic renderer, downloadable asset or multiplayer support is available."));
    }
    if (typeof dialog.showModal === "function" && !dialog.open) dialog.showModal();
    else dialog.setAttribute("open", "");
  }

  function renderCard(item, index) {
    const active = isOwned(item) && getEquipped() === item.id;
    const card = make("article", "cos-card" + (active ? " is-equipped" : ""));
    card.style.setProperty("--cos-delay", Math.min(index, 7) * 35 + "ms");
    card.style.setProperty("--cos-color", item.color || "#a58bff");
    card.append(renderArt(item));

    const content = make("div", "cos-card-content");
    const chips = make("div", "cos-card-chips");
    let availabilityClass = "is-preview";
    if (isOwned(item) || isAvailable(item)) availabilityClass = "is-ready";
    else if (item.availability === "coming_soon") availabilityClass = "is-soon";
    chips.append(make("span", "cos-chip " + availabilityClass, availabilityLabel(item)));
    chips.append(make("span", "cos-chip", priceLabel(item)));

    content.append(chips);
    content.append(make("h3", "", item.name || "Unnamed cosmetic"));
    if (item.tagline) content.append(make("p", "cos-card-tagline", item.tagline));
    const description = make("p", "cos-card-description", item.description || "No description supplied.");
    if (description.textContent.length > 150) description.textContent = description.textContent.slice(0, 147).trimEnd() + "…";
    content.append(description);

    const meta = make("div", "cos-card-meta");
    meta.append(make("span", "", item.version ? "Version " + item.version : "Version not supplied"));
    if (item.license) meta.append(make("span", "", item.license));
    content.append(meta);
    card.append(content);
    renderActions(item, card);
    return card;
  }

  function render() {
    renderEquipState();
    const query = (search.value || "").trim().toLowerCase();
    const mode = filter.value || "all";
    const visible = items.filter(function(item) {
      if (query && !getSearchText(item).includes(query)) return false;
      if (mode === "profile" && !isOwned(item)) return false;
      if (mode === "available" && !isAvailable(item)) return false;
      if (mode === "preview" && (isOwned(item) || isAvailable(item))) return false;
      return true;
    });

    grid.replaceChildren();
    if (!visible.length) {
      const empty = make("div", "cosmetics-empty");
      empty.append(make("strong", "", "No matching cosmetics"));
      empty.append(make("p", "", "Try another search or choose a different listing filter."));
      grid.append(empty);
    } else {
      visible.forEach(function(item, index) { grid.append(renderCard(item, index)); });
    }
    grid.setAttribute("aria-busy", "false");
    count.textContent = "Showing " + visible.length + " of " + items.length + " cosmetics";
  }

  async function loadEntitlements() {
    entitlementsUnavailable = false;
    ownedItems = new Map();
    try {
      const response = await fetch("/api/store/entitlements", {
        credentials: "same-origin",
        cache: "no-store",
        headers: { "Accept": "application/json" }
      });
      const data = await response.json().catch(function() { return {}; });
      if (!response.ok) throw new Error(data.error || "Could not check cosmetic ownership.");
      for (const item of Array.isArray(data.items) ? data.items : []) {
        if (item && typeof item.itemId === "string") {
          ownedItems.set(item.itemId, { id: item.itemId, kind: item.kind, name: item.name, purchasedAt: item.purchasedAt });
        }
      }
      if (data.authenticated === false) entitlementsUnavailable = true;
    } catch {
      entitlementsUnavailable = true;
      ownedItems = new Map();
    }
  }

  async function loadCatalog() {
    if (isLoading) return;
    isLoading = true;
    catalogError = "";
    if (refreshButton) {
      refreshButton.disabled = true;
      refreshButton.textContent = "Loading…";
    }
    grid.setAttribute("aria-busy", "true");
    if (feedStatus) {
      feedStatus.textContent = "Checking the online cosmetic catalog…";
      feedStatus.dataset.error = "false";
    }
    try {
      const response = await fetch("/api/store/catalog?kind=cosmetic", {
        credentials: "same-origin",
        cache: "no-store",
        headers: { "Accept": "application/json" }
      });
      const data = await response.json().catch(function() { return {}; });
      if (!response.ok) throw new Error(data.error || "The online catalog returned HTTP " + response.status + ".");
      onlineItems = Array.isArray(data.items) ? data.items.filter(function(item) { return item && item.id && item.name; }).map(function(item) {
        return Object.assign({ localProfile: false }, item);
      }) : [];
    } catch (error) {
      onlineItems = [];
      catalogError = error && error.message ? error.message : "The online catalog could not be reached.";
    } finally {
      isLoading = false;
      if (refreshButton) {
        refreshButton.disabled = false;
        refreshButton.textContent = "↻ Refresh catalog";
      }
    }

    await loadEntitlements();
    items = onlineItems;
    render();
    if (catalogError) {
      feedStatus.textContent = "Online cosmetic catalog unavailable. " + catalogError;
      feedStatus.dataset.error = "true";
    } else if (onlineItems.length) {
      feedStatus.textContent = "Loaded " + onlineItems.length + " cosmetic catalog listing" + (onlineItems.length === 1 ? "" : "s") + ". Owned paid cosmetics show Equip after purchase confirmation.";
      feedStatus.dataset.error = "false";
    } else {
      feedStatus.textContent = "No cosmetic releases are listed yet. Preview products cannot be purchased or equipped until a released package is published.";
      feedStatus.dataset.error = "false";
    }
  }

  search.addEventListener("input", render);
  filter.addEventListener("change", render);
  if (refreshButton) refreshButton.addEventListener("click", loadCatalog);
  if (dialogClose) dialogClose.addEventListener("click", function() { dialog.close(); });
  if (dialog) dialog.addEventListener("click", function(event) { if (event.target === dialog) dialog.close(); });
  window.addEventListener("storage", function(event) {
    if (event.key === EQUIPPED_KEY) render();
  });

  render();
  loadCatalog();
})();