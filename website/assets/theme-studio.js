(() => {
  "use strict";
  const KEY = "voidcraft.theme-studio.palette.v1";
  const DEFAULTS = { bg:"#090b13", panel:"#121624", text:"#f4f5ff", muted:"#a7aec8", accent:"#9c7bff", highlight:"#63e8ed" };
  const PRESETS = {
    void: DEFAULTS,
    mint: {bg:"#07130f",panel:"#10231d",text:"#effff8",muted:"#a2c8b9",accent:"#63e8c1",highlight:"#c1ff77"},
    ice: {bg:"#071018",panel:"#101e2a",text:"#f0f8ff",muted:"#a8bdce",accent:"#5f9dff",highlight:"#63e8ed"},
    ember: {bg:"#180d0b",panel:"#291713",text:"#fff4ed",muted:"#d0aaa0",accent:"#ff966b",highlight:"#ffd166"}
  };
  const fields = {bg:document.querySelector("#theme-bg"),panel:document.querySelector("#theme-panel"),text:document.querySelector("#theme-text"),muted:document.querySelector("#theme-muted"),accent:document.querySelector("#theme-accent"),highlight:document.querySelector("#theme-highlight")};
  const preview = document.querySelector("#theme-preview");
  const status = document.querySelector("#theme-status");
  if (!Object.values(fields).every(Boolean) || !preview) return;
  function readSaved() {
    try { const p = JSON.parse(localStorage.getItem(KEY) || "null"); return p && Object.keys(fields).every(k => /^#[0-9a-f]{6}$/i.test(p[k])) ? p : DEFAULTS; }
    catch { return DEFAULTS; }
  }
  function paletteFromControls() { return Object.fromEntries(Object.entries(fields).map(([k,el])=>[k,el.value])); }
  function applyPalette(palette, target) {
    target.style.setProperty("--studio-bg",palette.bg);
    target.style.setProperty("--studio-panel",palette.panel);
    target.style.setProperty("--studio-text",palette.text);
    target.style.setProperty("--studio-muted",palette.muted);
    target.style.setProperty("--studio-accent",palette.accent);
    target.style.setProperty("--studio-highlight",palette.highlight);
    target.style.setProperty("--studio-line",palette.highlight+"66");
  }
  function renderPreview() { applyPalette(paletteFromControls(), preview); if(status) status.textContent="Unsaved changes are preview-only. Save to apply this palette across the website."; }
  function loadPalette(p) { for(const [k,el] of Object.entries(fields)) el.value=p[k]; renderPreview(); }
  function savePalette() {
    const p=paletteFromControls();
    try { localStorage.setItem(KEY,JSON.stringify(p)); window.dispatchEvent(new CustomEvent("voidcraft-theme-updated",{detail:p})); if(status) status.textContent="Theme saved and applied on this browser."; }
    catch { if(status) status.textContent="Could not save theme. Check browser site-storage settings."; }
  }
  Object.values(fields).forEach(el=>el.addEventListener("input",renderPreview));
  document.querySelectorAll("[data-preset]").forEach(btn=>btn.addEventListener("click",()=>loadPalette(PRESETS[btn.dataset.preset]||DEFAULTS)));
  document.querySelector("#theme-save")?.addEventListener("click",savePalette);
  document.querySelector("#theme-save-top")?.addEventListener("click",savePalette);
  document.querySelector("#theme-reset")?.addEventListener("click",()=>{loadPalette(DEFAULTS);savePalette();});
  loadPalette(readSaved());
})();