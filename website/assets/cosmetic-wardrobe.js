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
  const exportButton=document.querySelector("#wardrobe-export");
  if(!grid||!status||!unequip||!exportButton)return;
  const isInstalled=()=>{try{return localStorage.getItem(INSTALLED)==="true"}catch{return false}};
  const current=()=>{try{return localStorage.getItem(EQUIPPED)||""}catch{return ""}};
  function render(){
    const installed=isInstalled(), equipped=current();
    installLink.hidden=installed;
    unequip.disabled=!installed||!equipped;
    exportButton.disabled=!installed||!items.some(item=>item.id===equipped);
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
  exportButton.addEventListener("click",()=>{
    const selected=items.find(item=>item.id===current());
    if(!isInstalled()||!selected){status.textContent="Install Cosmetic Store and equip a supported style before exporting.";return}
    try{
      const config={schemaVersion:1,equipped:selected.id};
      const blob=new Blob([JSON.stringify(config,null,2)+"\n"],{type:"application/json"});
      const url=URL.createObjectURL(blob);
      const link=document.createElement("a");
      link.href=url;
      link.download="voidcraft-cosmetics.json";
      document.body.append(link);
      link.click();
      link.remove();
      setTimeout(()=>URL.revokeObjectURL(url),1000);
      status.textContent="Downloaded the game config. Put it in your active Minecraft game folder's config/ directory as voidcraft-cosmetics.json and install the matching version-specific Fabric mod. This website preview does not modify game files.";
    }catch{status.textContent="Could not export the config from this browser."}
  });
  window.addEventListener("storage",event=>{if(event.key===INSTALLED||event.key===EQUIPPED)render()});
  render();
})();