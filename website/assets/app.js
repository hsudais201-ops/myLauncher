document.addEventListener("click", (event) => {
  const demo = event.target.closest("[data-demo]");
  if (demo) {
    event.preventDefault();
    const message = document.querySelector("#demo-message");
    if (message) message.textContent = "Demo only: this action is not connected to a backend. No account data was sent.";
  }
});

function createPreviewDialog() {
  let dialog = document.getElementById("image-preview-dialog");
  if (dialog) return dialog;

  dialog = document.createElement("dialog");
  dialog.id = "image-preview-dialog";
  dialog.className = "image-preview-dialog";
  dialog.setAttribute("aria-label", "Image preview");
  const panel = document.createElement("div");
  panel.className = "preview-dialog-panel";
  const head = document.createElement("div");
  head.className = "preview-dialog-head";
  const title = document.createElement("h2");
  title.id = "image-preview-title";
  title.textContent = "Image preview";
  const close = document.createElement("button");
  close.type = "button";
  close.className = "preview-dialog-close";
  close.dataset.closeImagePreview = "true";
  close.textContent = "Close ✕";
  close.setAttribute("aria-label", "Close image preview");
  head.append(title, close);

  const image = document.createElement("img");
  image.className = "preview-dialog-image";
  image.alt = "";
  image.decoding = "async";
  const caption = document.createElement("p");
  caption.className = "preview-dialog-caption";
  caption.textContent = "Illustrative VoidCraft concept preview.";
  panel.append(head, image, caption);
  dialog.append(panel);
  document.body.append(dialog);
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) dialog.close();
  });
  return dialog;
}

document.addEventListener("click", (event) => {
  const trigger = event.target.closest("[data-image-preview]");
  if (trigger) {
    const dialog = createPreviewDialog();
    const image = dialog.querySelector(".preview-dialog-image");
    const title = dialog.querySelector("#image-preview-title");
    const caption = dialog.querySelector(".preview-dialog-caption");
    const src = trigger.getAttribute("data-image-preview");
    if (!src) return;
    image.src = src;
    image.alt = trigger.getAttribute("data-image-title") || "VoidCraft image preview";
    title.textContent = trigger.getAttribute("data-image-title") || "Image preview";
    caption.textContent = trigger.dataset.previewCaption || "Illustrative concept preview — not evidence of a completed feature.";
    if (!dialog.open) dialog.showModal();
    return;
  }
  if (event.target.closest("[data-close-image-preview]")) {
    const dialog = document.getElementById("image-preview-dialog");
    if (dialog?.open) dialog.close();
  }
});

function safeInternalReturnPath(candidate, fallback = "profile.html") {
  if (typeof candidate !== "string" || !candidate.trim()) return fallback;
  try {
    const target = new URL(candidate, window.location.origin);
    if (target.origin !== window.location.origin || target.pathname === "/signin.html") return fallback;
    return target.pathname + target.search + target.hash;
  } catch {
    return fallback;
  }
}

function requestedAccountReturnPath() {
  return safeInternalReturnPath(new URLSearchParams(window.location.search).get("next"), "profile.html");
}

const authForm = document.getElementById("auth-form");
if (authForm) {
  const modeButtons = [...document.querySelectorAll("[data-auth-mode]")];
  const loginFields = document.getElementById("login-fields");
  const registerFields = document.getElementById("register-fields");
  const identifier = document.getElementById("auth-identifier");
  const username = document.getElementById("auth-username");
  const email = document.getElementById("auth-email");
  const password = document.getElementById("auth-password");
  const confirmField = document.getElementById("confirm-field");
  const confirm = document.getElementById("auth-confirm");
  const submit = document.getElementById("auth-submit");
  const feedback = document.getElementById("auth-feedback");
  let mode = "login";

  function setMode(nextMode) {
    mode = nextMode === "register" ? "register" : "login";
    const isRegister = mode === "register";
    loginFields.hidden = isRegister;
    identifier.disabled = isRegister;
    identifier.required = !isRegister;
    registerFields.hidden = !isRegister;
    username.disabled = !isRegister;
    username.required = isRegister;
    email.disabled = !isRegister;
    email.required = isRegister;
    confirmField.hidden = !isRegister;
    confirm.disabled = !isRegister;
    confirm.required = isRegister;
    password.autocomplete = isRegister ? "new-password" : "current-password";
    confirm.autocomplete = "new-password";
    password.minLength = isRegister ? 12 : 1;
    modeButtons.forEach((button) => {
      const active = button.dataset.authMode === mode;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-pressed", String(active));
    });
    submit.textContent = isRegister ? "Create account" : "Sign in";
    feedback.textContent = "";
    feedback.className = "form-feedback";
  }

  modeButtons.forEach((button) => button.addEventListener("click", () => setMode(button.dataset.authMode)));

  async function apiJson(url, options) {
    const response = await fetch(url, {
      credentials: "same-origin",
      cache: "no-store",
      headers: { "Content-Type": "application/json", "Accept": "application/json" },
      ...options
    });
    const contentType = response.headers.get("Content-Type") || "";
    let data = {};
    if (contentType.toLowerCase().includes("application/json")) {
      try { data = await response.json(); } catch {}
    }
    return { response, data };
  }

  authForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    feedback.textContent = "";
    feedback.className = "form-feedback";
    const pass = password.value;
    let payload;
    let url;
    if (mode === "register") {
      if (pass.length < 12) {
        feedback.textContent = "Choose a password with at least 12 characters.";
        feedback.classList.add("is-error");
        password.focus();
        return;
      }
      if (pass !== confirm.value) {
        feedback.textContent = "The passwords do not match.";
        feedback.classList.add("is-error");
        confirm.focus();
        return;
      }
      payload = { username: username.value.trim(), email: email.value.trim(), password: pass };
      url = "/api/auth/register";
    } else {
      payload = { identifier: identifier.value.trim(), password: pass };
      url = "/api/auth/login";
    }

    submit.disabled = true;
    submit.textContent = mode === "register" ? "Creating account…" : "Signing in…";
    try {
      const { response, data } = await apiJson(url, { method: "POST", body: JSON.stringify(payload) });
      if (!response.ok) {
        const fallback = response.status === 404
          ? "The account API route was not found in this deployment (HTTP 404). Try refreshing the page."
          : response.status >= 500
            ? "The account server returned an internal error (HTTP " + response.status + "). Please retry in a moment."
            : "The request was rejected (HTTP " + response.status + "). Check the details and try again.";
        feedback.textContent = data.error || fallback;
        if (data.code) feedback.textContent += " [" + data.code + "]";
        feedback.classList.add("is-error");
        return;
      }
      feedback.textContent = data.message || "Success. Opening your profile…";
      feedback.classList.add("is-success");
      password.value = "";
      confirm.value = "";
      window.location.assign(requestedAccountReturnPath());
    } catch {
      feedback.textContent = "The account service could not be reached. Check your connection and retry.";
      feedback.classList.add("is-error");
    } finally {
      submit.disabled = false;
      submit.textContent = mode === "register" ? "Create account" : "Sign in";
    }
  });

  setMode("login");
  fetch("/api/auth/health", { credentials: "same-origin", cache: "no-store", headers: { "Accept": "application/json" } })
    .then(async (response) => {
      let data = {};
      if ((response.headers.get("Content-Type") || "").toLowerCase().includes("application/json")) {
        try { data = await response.json(); } catch {}
      }
      if (!response.ok || data.ok !== true) {
        feedback.textContent = data.code
          ? "Account service check failed: " + data.code + "."
          : "Account service check failed (HTTP " + response.status + "). The form will show a detailed error if you submit.";
        feedback.className = "form-feedback is-info";
      }
    })
    .catch(() => {
      feedback.textContent = "Couldn't reach the account health check. Check your connection; a submit will show the HTTP result.";
      feedback.className = "form-feedback is-info";
    });
  fetch("/api/auth/me", { credentials: "same-origin", cache: "no-store", headers: { "Accept": "application/json" } })
    .then((response) => response.ok ? response.json() : null)
    .then((data) => {
      if (data?.authenticated && data.user) {
        window.location.replace(requestedAccountReturnPath());
      }
    })
    .catch(() => {});
}

const profileCard = document.getElementById("profile-card");
if (profileCard) {
  function makeElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== undefined) element.textContent = text;
    return element;
  }

  async function loadProfile() {
    try {
      const response = await fetch("/api/auth/me", { credentials: "same-origin", cache: "no-store", headers: { "Accept": "application/json" } });
      const data = await response.json().catch(() => ({}));
      profileCard.replaceChildren();
      if (!response.ok || data.error) {
        profileCard.append(makeElement("h2", "", "Account service unavailable"), makeElement("p", "", data.error || "Please try again later."));
        const retry = makeElement("button", "btn secondary", "Try again");
        retry.type = "button";
        retry.addEventListener("click", loadProfile);
        profileCard.append(retry);
        return;
      }
      if (!data.authenticated || !data.user) {
        profileCard.append(makeElement("h2", "", "You're not signed in"), makeElement("p", "", "Sign in or create a website account to see your profile here."));
        const link = makeElement("a", "btn", "Sign in / Create account →");
        link.href = "signin.html";
        profileCard.append(link);
        return;
      }
      const user = data.user;
      const heading = makeElement("h2", "");
      heading.append(document.createTextNode("Welcome, "));
      heading.append(makeElement("span", "profile-username", user.username));
      if (user.role === "owner") {
        heading.append(document.createTextNode(" "));
        const crown = makeElement("span", "owner-crown", "👑");
        crown.setAttribute("role", "img");
        crown.setAttribute("aria-label", "Owner");
        crown.title = "VoidCraft website owner";
        heading.append(crown);
      }
      const email = makeElement("p", "profile-email", user.email);
      const details = makeElement("div", "profile-meta", user.role === "owner" ? "VoidCraft website owner" : "VoidCraft website member");
      if (user.role === "owner") details.append(makeElement("span", "owner-pill", "OWNER"));
      const created = makeElement("p", "profile-meta", "Created: " + (user.createdAt ? new Date(user.createdAt).toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" }) : "Unknown"));
      const actions = makeElement("div", "profile-actions");
      const signout = makeElement("button", "btn secondary profile-signout", "Sign out");
      signout.type = "button";
      signout.addEventListener("click", async () => {
        signout.disabled = true;
        signout.textContent = "Signing out…";
        try {
          const result = await fetch("/api/auth/logout", { method: "POST", credentials: "same-origin", cache: "no-store", headers: { "Accept": "application/json" } });
          if (!result.ok) throw new Error("Sign-out failed");
          window.location.assign("signin.html");
        } catch {
          signout.disabled = false;
          signout.textContent = "Try sign out again";
        }
      });
      actions.append(signout);
      profileCard.append(heading, email, details, created, actions);
    } catch {
      profileCard.replaceChildren(makeElement("h2", "", "Could not load your profile"), makeElement("p", "", "Check your connection and refresh this page."));
    }
  }
  loadProfile();
}


// Reflect the current website session in every page header.
(() => {
  const signInLink = document.querySelector('.account-links a[href="signin.html"]');
  const profileLink = document.querySelector('.account-links a[href="profile.html"]');

  function showSignedOut() {
    if (signInLink) {
      signInLink.hidden = false;
      signInLink.textContent = "Sign in";
      signInLink.setAttribute("aria-label", "Sign in or create a website account");
      signInLink.removeAttribute("title");
    }
    if (profileLink) {
      profileLink.hidden = false;
      profileLink.textContent = "Profile";
      profileLink.setAttribute("aria-label", "Website profile");
    }
  }

  function showSignedIn(user) {
    if (signInLink) signInLink.hidden = true;
    if (!profileLink) return;
    profileLink.hidden = false;
    profileLink.replaceChildren(document.createTextNode(user.username));
    if (user.role === "owner") {
      profileLink.append(document.createTextNode(" "));
      const crown = document.createElement("span");
      crown.className = "owner-crown";
      crown.textContent = "👑";
      crown.setAttribute("role", "img");
      crown.setAttribute("aria-label", "Owner");
      crown.title = "VoidCraft website owner";
      profileLink.append(crown);
    }
    profileLink.setAttribute("aria-label", "Profile for " + user.username + (user.role === "owner" ? ", VoidCraft website owner" : ""));
    profileLink.title = user.role === "owner" ? "VoidCraft website owner" : "Open your profile";
  }

  fetch("/api/auth/me", { credentials: "same-origin", cache: "no-store", headers: { "Accept": "application/json" } })
    .then((response) => response.ok ? response.json() : null)
    .then((data) => {
      if (data?.authenticated && data.user) showSignedIn(data.user);
      else showSignedOut();
    })
    .catch(showSignedOut);
})();

// Purchase-only account gate. It does not intercept free-download links or launcher releases.
(() => {
  function showPurchaseFeedback(message) {
    let feedback = document.querySelector("[data-purchase-feedback]");
    if (!feedback) {
      feedback = document.createElement("div");
      feedback.className = "notice";
      feedback.setAttribute("data-purchase-feedback", "");
      feedback.setAttribute("role", "status");
      const main = document.querySelector("main") || document.body;
      main.prepend(feedback);
    }
    feedback.textContent = message;
  }

  async function attemptPurchase(itemId, mayRedirectToSignIn = true) {
    if (!/^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$/.test(itemId || "")) {
      showPurchaseFeedback("This store item does not have a valid purchase ID yet.");
      return;
    }
    try {
      const sessionResponse = await fetch("/api/auth/me", {
        credentials: "same-origin", cache: "no-store", headers: { "Accept": "application/json" }
      });
      const session = sessionResponse.ok ? await sessionResponse.json().catch(() => ({})) : {};
      if (!session?.authenticated || !session.user) {
        if (!mayRedirectToSignIn) {
          showPurchaseFeedback("Your sign-in session is missing. Sign in, then try the purchase again. Free downloads remain available without an account.");
          return;
        }
        const destination = new URL(window.location.href);
        destination.searchParams.set("vc_purchase", itemId);
        const signIn = new URL("signin.html", window.location.href);
        signIn.searchParams.set("next", destination.pathname + destination.search + destination.hash);
        window.location.assign(signIn.pathname + signIn.search);
        return;
      }

      const response = await fetch("/api/store/purchase", {
        method: "POST",
        credentials: "same-origin",
        cache: "no-store",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        body: JSON.stringify({ itemId })
      });
      const result = await response.json().catch(() => ({}));
      if (response.status === 401) {
        if (mayRedirectToSignIn) {
          const signIn = new URL("signin.html", window.location.href);
          signIn.searchParams.set("next", window.location.pathname + window.location.search + window.location.hash);
          window.location.assign(signIn.pathname + signIn.search);
        } else {
          showPurchaseFeedback(result.error || "Sign in is required to purchase this item. Free downloads remain public.");
        }
        return;
      }
      if (!response.ok) {
        showPurchaseFeedback(result.error || "Store purchases are not enabled yet. No charge has been made.");
        return;
      }
      if (result.checkoutUrl) window.location.assign(result.checkoutUrl);
      else showPurchaseFeedback("The store did not return a checkout link. No charge has been made.");
    } catch {
      showPurchaseFeedback("The account or store service could not be reached. No charge has been made.");
    }
  }

  document.addEventListener("click", (event) => {
    const action = event.target.closest("[data-purchase]");
    if (!action || action.disabled || action.getAttribute("aria-disabled") === "true") return;
    event.preventDefault();
    const itemId = action.dataset.purchase || action.dataset.itemId || action.dataset.sku || "";
    attemptPurchase(itemId, true);
  });

  const pending = new URLSearchParams(window.location.search).get("vc_purchase");
  if (pending) {
    const cleanUrl = new URL(window.location.href);
    cleanUrl.searchParams.delete("vc_purchase");
    window.history.replaceState({}, "", cleanUrl.pathname + cleanUrl.search + cleanUrl.hash);
    attemptPurchase(pending, false);
  }
})();


/* Lightweight 3D hover, reveal, and pointer ambience. No external libraries. */
(() => {
  const reducedMotion = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const finePointer = window.matchMedia && window.matchMedia("(hover: hover) and (pointer: fine)").matches;

  const revealTargets = document.querySelectorAll(".hero-copy, .hero-visual, .card, .auth-panel, .auth-aside, .profile-card");
  if (reducedMotion) {
    revealTargets.forEach((node) => node.classList.add("is-visible"));
  } else if ("IntersectionObserver" in window) {
    const revealObserver = new IntersectionObserver((entries, observer) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.08, rootMargin: "0px 0px -24px 0px" });
    revealTargets.forEach((node, index) => {
      node.classList.add("reveal-up");
      node.style.setProperty("--reveal-delay", Math.min(index % 6, 5) * 65 + "ms");
      revealObserver.observe(node);
    });
  }

  if (reducedMotion || !finePointer) return;
  document.querySelectorAll(".feature-card, .cosmetic-card, .auth-panel, .auth-aside, .profile-card").forEach((card) => {
    let rect = null;
    let frame = 0;
    card.classList.add("has-tilt");
    card.addEventListener("pointerenter", () => {
      rect = card.getBoundingClientRect();
      card.classList.add("is-tilting");
    });
    card.addEventListener("pointermove", (event) => {
      if (event.pointerType === "touch") return;
      if (!rect || !frame) rect = card.getBoundingClientRect();
      if (frame) cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const x = (event.clientX - rect.left) / Math.max(1, rect.width);
        const y = (event.clientY - rect.top) / Math.max(1, rect.height);
        const ry = (x - 0.5) * 5;
        const rx = (0.5 - y) * 4;
        card.style.setProperty("--tilt-x", rx.toFixed(2) + "deg");
        card.style.setProperty("--tilt-y", ry.toFixed(2) + "deg");
        card.style.setProperty("--spot-x", (x * 100).toFixed(1) + "%");
        card.style.setProperty("--spot-y", (y * 100).toFixed(1) + "%");
        frame = 0;
      });
    });
    card.addEventListener("pointerleave", () => {
      if (frame) cancelAnimationFrame(frame);
      frame = 0;
      rect = null;
      card.classList.remove("is-tilting");
      card.style.setProperty("--tilt-x", "0deg");
      card.style.setProperty("--tilt-y", "0deg");
      card.style.setProperty("--spot-x", "50%");
      card.style.setProperty("--spot-y", "50%");
    });
  });
})();


/* Online storefront: public catalog, item details and free download handling. */
(() => {
  const grid = document.querySelector("[data-store-grid]");
  if (!grid) return;

  const kind = grid.dataset.storeKind;
  const searchInput = document.getElementById("store-search");
  const availabilityFilter = document.getElementById("store-filter");
  const resultCount = document.getElementById("store-result-count");
  const refreshButton = document.querySelector("[data-store-refresh]");
  let items = [];
  let loadError = "";

  function safeAssetUrl(value) {
    if (typeof value !== "string" || !value.trim()) return "";
    const source = value.trim();
    try {
      const url = new URL(source, window.location.origin);
      if (url.protocol !== "https:" && url.origin !== window.location.origin) return "";
      if (!["https:", "http:"].includes(url.protocol)) return "";
      return url.href;
    } catch {
      return "";
    }
  }

  function itemAvailability(item) {
    if (item.availability === "available" && item.packageUrl) return "Available now";
    if (item.availability === "coming_soon") return "Coming soon";
    if (item.availability === "paused") return "Temporarily unavailable";
    return "Preview";
  }

  function itemAvailabilityClass(item) {
    if (item.availability === "available" && item.packageUrl) return "is-available";
    if (item.availability === "coming_soon") return "is-coming-soon";
    return "is-preview";
  }

  function itemPrice(item) {
    if (item.priceCents === null || item.priceCents === undefined) return "Preview · not for sale";
    if (Number(item.priceCents) === 0) return item.packageUrl && item.availability === "available" ? "Free download" : "Free · package not available";
    try {
      return new Intl.NumberFormat("en-US", { style: "currency", currency: item.currency || "USD" }).format(Number(item.priceCents) / 100);
    } catch {
      return "$" + (Number(item.priceCents) / 100).toFixed(2);
    }
  }

  function make(tag, className, content) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (content !== undefined) element.textContent = content;
    return element;
  }

  function actionFor(item, detailMode = false) {
    if (item.availability === "available" && item.packageUrl && Number(item.priceCents) === 0) {
      const link = make("a", "btn store-action-primary", "Download free");
      link.href = safeAssetUrl(item.packageUrl);
      link.rel = "noopener noreferrer";
      link.setAttribute("aria-label", "Download free package: " + item.name);
      return link;
    }
    if (item.availability === "available" && item.packageUrl && Number(item.priceCents) > 0) {
      const buy = make("button", "btn store-action-primary", "Purchase");
      buy.type = "button";
      buy.dataset.purchase = item.id;
      buy.setAttribute("aria-label", "Purchase " + item.name);
      return buy;
    }
    const button = make("button", detailMode ? "btn secondary" : "btn store-action-secondary", "View details");
    button.type = "button";
    button.dataset.storeDetails = item.id;
    return button;
  }

  function renderCatalog() {
    const needle = (searchInput?.value || "").trim().toLowerCase();
    const status = availabilityFilter?.value || "all";
    const visible = items.filter((item) => {
      const searchable = [item.name, item.tagline, item.description, item.license, item.compatibility, item.creatorUsername].filter(Boolean).join(" ").toLowerCase();
      if (needle && !searchable.includes(needle)) return false;
      if (status === "available" && !(item.availability === "available" && item.packageUrl)) return false;
      if (status === "preview" && item.availability !== "preview") return false;
      if (status === "coming_soon" && item.availability !== "coming_soon") return false;
      return true;
    });

    grid.replaceChildren();
    if (!visible.length) {
      const empty = make("div", "empty-state");
      empty.append(make("strong", "", loadError ? "Catalog could not be loaded" : "No matching listings"),
        make("p", "", loadError || "Try a different search or availability filter."));
      if (loadError) {
        const retry = make("button", "btn secondary", "Try again");
        retry.type = "button";
        retry.addEventListener("click", loadCatalog);
        empty.append(retry);
      }
      grid.append(empty);
      if (resultCount) resultCount.textContent = loadError ? "Catalog connection needs attention." : "Showing 0 listings.";
      return;
    }

    visible.forEach((item, index) => {
      const card = make("article", "store-card");
      card.style.setProperty("--store-stagger", Math.min(index, 8) * 45 + "ms");
      const media = make("div", "store-card-media");
      const imgUrl = safeAssetUrl(item.imageUrl);
      if (imgUrl) {
        const img = make("img", "store-card-image");
        img.src = imgUrl;
        img.alt = item.name + " preview";
        img.loading = "lazy";
        img.decoding = "async";
        img.addEventListener("error", () => {
          img.remove();
          media.classList.add("store-card-media-fallback");
          media.append(make("span", "store-fallback-monogram", item.name.slice(0, 1).toUpperCase()));
        }, { once: true });
        media.append(img);
      } else {
        media.classList.add("store-card-media-fallback");
        media.append(make("span", "store-fallback-monogram", item.name.slice(0, 1).toUpperCase()));
      }
      const overlay = make("span", "store-card-kind", kind === "cosmetic" ? "COSMETIC" : "FEATURE");
      media.append(overlay);

      const content = make("div", "store-card-content");
      const chips = make("div", "store-card-chips");
      chips.append(make("span", "store-status-chip " + itemAvailabilityClass(item), itemAvailability(item)));
      chips.append(make("span", "store-price-chip", itemPrice(item)));
      const title = make("h3", "store-card-title", item.name);
      const tagline = make("p", "store-card-tagline", item.tagline || "");
      const desc = make("p", "store-card-description", item.description || "");
      desc.textContent = desc.textContent.length > 175 ? desc.textContent.slice(0, 172).trimEnd() + "…" : desc.textContent;
      const meta = make("div", "store-card-meta");
      meta.append(make("span", "", item.version ? "Version " + item.version : "Version details on listing"));
      if (item.license) meta.append(make("span", "", "License: " + item.license));
      const actions = make("div", "store-card-actions");
      actions.append(actionFor(item));
      const details = make("button", "store-icon-action", "Details ↗");
      details.type = "button";
      details.dataset.storeDetails = item.id;
      details.setAttribute("aria-label", "View full details for " + item.name);
      actions.append(details);
      content.append(chips, title, tagline, desc, meta, actions);
      card.append(media, content);
      grid.append(card);
    });

    if (resultCount) resultCount.textContent = "Showing " + visible.length + " of " + items.length + " catalog listings · live catalog";
  }

  function createDetailDialog() {
    let dialog = document.getElementById("store-item-dialog");
    if (dialog) return dialog;
    dialog = document.createElement("dialog");
    dialog.className = "store-detail-dialog";
    dialog.id = "store-item-dialog";
    dialog.setAttribute("aria-label", "Store item details");
    const panel = make("div", "store-detail-panel");
    const head = make("div", "store-detail-head");
    const title = make("h2", "store-detail-title", "Listing details");
    title.id = "store-detail-title";
    const close = make("button", "store-detail-close", "Close ✕");
    close.type = "button";
    close.addEventListener("click", () => dialog.close());
    head.append(title, close);
    const body = make("div", "store-detail-body");
    body.id = "store-detail-body";
    panel.append(head, body);
    dialog.append(panel);
    dialog.addEventListener("click", (event) => {
      if (event.target === dialog) dialog.close();
    });
    document.body.append(dialog);
    return dialog;
  }

  function showItemDetails(id) {
    const item = items.find((entry) => entry.id === id);
    if (!item) return;
    const dialog = createDetailDialog();
    const title = dialog.querySelector(".store-detail-title");
    const body = dialog.querySelector("#store-detail-body");
    title.textContent = item.name;
    body.replaceChildren();
    const banner = make("div", "store-detail-banner");
    const imageSrc = safeAssetUrl(item.imageUrl);
    if (imageSrc) {
      const image = make("img", "store-detail-image");
      image.src = imageSrc;
      image.alt = item.name + " preview";
      image.addEventListener("error", () => image.remove(), { once: true });
      banner.append(image);
    }
    const details = make("div", "store-detail-copy");
    const chips = make("div", "store-card-chips");
    chips.append(make("span", "store-status-chip " + itemAvailabilityClass(item), itemAvailability(item)));
    chips.append(make("span", "store-price-chip", itemPrice(item)));
    details.append(chips, make("p", "store-detail-tagline", item.tagline || ""));
    const description = make("p", "store-detail-description", item.description || "No additional description provided.");
    details.append(description);
    banner.append(details);
    body.append(banner);

    const facts = make("dl", "store-detail-facts");
    const factsList = [
      ["Type", item.kind === "cosmetic" ? "Cosmetic" : "Launcher feature"],
      ["Version", item.version || "Not specified"],
      ["Compatibility", item.compatibility || "Not supplied"],
      ["Permissions", item.permissions || "Not supplied"],
      ["License", item.license || "Not supplied"],
      ["Creator", item.creatorUsername || "VoidCraft catalog"],
      ["Availability", itemAvailability(item)]
    ];
    factsList.forEach(([label, value]) => {
      facts.append(make("dt", "", label), make("dd", "", value));
    });
    body.append(facts);
    const links = make("div", "store-detail-links");
    const sourceUrl = safeAssetUrl(item.sourceUrl);
    if (sourceUrl) {
      const source = make("a", "text-link", "Source repository ↗");
      source.href = sourceUrl;
      source.target = "_blank";
      source.rel = "noopener noreferrer";
      links.append(source);
    }
    body.append(links);
    const actions = make("div", "store-detail-actions");
    actions.append(actionFor(item, true));
    body.append(actions);
    if (item.priceCents !== null && Number(item.priceCents) > 0) {
      body.append(make("p", "store-detail-payment-note", "Paid checkout is not active yet. This button will not charge you; the payment provider is not configured."));
    } else if (!(item.availability === "available" && item.packageUrl)) {
      body.append(make("p", "store-detail-payment-note", "This is a catalog preview only. A real package download is not available yet."));
    }
    if (!dialog.open) dialog.showModal();
  }

  document.addEventListener("click", (event) => {
    const button = event.target.closest("[data-store-details]");
    if (!button) return;
    event.preventDefault();
    showItemDetails(button.dataset.storeDetails);
  });

  async function loadCatalog() {
    loadError = "";
    grid.replaceChildren(make("div", "empty-state", "Loading the online catalog…"));
    if (resultCount) resultCount.textContent = "Connecting to the online catalog…";
    if (refreshButton) {
      refreshButton.disabled = true;
      refreshButton.textContent = "Loading…";
    }
    try {
      const response = await fetch("/api/store/catalog?kind=" + encodeURIComponent(kind), {
        credentials: "same-origin", cache: "no-store", headers: { "Accept": "application/json" }
      });
      const data = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(data.error || "The online catalog returned HTTP " + response.status + ".");
      items = Array.isArray(data.items) ? data.items : [];
    } catch (error) {
      items = [];
      loadError = error?.message || "The online catalog could not be reached.";
    }
    if (refreshButton) {
      refreshButton.disabled = false;
      refreshButton.textContent = "↻ Refresh";
    }
    renderCatalog();
  }

  searchInput?.addEventListener("input", renderCatalog);
  availabilityFilter?.addEventListener("change", renderCatalog);
  refreshButton?.addEventListener("click", loadCatalog);
  loadCatalog();
})();

/* Creator Studio: submit metadata, follow moderation and enable owner review. */
(() => {
  const form = document.getElementById("developer-submission-form");
  const submissionList = document.getElementById("developer-submissions-list");
  if (!form || !submissionList) return;

  const accountState = document.getElementById("developer-account-state");
  const feedback = document.getElementById("developer-form-feedback");
  const submitButton = document.getElementById("dev-submit-button");
  const priceMode = document.getElementById("dev-pricing-mode");
  const priceField = document.getElementById("dev-price-field");
  const priceInput = form.elements.namedItem("priceUsd");
  const ownerSection = document.getElementById("owner-review-section");
  const ownerList = document.getElementById("owner-review-list");
  const historyTitle = document.getElementById("developer-submissions-title");
  const historyDescription = document.getElementById("developer-submissions-description");
  const refreshButton = document.getElementById("dev-refresh-submissions");
  let currentUser = null;

  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  function internalSignInLink(next = "developers.html") {
    const link = el("a", "btn", "Sign in / Create account →");
    link.href = "signin.html?next=" + encodeURIComponent(next);
    return link;
  }

  function showAccountState(title, description, link) {
    accountState.replaceChildren();
    accountState.append(el("div", "dev-state-icon", "↗"));
    const copy = el("div", "developer-account-copy");
    copy.append(el("strong", "", title), el("p", "", description));
    if (link) copy.append(link);
    accountState.append(copy);
  }

  function statusChip(item) {
    const label = item.status === "published" ? (item.availability === "available" ? "Published · package linked" : "Published · preview")
      : item.status === "pending" ? "Pending review"
      : item.status === "rejected" ? "Changes requested"
      : "Archived";
    const className = item.status === "published" ? "is-available" : item.status === "pending" ? "is-coming-soon" : "is-preview";
    return el("span", "store-status-chip " + className, label);
  }

  function safeExternalLink(url, title) {
    if (typeof url !== "string" || !url.trim()) return null;
    try {
      const parsed = new URL(url);
      if (parsed.protocol !== "https:" || parsed.username || parsed.password) return null;
      const link = el("a", "text-link", title);
      link.href = parsed.href;
      link.target = "_blank";
      link.rel = "noopener noreferrer";
      return link;
    } catch {
      return null;
    }
  }

  function buildSubmissionCard(item, ownerReview = false) {
    const card = el("article", "developer-submission-card");
    const top = el("div", "developer-submission-top");
    const info = el("div", "developer-submission-heading");
    info.append(el("div", "eyebrow", item.kind === "cosmetic" ? "COSMETIC SUBMISSION" : "FEATURE SUBMISSION"),
      el("h3", "", item.name), el("p", "", item.tagline || ""));
    const chips = el("div", "developer-submission-chips");
    chips.append(statusChip(item));
    chips.append(el("span", "store-price-chip", item.priceCents === null ? "Not priced" : Number(item.priceCents) === 0 ? "Free" : "$" + (Number(item.priceCents) / 100).toFixed(2) + " USD"));
    top.append(info, chips);
    card.append(top, el("p", "developer-submission-description", item.description || ""));
    const meta = el("div", "developer-submission-meta");
    meta.append(el("span", "", "Version: " + (item.version || "—")),
      el("span", "", "License: " + (item.license || "—")));
    if (ownerReview && item.creatorUsername) meta.append(el("span", "", "Creator: " + item.creatorUsername));
    if (item.createdAt) meta.append(el("span", "", "Submitted: " + new Date(item.createdAt).toLocaleDateString()));
    card.append(meta);
    const links = el("div", "developer-submission-links");
    const source = safeExternalLink(item.sourceUrl, "Source ↗");
    const packageLink = safeExternalLink(item.packageUrl, "Package URL ↗");
    if (source) links.append(source);
    if (packageLink) links.append(packageLink);
    if (links.childNodes.length) card.append(links);
    if (item.reviewNote) card.append(el("p", "developer-review-note", "Review note: " + item.reviewNote));

    if (ownerReview && item.status === "pending") {
      const review = el("div", "developer-review-controls");
      const note = el("input", "developer-review-note-input");
      note.type = "text";
      note.maxLength = 500;
      note.placeholder = "Review note (optional)";
      note.setAttribute("aria-label", "Review note for " + item.name);
      const publish = el("button", "btn", item.packageUrl ? "Approve & publish" : "Publish preview");
      publish.type = "button";
      publish.dataset.reviewAction = "publish";
      publish.dataset.reviewId = item.id;
      const reject = el("button", "btn secondary", "Reject");
      reject.type = "button";
      reject.dataset.reviewAction = "reject";
      reject.dataset.reviewId = item.id;
      review.append(note, publish, reject);
      card.append(review);
    }
    return card;
  }

  async function loadSubmissions() {
    submissionList.replaceChildren(el("div", "empty-state", "Loading your developer workspace…"));
    if (ownerList) ownerList.replaceChildren(el("div", "empty-state", "Loading moderation queue…"));
    if (refreshButton) {
      refreshButton.disabled = true;
      refreshButton.textContent = "Loading…";
    }
    try {
      const response = await fetch("/api/developer/submissions", {
        credentials: "same-origin", cache: "no-store", headers: { "Accept": "application/json" }
      });
      const data = await response.json().catch(() => ({}));
      if (response.status === 401) {
        currentUser = null;
        submissionList.replaceChildren(el("div", "empty-state", "Sign in to see your submissions."));
        if (ownerSection) ownerSection.hidden = true;
        if (submitButton) submitButton.disabled = true;
        showAccountState("Sign in to open Creator Studio", "Create and track submissions with your website account. Free downloads remain public.", internalSignInLink("developers.html"));
        return;
      }
      if (!response.ok) throw new Error(data.error || "Developer API returned HTTP " + response.status + ".");
      currentUser = data.user || null;
      if (submitButton) submitButton.disabled = !currentUser;
      if (currentUser) {
        const roleText = currentUser.role === "owner" ? "Owner access enabled — you can review pending store submissions." : "Signed in as " + currentUser.username + ". Your submissions stay private until approved.";
        showAccountState("Welcome, " + currentUser.username + (currentUser.role === "owner" ? " 👑" : ""), roleText, null);
      }

      const records = Array.isArray(data.items) ? data.items : [];
      submissionList.replaceChildren();
      if (!records.length) submissionList.append(el("div", "empty-state", "You have no submissions yet. Use the form above to submit your first item."));
      else records.forEach((item) => submissionList.append(buildSubmissionCard(item, currentUser?.role === "owner")));

      if (currentUser?.role === "owner" && ownerSection && ownerList) {
        ownerSection.hidden = false;
        const pending = records.filter((item) => item.status === "pending");
        ownerList.replaceChildren();
        if (!pending.length) ownerList.append(el("div", "empty-state", "No submissions are waiting for review."));
        else pending.forEach((item) => ownerList.append(buildSubmissionCard(item, true)));
        if (historyTitle) historyTitle.textContent = "Catalog and submission activity";
        if (historyDescription) historyDescription.textContent = "Owner view includes public catalog entries and submissions across creators. Use Pending submissions below to approve or reject items.";
      } else if (ownerSection) {
        ownerSection.hidden = true;
        if (historyTitle) historyTitle.textContent = "Your submissions";
        if (historyDescription) historyDescription.textContent = "Track review status, owner feedback and publication of your submitted listings.";
      }
    } catch (error) {
      currentUser = null;
      submissionList.replaceChildren(el("div", "empty-state", error?.message || "The developer portal could not be loaded."));
      showAccountState("Developer service unavailable", "Refresh the page or retry when the account service is online.", null);
    } finally {
      if (refreshButton) {
        refreshButton.disabled = false;
        refreshButton.textContent = "↻ Refresh";
      }
    }
  }

  priceMode?.addEventListener("change", () => {
    const paid = priceMode.value === "paid";
    priceField.hidden = !paid;
    priceInput.required = paid;
    if (!paid) priceInput.value = "";
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    feedback.textContent = "";
    feedback.className = "form-feedback";
    if (!currentUser) {
      feedback.textContent = "Sign in to submit an item.";
      feedback.classList.add("is-error");
      window.location.assign("signin.html?next=" + encodeURIComponent("developers.html"));
      return;
    }
    const data = new FormData(form);
    const pricingModeValue = String(data.get("pricingMode") || "free");
    const priceUsd = Number(data.get("priceUsd") || 0);
    const payload = {
      kind: String(data.get("kind") || ""),
      name: String(data.get("name") || "").trim(),
      tagline: String(data.get("tagline") || "").trim(),
      description: String(data.get("description") || "").trim(),
      version: String(data.get("version") || "").trim(),
      license: String(data.get("license") || "").trim(),
      compatibility: String(data.get("compatibility") || "").trim(),
      permissions: String(data.get("permissions") || "").trim(),
      sourceUrl: String(data.get("sourceUrl") || "").trim(),
      packageUrl: String(data.get("packageUrl") || "").trim(),
      imageUrl: String(data.get("imageUrl") || "").trim(),
      pricingMode: pricingModeValue,
      priceCents: Math.round(priceUsd * 100)
    };
    if (pricingModeValue === "paid" && (!Number.isFinite(priceUsd) || priceUsd < 0.5 || priceUsd > 100000)) {
      feedback.textContent = "Choose a price between $0.50 and $100,000.00 USD.";
      feedback.classList.add("is-error");
      return;
    }
    submitButton.disabled = true;
    submitButton.textContent = "Submitting…";
    try {
      const response = await fetch("/api/developer/submissions", {
        method: "POST", credentials: "same-origin", cache: "no-store",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        body: JSON.stringify(payload)
      });
      const result = await response.json().catch(() => ({}));
      if (response.status === 401) {
        window.location.assign("signin.html?next=" + encodeURIComponent("developers.html"));
        return;
      }
      if (!response.ok) throw new Error(result.error || "Submission failed (HTTP " + response.status + ").");
      feedback.textContent = result.message || "Submission received.";
      feedback.classList.add("is-success");
      form.reset();
      priceField.hidden = true;
      priceInput.required = false;
      await loadSubmissions();
    } catch (error) {
      feedback.textContent = error?.message || "The submission could not be saved.";
      feedback.classList.add("is-error");
    } finally {
      submitButton.disabled = !currentUser;
      submitButton.textContent = "Submit for review →";
    }
  });

  ownerList?.addEventListener("click", async (event) => {
    const actionButton = event.target.closest("[data-review-action]");
    if (!actionButton || !currentUser || currentUser.role !== "owner") return;
    const itemId = actionButton.dataset.reviewId;
    const reviewCard = actionButton.closest(".developer-submission-card");
    const note = reviewCard?.querySelector(".developer-review-note-input")?.value || "";
    actionButton.disabled = true;
    actionButton.textContent = actionButton.dataset.reviewAction === "publish" ? "Publishing…" : "Rejecting…";
    try {
      const response = await fetch("/api/developer/review", {
        method: "POST", credentials: "same-origin", cache: "no-store",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        body: JSON.stringify({ id: itemId, action: actionButton.dataset.reviewAction, note })
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.error || "Review action failed (HTTP " + response.status + ").");
      showAccountState("Review saved", result.message || "The submission status was updated.", null);
      await loadSubmissions();
    } catch (error) {
      showAccountState("Review could not be saved", error?.message || "Try again.", null);
      actionButton.disabled = false;
      actionButton.textContent = actionButton.dataset.reviewAction === "publish" ? "Approve & publish" : "Reject";
    }
  });

  refreshButton?.addEventListener("click", loadSubmissions);
  priceField.hidden = priceMode?.value !== "paid";
  if (priceInput) priceInput.required = priceMode?.value === "paid";
  loadSubmissions();
})();
