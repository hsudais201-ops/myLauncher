(() => {
  "use strict";
  const INSTALLED = "voidcraft.feature-installed.cosmetic-store";
  const EQUIPPED = "voidcraft.cosmetic-store.equipped";
  const items = [
    {id:"void-aura",name:"Void Aura",description:"Signature violet energy glow.",color:"#9c7bff"},
    {id:"neon-mint",name:"Neon Mint",description:"A bright mint-green profile effect.",color:"#63e8c1"},
    {id:"arctic-ice",name:"Arctic Ice",description:"Cool cyan light for your profile.",color:"#63e8ed"},
    {id:"solar-ember",name:"Solar Ember",description:"Warm orange energy effect.",color:"#ff966b"}
  ];
  const grid=document.querySelector("#wardrobe-grid");
  const status=document.querySelector("#wardrobe-status");
  const unequip=document.querySelector("#wardrobe-unequip");
  const installLink=document.querySelector("#wardrobe-install-link");
  if(!grid||!status||!unequip)return;
  const isInstalled=()=>{try{return localStorage.getItem(INSTALLED)==="true"}catch{return false}};
  const current=()=>{try{return localStorage.getItem(EQUIPPED)||""}catch{return ""}};
  function render(){
    const installed=isInstalled(), equipped=current();
    installLink.hidden=installed;
    unequip.disabled=!installed||!equipped;
    grid.replaceChildren();
    for(const item of items){
      const card=document.createElement("article");card.className="wardrobe-item";card.style.setProperty("--wardrobe-color",item.color);card.setAttribute("aria-pressed",String(equipped===item.id));
      const avatar=document.createElement("div");avatar.className="wardrobe-avatar";
      const orb=document.createElement("div");orb.className="wardrobe-orb";avatar.append(orb);
      const title=document.createElement("h3");title.textContent=item.name;
      const desc=document.createElement("p");desc.textContent=item.description;
      const button=document.createElement("button");button.type="button";button.className="btn";button.textContent=equipped===item.id?"Equipped":"Equip";button.disabled=!installed||equipped===item.id;
      button.addEventListener("click",()=>{if(!isInstalled()){status.textContent="Install Cosmetic Store from the Feature Store before equipping cosmetics.";return}try{localStorage.setItem(EQUIPPED,item.id);status.textContent=item.name+" equipped for your browser profile.";render()}catch{status.textContent="Could not save selection. Check browser site-storage settings."}});
      card.append(avatar,title,desc,button);grid.append(card);
    }
    status.textContent=installed?(equipped?"Equipped: "+(items.find(i=>i.id===equipped)?.name||equipped)+". This is a local website profile effect.":"Choose a style to equip it on this browser profile."):"Cosmetic Store is not installed. You can browse the catalog, but equipping profile effects requires installation.";
  }
  unequip.addEventListener("click",()=>{try{localStorage.removeItem(EQUIPPED);render()}catch{status.textContent="Could not clear equipped cosmetic."}});
  window.addEventListener("storage",event=>{if(event.key===INSTALLED||event.key===EQUIPPED)render()});
  render();
})();