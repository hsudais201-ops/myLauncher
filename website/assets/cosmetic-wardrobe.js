(() => {
  "use strict";
  // Keep one wardrobe and one set of equip controls if markup or scripts are repeated.
  const wardrobes = document.querySelectorAll('section[aria-labelledby="wardrobe-title"]');
  wardrobes.forEach((section, index) => { if (index > 0) section.remove(); });
  if (window.__voidcraftCosmeticWardrobeInitialized) return;
  window.__voidcraftCosmeticWardrobeInitialized = true;
  const INSTALLED = "voidcraft.feature-installed.cosmetic-store";
  const EQUIPPED = "voidcraft.cosmetic-store.equipped";
  const items = [
    {id:"void-aura",name:"Void Aura",description:"Signature violet energy. Made for the dark.",color:"#9c7bff",tag:"SIGNATURE",kind:"AURA"},
    {id:"neon-mint",name:"Neon Mint",description:"Electric mint light with a clean sci-fi feel.",color:"#63e8c1",tag:"NEON",kind:"AURA"},
    {id:"arctic-ice",name:"Arctic Ice",description:"Cool cyan glow inspired by frozen starlight.",color:"#63e8ed",tag:"CHILL",kind:"AURA"},
    {id:"solar-ember",name:"Solar Ember",description:"Warm solar flare tones with a vivid edge.",color:"#ff966b",tag:"FIRE",kind:"AURA"}
  ];
  const grid=document.querySelector("#wardrobe-grid");
  const status=document.querySelector("#wardrobe-status");
  const unequip=document.querySelector("#wardrobe-unequip");
  const installLink=document.querySelector("#wardrobe-install-link");
  if(!grid||!status||!unequip)return;

  const style=document.createElement("style");
  style.textContent=`
    .wardrobe-section{position:relative;overflow:hidden;margin:30px 0;padding:clamp(18px,3vw,32px);border:1px solid #30294b;border-radius:26px;background:radial-gradient(ellipse at 85% 0%,#35245b 0,transparent 43%),linear-gradient(145deg,#151629,#0a0d17)}
    .wardrobe-section:before{content:"";position:absolute;right:-90px;top:90px;width:210px;height:210px;border:1px solid #a58aff20;border-radius:50%;box-shadow:0 0 0 24px #a58aff08,0 0 0 48px #a58aff05;pointer-events:none}
    .wardrobe-head{position:relative;z-index:1;display:flex;gap:20px;align-items:center;justify-content:space-between;flex-wrap:wrap}
    .wardrobe-head h2{font-size:clamp(25px,4vw,36px);letter-spacing:-.04em;margin:8px 0}
    .wardrobe-head p{max-width:650px;font-size:14px}
    .wardrobe-status{position:relative;z-index:1;display:flex;align-items:center;gap:9px;min-height:44px;padding:10px 13px;border:1px solid #292d43;border-radius:12px;background:#080b14aa;font-size:13px;color:#c6cce2}
    .wardrobe-status:before{content:"";width:8px;height:8px;flex:0 0 8px;border-radius:50%;background:#63e8c1;box-shadow:0 0 12px #63e8c188}
    .wardrobe-grid{position:relative;z-index:1;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin:18px 0 4px}
    .wardrobe-item{min-width:0;border:1px solid #292d43;border-radius:18px;padding:12px;background:linear-gradient(160deg,#151a2b,#0a0d16);color:#f4f5ff;transition:transform .18s,border-color .18s,box-shadow .18s}
    .wardrobe-item:hover{transform:translateY(-3px);border-color:var(--wardrobe-color);box-shadow:0 12px 34px color-mix(in srgb,var(--wardrobe-color) 13%,transparent)}
    .wardrobe-item[aria-pressed=true]{border-color:var(--wardrobe-color);box-shadow:inset 0 0 0 1px var(--wardrobe-color),0 0 25px color-mix(in srgb,var(--wardrobe-color) 15%,transparent)}
    .wardrobe-art{height:135px;position:relative;display:grid;place-items:center;overflow:hidden;border-radius:12px;background:radial-gradient(ellipse at center,var(--wardrobe-color)00 0%,#0a0e19 75%),linear-gradient(135deg,#171b2d,#0b0e18);margin-bottom:13px}
    .wardrobe-art:before,.wardrobe-art:after{content:"";position:absolute;width:88px;height:88px;border:1px solid var(--wardrobe-color);border-radius:50%;opacity:.25;transform:rotateX(65deg) scaleY(.45)}
    .wardrobe-art:after{width:116px;height:116px;opacity:.13}
    .wardrobe-orb{position:relative;z-index:1;width:53px;height:53px;border-radius:50%;background:radial-gradient(circle at 32% 25%,#fff 0%,var(--wardrobe-color) 24%,#17182a 78%);border:2px solid #ffffffa8;box-shadow:0 0 18px var(--wardrobe-color),0 0 42px color-mix(in srgb,var(--wardrobe-color) 55%,transparent);animation:wardrobe-float 3s ease-in-out infinite}
    .wardrobe-tag{position:absolute;top:9px;left:9px;z-index:2;border:1px solid #ffffff22;border-radius:7px;padding:3px 7px;background:#080b14b8;color:#d8dcf0;font-size:9px;font-weight:850;letter-spacing:.12em}
    .wardrobe-item h3{font-size:15px;margin:4px 0}.wardrobe-item p{font-size:12px;line-height:1.5;min-height:36px;margin:5px 0 10px;color:#a7aec8}
    .wardrobe-equip{width:100%;margin-top:4px;font:inherit;font-size:12px;padding:10px 12px}
    .wardrobe-equip[aria-pressed=true]{background:linear-gradient(110deg,#24584e,#183b39);border-color:#63e8c1;color:#d9fff3}
    .wardrobe-section [disabled]{opacity:.5;cursor:not-allowed}
    @keyframes wardrobe-float{0%,100%{transform:translateY(0) scale(1)}50%{transform:translateY(-4px) scale(1.04)}}
    @media(max-width:760px){.wardrobe-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.wardrobe-art{height:120px}}
    @media(max-width:400px){.wardrobe-grid{grid-template-columns:1fr}.wardrobe-art{height:145px}}
    @media(prefers-reduced-motion:reduce){.wardrobe-orb,.wardrobe-item{animation:none;transition:none}}
  `;
  document.head.append(style);
  const isInstalled=()=>{try{return localStorage.getItem(INSTALLED)==="true"}catch{return false}};
  const current=()=>{try{return localStorage.getItem(EQUIPPED)||""}catch{return ""}};
  function render(){
    const installed=isInstalled(), equipped=current();
    if(installLink)installLink.hidden=installed;
    unequip.disabled=!installed||!equipped;
    grid.replaceChildren();
    for(const item of items){
      const active=equipped===item.id;
      const card=document.createElement("article");card.className="wardrobe-item";card.style.setProperty("--wardrobe-color",item.color);card.setAttribute("aria-pressed",String(active));
      const art=document.createElement("div");art.className="wardrobe-art";
      const tag=document.createElement("span");tag.className="wardrobe-tag";tag.textContent=item.tag;
      const orb=document.createElement("div");orb.className="wardrobe-orb";orb.setAttribute("aria-hidden","true");art.append(tag,orb);
      const title=document.createElement("h3");title.textContent=item.name;
      const desc=document.createElement("p");desc.textContent=item.description;
      const button=document.createElement("button");button.type="button";button.className="btn wardrobe-equip";button.textContent=active?"✓ Equipped":"Equip style";button.disabled=!installed||active;button.setAttribute("aria-pressed",String(active));
      button.addEventListener("click",()=>{if(!isInstalled()){status.textContent="Install Cosmetic Store from Feature Store before equipping a style.";return}try{localStorage.setItem(EQUIPPED,item.id);status.textContent=item.name+" equipped for this browser profile. This does not change Minecraft in-game visuals.";render()}catch{status.textContent="Could not save your style. Check browser site-storage settings."}});
      card.append(art,title,desc,button);grid.append(card);
    }
    if(!installed) status.textContent="Cosmetic Store isn't installed. Browse the styles, then install it from Feature Store to equip one.";
    else if(equipped){const item=items.find(i=>i.id===equipped);status.textContent=item?item.name+" is equipped on this browser profile. Website preview only; no Minecraft or multiplayer effect.":"Saved style is unknown. Choose a listed style to replace it."}
    else status.textContent="Store ready · Choose a style to equip it on your local browser profile.";
  }
  unequip.addEventListener("click",()=>{try{localStorage.removeItem(EQUIPPED);status.textContent="Style unequipped.";render()}catch{status.textContent="Could not clear equipped style."}});
  window.addEventListener("storage",event=>{if(event.key===INSTALLED||event.key===EQUIPPED)render()});
  render();
})();