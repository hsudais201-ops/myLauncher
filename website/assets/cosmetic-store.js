(() => {
  "use strict";

  const INSTALLED_KEY = "voidcraft.feature-installed.cosmetic-store";
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

  const profileEffects = [
    { id: "void-aura", name: "Void Aura", tagline: "The signature VoidCraft look", description: "A violet energy orb with a deep-space glow. This is a browser profile effect, not an in-game Minecraft render.", color: "#a58bff", creatorUsername: "VoidCraft", localProfile: true, version: "1.0", license: "VoidCraft original" },
    { id: "neon-mint", name: "Neon Mint", tagline: "Electric mint, clean sci-fi", description: "A crisp mint glow designed for a bright, modern profile. This is a browser profile effect, not an in-game Minecraft render.", color: "#63e8c1", creatorUsername: "VoidCraft", localProfile: true, version: "1.0", license: "VoidCraft original" },
    { id: "arctic-ice", name: "Arctic Ice", tagline: "Cold light, sharp edges", description: "A cool ice-blue profile glow for a quiet, futuristic style. This is a browser profile effect, not an in-game Minecraft render.", color: "#7fc8ff", creatorUsername: "VoidCraft", localProfile: true, version: "1.0", license: "VoidCraft original" },
    { id: "solar-ember", name: "Solar Ember", tagline: "Warm light with a bold edge", description: "An ember-orange profile glow inspired by a solar flare. This is a browser profile effect, not an in-game Minecraft render.", color: "#ff9e79", creatorUsername: "VoidCraft", localProfile: true, version: "1.0", license: "VoidCraft original" }
  ];

  let onlineItems = [];
  let catalogError = "";
  let isLoading = false;
  let items = profileEffects.slice();

  function make(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = String(text);
    return node;
  }

  function getLocal(key) {
    try { return localStorage.getItem(key) || ""; } catch { return ""; }
  }

  function storeInstalled() {
    return getLocal(INSTALLED_KEY) === "true";
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
    if (item.localProfile) return "Profile effect";
    if (isAvailable(item)) return "Available now";
    if (item.availability === "coming_soon") return "Coming soon";
    if (item.availability === "paused") return "Temporarily unavailable";
    return "Preview";
  }

  function priceLabel(item) {
    if (item.localProfile) return "Browser profile effect";
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
    const installed = storeInstalled();
    const equippedId = getEquipped();
    const equippedItem = profileEffects.find(function(item) { return item.id === equippedId; });

    if (!installed) {
      message.append(make("span", "", "Cosmetic Store is not installed. Install it to enable profile effects."));
      const link = make("a", "", "Install store");
      link.href = "feature-store.html#cosmetic-store-feature-title";
      message.append(link);
    } else if (equippedItem) {
      message.append(make("span", "", equippedItem.name + " is equipped on this browser profile. Website-only preview."));
    } else if (equippedId) {
      message.append(make("span", "", "Your saved style is no longer listed. Choose another profile effect."));
    } else {
      message.append(make("span", "", "No profile effect equipped. Choose one card below to customize this browser profile."));
    }
    equipState.append(message);

    if (installed && equippedId) {
      const clear = make("button", "cosmetics-unequip", "Unequip");
      clear.type = "button";
      clear.addEventListener("click", function() {
        try {
          localStorage.removeItem(EQUIPPED_KEY);
          render();
          feedStatus.textContent = "Profile effect unequipped.";
          feedStatus.dataset.error = "false";
        } catch {
          feedStatus.textContent = "Could not clear the saved effect. Check browser storage settings.";
          feedStatus.dataset.error = "true";
        }
      });
      equipState.append(clear);
    }
  }

  function renderArt(item) {
    const art = make("div", "cos-card-art");
    art.style.setProperty("--cos-color", item.color || "#a58bff");
    const tag = make("span", "cos-card-kind", item.localProfile ? "VOIDCRAFT ORIGINAL" : "COSMETIC LISTING");
    art.append(tag);

    const imageUrl = item.localProfile ? "" : safeAssetUrl(item.imageUrl);
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
    if (item.localProfile) {
      const active = getEquipped() === item.id;
      const equip = make("button", "btn" + (active ? " is-equipped" : ""), active ? "✓ Equipped" : (storeInstalled() ? "Equip style" : "Install to equip"));
      equip.type = "button";
      equip.disabled = !storeInstalled() || active;
      equip.setAttribute("aria-pressed", String(active));
      equip.addEventListener("click", function() {
        if (!storeInstalled()) {
          feedStatus.textContent = "Install Cosmetic Store from Feature Store before equipping a profile effect.";
          feedStatus.dataset.error = "true";
          return;
        }
        try {
          localStorage.setItem(EQUIPPED_KEY, item.id);
          render();
          feedStatus.textContent = item.name + " equipped for this browser profile.";
          feedStatus.dataset.error = "false";
        } catch {
          feedStatus.textContent = "Could not save the style. Check browser site-storage settings.";
          feedStatus.dataset.error = "true";
        }
      });
      actions.append(equip);
    } else if (hasPackageDownload(item)) {
      const download = make("a", "btn", "Download free");
      download.href = safeAssetUrl(item.packageUrl);
      download.rel = "noopener noreferrer";
      download.setAttribute("aria-label", "Download free package: " + item.name);
      actions.append(download);
    } else if (canBuy(item)) {
      const add = make("button", "btn secondary", "Add to cart");
      add.type = "button";
      add.dataset.cartAdd = item.id;
      add.setAttribute("aria-label", "Add " + item.name + " to cart");
      const buy = make("button", "btn", "Buy now");
      buy.type = "button";
      buy.dataset.buyNow = item.id;
      buy.setAttribute("aria-label", "Buy " + item.name + " now");
      actions.append(add, buy);
    } else {
      const state = make("button", "btn secondary", item.availability === "coming_soon" ? "Coming soon" : "Preview only");
      state.type = "button";
      state.disabled = true;
      state.title = "This listing does not have an available package yet.";
      actions.append(state);
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
      ["Type", item.localProfile ? "VoidCraft browser profile effect" : "Online cosmetic listing"],
      ["Status", availabilityLabel(item)],
      ["Version", item.version || "Not specified"],
      ["Compatibility", item.compatibility || (item.localProfile ? "VoidCraft website profile only" : "Not supplied")],
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
    if (item.localProfile) {
      const caveat = make("p", "", "This effect changes the local VoidCraft website profile experience only. It does not render on a Minecraft player model and is not visible to multiplayer players.");
      dialogBody.append(caveat);
    } else if (!hasPackageDownload(item) && !canBuy(item)) {
      dialogBody.append(make("p", "", "This listing is currently informational only. A preview does not mean a Minecraft cosmetic renderer, downloadable asset or multiplayer support is available."));
    }
    if (typeof dialog.showModal === "function" && !dialog.open) dialog.showModal();
    else dialog.setAttribute("open", "");
  }

  function renderCard(item, index) {
    const active = item.localProfile && getEquipped() === item.id;
    const card = make("article", "cos-card" + (active ? " is-equipped" : ""));
    card.style.setProperty("--cos-delay", Math.min(index, 7) * 35 + "ms");
    card.style.setProperty("--cos-color", item.color || "#a58bff");
    card.append(renderArt(item));

    const content = make("div", "cos-card-content");
    const chips = make("div", "cos-card-chips");
    let availabilityClass = "is-preview";
    if (item.localProfile || isAvailable(item)) availabilityClass = "is-ready";
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
    meta.append(make("span", "", item.version ? "Version " + item.version : (item.localProfile ? "Original" : "Version not supplied")));
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
      if (mode === "profile" && !item.localProfile) return false;
      if (mode === "available" && !isAvailable(item)) return false;
      if (mode === "preview" && (item.localProfile || isAvailable(item))) return false;
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
    count.textContent = "Showing " + visible.length + " of " + items.length + " cosmetics";
  }

  async function loadCatalog() {
    if (isLoading) return;
    isLoading = true;
    catalogError = "";
    if (refreshButton) {
      refreshButton.disabled = true;
      refreshButton.textContent = "Loading…";
    }
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

    items = profileEffects.concat(onlineItems);
    render();
    if (catalogError) {
      feedStatus.textContent = "Online catalog unavailable. Built-in profile effects are still ready to browse. " + catalogError;
      feedStatus.dataset.error = "true";
    } else if (onlineItems.length) {
      feedStatus.textContent = "Loaded " + onlineItems.length + " online cosmetic listing" + (onlineItems.length === 1 ? "" : "s") + " alongside the built-in profile effects.";
      feedStatus.dataset.error = "false";
    } else {
      feedStatus.textContent = "Your four VoidCraft profile effects are ready. No online cosmetic releases are listed right now.";
      feedStatus.dataset.error = "false";
    }
  }

  search.addEventListener("input", render);
  filter.addEventListener("change", render);
  if (refreshButton) refreshButton.addEventListener("click", loadCatalog);
  if (dialogClose) dialogClose.addEventListener("click", function() { dialog.close(); });
  if (dialog) dialog.addEventListener("click", function(event) { if (event.target === dialog) dialog.close(); });
  window.addEventListener("storage", function(event) {
    if (event.key === INSTALLED_KEY || event.key === EQUIPPED_KEY) render();
  });

  render();
  loadCatalog();
})();