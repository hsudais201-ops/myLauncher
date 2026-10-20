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
      window.location.assign("profile.html");
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
        feedback.textContent = "You're already signed in as " + data.user.username + ". This Sign in page stays available; open Profile to review your session or sign out before switching accounts.";
        feedback.className = "form-feedback is-info";
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
      const heading = makeElement("h2", "", "Welcome, " + user.username);
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


// Keep Sign in visible in every header, including when the visitor has an active session.
(() => {
  const signInLink = document.querySelector(".account-links a[href=\"signin.html\"]");
  const profileLink = document.querySelector(".account-links a[href=\"profile.html\"]");
  if (signInLink) {
    signInLink.hidden = false;
    signInLink.textContent = "Sign in";
    signInLink.setAttribute("aria-label", "Sign in or open the sign-in page");
  }
  fetch("/api/auth/me", { credentials: "same-origin", cache: "no-store", headers: { "Accept": "application/json" } })
    .then((response) => response.ok ? response.json() : null)
    .then((data) => {
      if (!data?.authenticated || !data.user) return;
      if (signInLink) signInLink.title = "This link remains available while signed in";
      if (profileLink) {
        profileLink.textContent = "Profile · " + data.user.username;
        profileLink.setAttribute("aria-label", "Profile for " + data.user.username + (data.user.role === "owner" ? ", website owner" : ""));
      }
    })
    .catch(() => {});
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
