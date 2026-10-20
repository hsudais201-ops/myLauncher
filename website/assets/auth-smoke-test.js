(async () => {
  const suffix = String(Date.now()).slice(-9);
  const username = "VCSmoke" + suffix;
  const email = "vc-smoke-" + suffix + "@example.invalid";
  const password = "VoidCraft-Smoke-" + suffix + "!Safe";
  const results = { username, steps: {} };

  async function call(url, options) {
    const response = await fetch(url, {
      credentials: "same-origin",
      cache: "no-store",
      headers: { "Content-Type": "application/json", "Accept": "application/json" },
      ...options
    });
    let data = {};
    try { data = await response.json(); } catch {}
    return { status: response.status, ok: response.ok, data };
  }

  try {
    results.steps.health = await call("/api/auth/health", { method: "GET" });
    results.steps.register = await call("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, email, password })
    });

    if (results.steps.register.ok) {
      results.steps.sessionAfterRegister = await call("/api/auth/me", { method: "GET" });
      results.steps.logoutAfterRegister = await call("/api/auth/logout", { method: "POST" });
      results.steps.sessionAfterLogout = await call("/api/auth/me", { method: "GET" });
      results.steps.login = await call("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ identifier: username, password })
      });
      results.steps.sessionAfterLogin = await call("/api/auth/me", { method: "GET" });
      results.steps.finalLogout = await call("/api/auth/logout", { method: "POST" });
    } else {
      results.steps.loginAfterRegisterFailure = await call("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ identifier: username, password })
      });
    }

    const s = results.steps;
    results.summary = {
      healthOk: s.health?.ok && s.health?.data?.ok === true,
      registrationOk: s.register?.status === 201,
      sessionCreated: s.sessionAfterRegister?.data?.authenticated === true,
      logoutOk: s.logoutAfterRegister?.ok,
      loggedOut: s.sessionAfterLogout?.data?.authenticated === false,
      loginOk: s.login?.ok,
      sessionRestored: s.sessionAfterLogin?.data?.authenticated === true,
      finalLogoutOk: s.finalLogout?.ok
    };
  } catch (error) {
    results.exception = String(error && error.name || "Error");
  }

  const root = document.createElement("main");
  root.style.cssText = "font:14px/1.5 system-ui,sans-serif;max-width:1000px;margin:24px auto;padding:20px;color:#111;background:#fff;border-radius:12px";
  const heading = document.createElement("h1");
  heading.textContent = "VoidCraft auth smoke test";
  const pre = document.createElement("pre");
  pre.style.cssText = "white-space:pre-wrap;overflow-wrap:anywhere";
  pre.textContent = JSON.stringify(results, null, 2);
  root.append(heading, pre);
  document.body.replaceChildren(root);
})();