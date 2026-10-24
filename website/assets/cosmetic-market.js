(() => {
  "use strict";

  const EQUIPPED_KEY = "voidcraft.cosmetic-store.equipped";
  const root = document.getElementById("cm-catalog");
  if (!root || window.__voidcraftCosmeticMarketLoaded) return;
  window.__voidcraftCosmeticMarketLoaded = true;

  const search = document.getElementById("cm-search");
  const filter = document.getElementById("cm-filter");
  const sort = document.getElementById("cm-sort");
  const count = document.getElementById("cm-count");
  const feed = document.getElementById("cm-feed");
  const ownership = document.getElementById("cm-ownership");
  const refresh = document.getElementById("cm-refresh");
  const dialog = document.getElementById("cm-details");
  const dialogTitle = document.getElementById("cm-details-title");
  const dialogBody = document.getElementById("cm-details-body");
  const dialogClose = document.getElementById("cm-details-close");
  const reset = document.getElementById("cm-reset");

  let catalog = [];
  let visibleItems = [];
  let entitlements = new Map();
  let catalogLoading = false;
  let catalogLoaded = false;
  let catalogFailed = false;
  let entitlementStatus = "loading";

  const PALETTE = {
    "cosmetic-void-aura": "#a58bff",
    "cosmetic-neon-mint": "#63e8c1",
    "cosmetic-arctic-ice": "#7fc8ff",
    "cosmetic-solar-ember": "#ff9e79",
    "cosmetic-neon-night": "#9c7bff",
    "cosmetic-void-minimal": "#c4c8dc",
    "cosmetic-creator-packs": "#69e8ed"
  };

  function make(tag, className, label) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (label !== undefined && label !== null) node.textContent = String(label);
    return node;
  }

  function localGet(key) {
    try { return localStorage.getItem(key) || ""; } catch { return ""; }
  }

  function getEquippedId() { return localGet(EQUIPPED_KEY); }

  function isOwned(item) {
    return Boolean(item && entitlements.has(item.id) && item.kind === "cosmetic");
  }

  function isAvailable(item) {
    return Boolean(item && item.status === "published" && item.availability === "available" && item.packageAvailable);
  }

  function isFreeDownload(item) {
    return isAvailable(item) && Number(item.priceCents) === 0 && Boolean(safeUrl(item.packageUrl));
  }

  function canPurchase(item) {
    return Boolean(item && item.kind === "cosmetic" && item.status === "published" &&
      item.checkoutEnabled === true && item.availability === "available" &&
      item.packageAvailable && item.priceCents !== null && item.priceCents !== undefined &&
      Number.isSafeInteger(Number(item.priceCents)) && Number(item.priceCents) > 0);
  }

  function safeUrl(value) {
    if (typeof value !== "string" || !value.trim()) return "";
    try {
      const url = new URL(value.trim(), window.location.origin);
      if (url.protocol !== "https:" && url.origin !== window.location.origin) return "";
      if (url.protocol !== "https:" && url.protocol !== "http:") return "";
      return url.href;
    } catch { return ""; }
  }

  function money(item) {
    if (isOwned(item)) return "Purchased";
    if (item.priceCents === null || item.priceCents === undefined) return "Preview · not for sale";
    const amount = Number(item.priceCents);
    if (amount === 0) return isFreeDownload(item) ? "Free download" : "Free · no package";
    try {
      return new Intl.NumberFormat("en-US", { style: "currency", currency: item.currency || "USD" }).format(amount / 100);
    } catch { return "$" + (amount / 100).toFixed(2); }
  }

  function statusText(item) {
    if (isOwned(item)) return "Owned · Equip unlocked";
    if (item.availability === "available" && canPurchase(item)) return "Available now";
    if (item.availability === "coming_soon") return "Coming soon";
    if (item.availability === "paused") return "Owned archive";
    return "Preview";
  }

  function colorFor(item) { return item.color || PALETTE[item.id] || "#a58bff"; }

  function paintFeed(message, state) {
    feed.textContent = message;
    feed.dataset.state = state || "info";
  }

  function renderOwnership() {
    ownership.replaceChildren();
    const current = catalog.find(item => item.id === getEquippedId() && isOwned(item));
    const ownedCount = [...entitlements.keys()].filter(id => {
      const item = catalog.find(value => value.id === id);
      return item ? item.kind === "cosmetic" : (entitlements.get(id)?.kind === "cosmetic");
    }).length;

    ownership.dataset.state = current ? "owned" : "default";
    const status = make("div", "cm-ownership-state");
    if (current) status.textContent = current.name + " is equipped on your VoidCraft website profile.";
    else if (entitlementStatus === "signed-out") status.textContent = "Sign in to check purchases. Equip appears only for cosmetics you own.";
    else if (entitlementStatus === "unavailable") status.textContent = "Could not check ownership right now. Refresh to retry; equip stays locked.";
    else if (ownedCount) status.textContent = ownedCount + " owned cosmetic" + (ownedCount === 1 ? "" : "s") + ". Choose Equip on an owned card.";
    else status.textContent = "No purchased cosmetics found. Purchase a released cosmetic to unlock Equip.";

    ownership.append(status);
    if (current) {
      const clear = make("button", "cm-unequip", "Unequip");
      clear.type = "button";
      clear.addEventListener("click", () => {
        try {
          localStorage.removeItem(EQUIPPED_KEY);
          render();
          paintFeed("Cosmetic unequipped from your website profile.", "success");
        } catch {
          paintFeed("Unable to clear the equipped style. Check your browser storage settings.", "error");
        }
      });
      ownership.append(clear);
    } else if (catalogLoaded && !catalogFailed && entitlementStatus === "signed-in" && getEquippedId()) {
      // The saved cosmetic is no longer owned; do not allow it to remain active.
      try { localStorage.removeItem(EQUIPPED_KEY); } catch {}
    }
  }

  function art(item) {
    const box = make("div", "cm-art");
    box.style.setProperty("--cm-color", colorFor(item));
    box.append(make("span", "cm-art-label", isOwned(item) ? "OWNED COSMETIC" : "COSMETIC PREVIEW"));

    const imageUrl = safeUrl(item.imageUrl);
    if (imageUrl) {
      const img = make("img", "cm-art-img");
      img.src = imageUrl;
      img.alt = item.name + " preview";
      img.loading = "lazy";
      img.decoding = "async";
      img.addEventListener("error", () => img.remove(), { once: true });
      box.append(img);
    } else {
      box.append(make("span", "cm-art-orbit"), make("span", "cm-art-core"));
    }
    return box;
  }

  function statusClass(item) {
    if (isOwned(item)) return "is-owned";
    if (canPurchase(item) || isFreeDownload(item)) return "is-ready";
    if (item.availability === "coming_soon") return "is-coming";
    return "is-preview";
  }

  function details(item) {
    dialogTitle.textContent = item.name || "Cosmetic details";
    dialogBody.replaceChildren();
    dialogBody.append(make("p", "", item.description || item.tagline || "No description supplied."));
    const facts = make("dl", "cm-dialog-facts");
    [
      ["Listing status", statusText(item)],
      ["Price", money(item)],
      ["Version", item.version || "Not supplied"],
      ["Compatibility", item.compatibility || "VoidCraft website profile preview"],
      ["License", item.license || "Not supplied"],
      ["Creator", item.creatorUsername || "VoidCraft catalog"]
    ].forEach(([key, value]) => facts.append(make("dt", "", key), make("dd", "", value)));
    dialogBody.append(facts);

    if (isOwned(item)) {
      dialogBody.append(make("p", "", "Your account owns this cosmetic. Equip applies it to this website profile only. Minecraft in-game rendering and multiplayer visibility are not part of this website store."));
    } else if (!canPurchase(item) && !isFreeDownload(item)) {
      dialogBody.append(make("p", "", "This listing is a preview. The purchase action will unlock when a real release package and price are published and secure checkout is configured."));
    }

    const sourceUrl = safeUrl(item.sourceUrl);
    if (sourceUrl) {
      const source = make("a", "", "View source / creator page ↗");
      source.href = sourceUrl;
      source.target = "_blank";
      source.rel = "noopener noreferrer";
      dialogBody.append(source);
    }
    if (typeof dialog.showModal === "function" && !dialog.open) dialog.showModal();
    else dialog.setAttribute("open", "");
  }

  function actionButtons(item, card) {
    const actions = make("div", "cm-actions");
    if (isOwned(item)) {
      const equipped = getEquippedId() === item.id;
      const equip = make("button", "btn", equipped ? "✓ Equipped" : "Equip");
      equip.type = "button";
      equip.disabled = equipped;
      equip.setAttribute("aria-pressed", String(equipped));
      equip.addEventListener("click", () => {
        if (!isOwned(item)) {
          paintFeed("This cosmetic is not owned by this account.", "error");
          return;
        }
        try {
          localStorage.setItem(EQUIPPED_KEY, item.id);
          render();
          paintFeed(item.name + " equipped on your website profile.", "success");
        } catch {
          paintFeed("Unable to save your selection. Check browser storage settings.", "error");
        }
      });
      actions.append(equip);
    } else {
      if (isFreeDownload(item)) {
        const download = make("a", "btn", "Download free");
        download.href = safeUrl(item.packageUrl);
        download.rel = "noopener noreferrer";
        download.setAttribute("aria-label", "Download " + item.name);
        actions.append(download);
      }

      const add = make("button", "btn secondary", "Add to cart");
      add.type = "button";
      add.setAttribute("aria-label", "Add " + item.name + " to cart");
      const buy = make("button", "btn", "Buy now");
      buy.type = "button";
      buy.setAttribute("aria-label", "Buy " + item.name + " now");
      if (canPurchase(item)) {
        add.dataset.cartAdd = item.id;
        buy.dataset.buyNow = item.id;
      } else {
        add.disabled = true;
        buy.disabled = true;
        const why = item.checkoutEnabled !== true
          ? "Secure checkout is not configured yet; no charge will be made."
          : (item.availability === "coming_soon"
            ? "This cosmetic is coming soon."
            : (isFreeDownload(item) ? "This item is free; use Download free." : "Preview only; no released paid package."));
        add.title = why;
        buy.title = why;
      }
      actions.append(add, buy);
    }

    const showDetails = make("button", "cm-detail", "Details");
    showDetails.type = "button";
    showDetails.addEventListener("click", () => details(item));
    actions.append(showDetails);
    card.append(actions);
  }

  function card(item, index) {
    const active = isOwned(item) && getEquippedId() === item.id;
    const node = make("article", "cm-card" + (isOwned(item) ? " is-owned" : "") + (active ? " is-equipped" : ""));
    node.style.setProperty("--cm-delay", Math.min(index, 8) * 30 + "ms");
    node.style.setProperty("--cm-color", colorFor(item));
    node.append(art(item));

    const body = make("div", "cm-card-body");
    const chips = make("div", "cm-chip-row");
    chips.append(make("span", "cm-chip " + statusClass(item), statusText(item)));
    chips.append(make("span", "cm-chip", money(item)));
    body.append(chips, make("h3", "", item.name || "Untitled cosmetic"));
    if (item.tagline) body.append(make("p", "cm-tagline", item.tagline));
    const desc = make("p", "cm-description", item.description || "No description supplied.");
    if (desc.textContent.length > 155) desc.textContent = desc.textContent.slice(0, 152).trimEnd() + "…";
    body.append(desc);
    const meta = make("div", "cm-meta");
    meta.append(make("span", "", item.version ? "Version " + item.version : "Version not listed"));
    if (item.license) meta.append(make("span", "", item.license));
    body.append(meta);
    node.append(body);
    actionButtons(item, node);
    return node;
  }

  function render() {
    renderOwnership();
    const query = (search.value || "").trim().toLowerCase();
    const mode = filter.value || "all";
    const sortMode = sort.value || "featured";

    visibleItems = catalog.filter(item => {
      const searchable = [item.name, item.tagline, item.description, item.compatibility, item.license, item.creatorUsername, statusText(item)].filter(Boolean).join(" ").toLowerCase();
      if (query && !searchable.includes(query)) return false;
      if (mode === "owned" && !isOwned(item)) return false;
      if (mode === "available" && !canPurchase(item) && !isFreeDownload(item) && !isOwned(item)) return false;
      if (mode === "preview" && (canPurchase(item) || isFreeDownload(item) || isOwned(item))) return false;
      return true;
    });

    visibleItems.sort((a, b) => {
      if (sortMode === "name") return String(a.name || "").localeCompare(String(b.name || ""));
      if (sortMode === "price-low") return (Number(a.priceCents) || 0) - (Number(b.priceCents) || 0);
      if (sortMode === "newest") return String(b.createdAt || "").localeCompare(String(a.createdAt || ""));
      const ownOrder = Number(isOwned(b)) - Number(isOwned(a));
      if (ownOrder) return ownOrder;
      const readiness = Number(canPurchase(b) || isFreeDownload(b)) - Number(canPurchase(a) || isFreeDownload(a));
      return readiness || String(a.name || "").localeCompare(String(b.name || ""));
    });

    root.replaceChildren();
    root.setAttribute("aria-busy", "false");
    if (!visibleItems.length) {
      const empty = make("div", "cm-empty");
      empty.append(make("strong", query ? "No matching cosmetics" : (mode === "owned" ? "No owned cosmetics yet" : "No cosmetics in this view")));
      empty.append(make("p", "", query
        ? "Try a different search, or clear the filter to see the full catalog."
        : "Released products appear here when their package, price and store status are ready. Previews are not equipable."));
      root.append(empty);
    } else {
      visibleItems.forEach((item, index) => root.append(card(item, index)));
    }
    count.textContent = "Showing " + visibleItems.length + " of " + catalog.length + " cosmetics";
  }

  async function loadCatalog() {
    if (catalogLoading) return;
    catalogLoading = true;
    catalogFailed = false;
    if (refresh) {
      refresh.disabled = true;
      refresh.textContent = "Refreshing…";
    }
    root.setAttribute("aria-busy", "true");
    paintFeed("Loading catalog and checking your confirmed purchases…", "info");
    try {
      const [catalogResponse, ownedResponse] = await Promise.all([
        fetch("/api/store/catalog?kind=cosmetic", {
          credentials: "same-origin", cache: "no-store", headers: { "Accept": "application/json" }
        }),
        fetch("/api/store/entitlements", {
          credentials: "same-origin", cache: "no-store", headers: { "Accept": "application/json" }
        })
      ]);
      const [catalogData, ownedData] = await Promise.all([
        catalogResponse.json().catch(() => ({})),
        ownedResponse.json().catch(() => ({}))
      ]);
      if (!catalogResponse.ok) throw new Error(catalogData.error || "The cosmetic catalog could not be loaded.");
      catalog = Array.isArray(catalogData.items) ? catalogData.items
        .filter(item => item && typeof item.id === "string" && item.id && typeof item.name === "string")
        .filter(item => item.status === "published")
        .map(item => Object.assign({}, item, { localProfile: false })) : [];

      entitlements = new Map();
      if (ownedResponse.ok && ownedData.authenticated !== false && Array.isArray(ownedData.items)) {
        for (const item of ownedData.items) {
          if (item && typeof item.itemId === "string" && item.kind === "cosmetic") {
            entitlements.set(item.itemId, {
              id: item.itemId, kind: item.kind, name: item.name || item.itemId, purchasedAt: item.purchasedAt || ""
            });
          }
        }
        entitlementStatus = "signed-in";
      } else if (ownedData.authenticated === false || ownedResponse.status === 401) {
        entitlementStatus = "signed-out";
      } else {
        entitlementStatus = "unavailable";
      }

      const listed = new Set(catalog.map(item => item.id));
      for (const owned of entitlements.values()) {
        if (!listed.has(owned.id)) {
          catalog.push({
            id: owned.id,
            name: owned.name,
            kind: "cosmetic",
            tagline: "Previously purchased cosmetic",
            description: "This cosmetic remains owned by your account although it is no longer listed for new purchases.",
            version: "Owned release",
            license: "Account entitlement",
            status: "published",
            availability: "paused",
            priceCents: null,
            currency: "USD",
            packageAvailable: false,
            checkoutEnabled: false,
            localProfile: false,
            purchasedAt: owned.purchasedAt
          });
        }
      }
      catalogLoaded = true;
      render();

      const ownedCount = [...entitlements.values()].length;
      if (catalog.length) {
        paintFeed(ownedCount
          ? "Catalog ready. " + ownedCount + " cosmetic" + (ownedCount === 1 ? "" : "s") + " unlocked on your account. Equip is shown only on owned items."
          : (entitlementStatus === "signed-out"
            ? "Catalog ready. Sign in after purchase to verify ownership and unlock Equip."
            : "Catalog ready. Preview items cannot be equipped; purchase an eligible release to unlock Equip."), "success");
      } else {
        paintFeed("No public cosmetic releases are listed yet. The store will show Equip after a real purchase is confirmed.", "info");
      }
    } catch (error) {
      catalogFailed = true;
      catalogLoaded = false;
      catalog = [];
      render();
      paintFeed(error && error.message ? error.message : "Unable to load the cosmetic store. Refresh to retry.", "error");
    } finally {
      catalogLoading = false;
      if (refresh) {
        refresh.disabled = false;
        refresh.textContent = "↻ Refresh catalog";
      }
    }
  }

  search.addEventListener("input", render);
  filter.addEventListener("change", render);
  sort.addEventListener("change", render);
  refresh.addEventListener("click", loadCatalog);
  reset.addEventListener("click", () => {
    search.value = "";
    filter.value = "all";
    sort.value = "featured";
    render();
  });
  dialogClose.addEventListener("click", () => dialog.close());
  dialog.addEventListener("click", event => { if (event.target === dialog) dialog.close(); });
  window.addEventListener("storage", event => {
    if (event.key === EQUIPPED_KEY) render();
  });

  render();
  loadCatalog();
})();