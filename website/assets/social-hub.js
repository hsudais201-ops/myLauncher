(() => {
  "use strict";
  const API = "https://voidcraft-social-hub-staging.hsudais201.workers.dev";
  const KEY = "voidcraft.socialHub.beta.token";
  const state = { token: getStored(), me: "", mode: "login", friends: [], incoming: [], outgoing: [], blocked: [], selected: "", cursors: Object.create(null), poll: null, busy: false };
  const el = (id) => document.getElementById(id);
  const authView = el("auth-view"), appView = el("app-view"), messages = el("messages");
  function getStored() { try { return sessionStorage.getItem(KEY) || ""; } catch (_) { return ""; } }
  function setToken(token) { state.token = token || ""; try { if (state.token) sessionStorage.setItem(KEY,state.token); else sessionStorage.removeItem(KEY); } catch (_) {} }
  function feedback(id,text,kind) { const n=el(id); n.textContent=text||""; n.className="feedback"+(text?" is-"+(kind||"info"):""); }
  function status(text,online) { el("service-status").textContent=text; el("service-banner").classList.toggle("online",online===true); el("service-banner").classList.toggle("offline",online===false); }
  async function api(path,opts) {
    opts=opts||{}; const authenticated=opts.auth!==false;
    const headers={"Content-Type":"application/json","X-Client-Version":"website-beta-1"};
    if(authenticated&&state.token) headers.Authorization="Bearer "+state.token;
    const res=await fetch(API+path,{method:opts.method||"GET",headers,cache:"no-store",body:opts.body===undefined?undefined:JSON.stringify(opts.body)});
    let data={}; try { data=await res.json(); } catch (_) {}
    if(!res.ok) {
      if(res.status===401&&authenticated&&state.token) showAuth("Your beta session expired. Please sign in again.","info");
      throw new Error(data.error||"The request failed. Please try again.");
    }
    return data;
  }
  function mode(next) {
    state.mode=next==="register"?"register":"login"; const reg=state.mode==="register";
    el("login-tab").classList.toggle("is-active",!reg); el("register-tab").classList.toggle("is-active",reg);
    el("auth-title").textContent=reg?"Create your Social Hub account":"Welcome back";
    el("auth-copy").textContent=reg?"Choose a username and a new, unique password for the beta.":"Sign in with your Social Hub username and password.";
    el("auth-submit").textContent=reg?"Create beta account":"Sign in to Social Hub";
    el("confirm-wrap").hidden=!reg; el("confirm-password").required=reg; el("password").minLength=reg?12:1; el("password").autocomplete=reg?"new-password":"current-password";
    feedback("auth-feedback","","info");
  }
  function stopPolling() { if(state.poll) clearInterval(state.poll); state.poll=null; }
  function showAuth(message,kind) {
    stopPolling(); setToken(""); state.me=""; state.selected=""; state.cursors=Object.create(null);
    authView.hidden=false; appView.hidden=true; if(message) feedback("auth-feedback",message,kind);
  }
  function showApp(username) { state.me=username; el("me-name").textContent=username; authView.hidden=true; appView.hidden=false; feedback("app-feedback","","info"); }
  function btn(label,cls,fn) { const b=document.createElement("button"); b.type="button"; b.className=cls; b.textContent=label; b.addEventListener("click",fn); return b; }
  function empty(container,text) { const p=document.createElement("p"); p.className="list-empty"; p.textContent=text; container.appendChild(p); }
  async function loadFriends() {
    const d=await api("/api/friends");
    state.friends=Array.isArray(d.friends)?d.friends:[]; state.incoming=Array.isArray(d.incoming)?d.incoming:[]; state.outgoing=Array.isArray(d.outgoing)?d.outgoing:[]; state.blocked=Array.isArray(d.blocked)?d.blocked:[];
    if(state.selected&&!state.friends.some(f=>f.username===state.selected)) clearChat();
    renderFriends(); renderRequests();
  }
  function renderFriends() {
    const list=el("friends-list"); list.replaceChildren();
    if(!state.friends.length) empty(list,"No friends yet. Add someone above.");
    state.friends.forEach(f=>{
      const b=btn("", "friend-row"+(state.selected===f.username?" active":""),()=>openChat(f.username));
      const a=document.createElement("span"); a.className="friend-avatar"; a.textContent=f.username.slice(0,1).toUpperCase(); a.setAttribute("aria-hidden","true");
      const n=document.createElement("span"); n.className="friend-copy"; n.textContent=f.username; b.append(a,n); list.appendChild(b);
    });
  }
  function renderRequests() {
    const incoming=el("incoming-list"); incoming.replaceChildren(); el("incoming-count").textContent=String(state.incoming.length);
    if(!state.incoming.length) empty(incoming,"No incoming requests.");
    state.incoming.forEach(r=>{
      const row=document.createElement("div"); row.className="request-row"; const name=document.createElement("span"); name.className="request-name"; name.textContent=r.username;
      const accept=btn("Accept","mini-action",()=>respond(r.id,"accept")); const reject=btn("Decline","mini-action muted",()=>respond(r.id,"reject"));
      accept.setAttribute("aria-label","Accept request from "+r.username); reject.setAttribute("aria-label","Decline request from "+r.username); row.append(name,accept,reject); incoming.appendChild(row);
    });
    const outgoing=el("outgoing-list"); outgoing.replaceChildren(); el("outgoing-count").textContent=String(state.outgoing.length);
    if(!state.outgoing.length) empty(outgoing,"No sent requests.");
    state.outgoing.forEach(r=>{const p=document.createElement("p");p.className="list-empty";p.textContent=r.username+" · pending";outgoing.appendChild(p);});
    const blocked=el("blocked-list"); blocked.replaceChildren();
    if(!state.blocked.length) empty(blocked,"No blocked users.");
    state.blocked.forEach(username=>{const row=document.createElement("div");row.className="request-row";const name=document.createElement("span");name.className="request-name";name.textContent=username;const spacer=document.createElement("span");const undo=btn("Unblock","mini-action muted",()=>unblock(username));row.append(name,spacer,undo);blocked.appendChild(row);});
  }
  async function respond(id,action) { try { await api("/api/friends/requests/"+encodeURIComponent(id)+"/respond",{method:"POST",body:{action}}); feedback("app-feedback",action==="accept"?"Friend request accepted.":"Friend request declined.","success"); await loadFriends(); } catch(e) { feedback("app-feedback",e.message,"error"); } }
  async function unblock(username) { try { await api("/api/blocks/"+encodeURIComponent(username),{method:"DELETE"}); await loadFriends(); feedback("app-feedback",username+" has been unblocked.","success"); } catch(e) { feedback("app-feedback",e.message,"error"); } }
  function clearChat() {
    state.selected=""; stopPolling(); el("chat-title").textContent="Choose a friend"; el("chat-subtitle").textContent="Messages are available between accepted friends.";
    el("chat-empty").hidden=false; messages.hidden=true; el("message-form").hidden=true; el("chat-actions").hidden=true; el("report-form").hidden=true;
  }
  async function openChat(username) {
    state.selected=username; state.cursors[username]=0; renderFriends(); el("chat-title").textContent=username; el("chat-subtitle").textContent="Private messages · refreshes automatically while this page is open";
    el("chat-empty").hidden=true; messages.hidden=false; el("message-form").hidden=false; el("chat-actions").hidden=false; el("report-form").hidden=true; el("report-name").textContent=username; feedback("thread-feedback","","info");
    try { await loadMessages(true); startPolling(); el("message-text").focus(); } catch(e) { feedback("thread-feedback",e.message,"error"); }
  }
  function appendMessage(m) {
    const id=String(m.id||""); if(id&&Array.from(messages.children).some(c=>c.dataset.messageId===id)) return;
    const emptyItem=messages.querySelector(".no-messages"); if(emptyItem) emptyItem.remove();
    const row=document.createElement("article"); row.className="message"+(m.sender===state.me?" mine":""); row.dataset.messageId=id;
    const meta=document.createElement("div"); meta.className="message-meta"; const who=document.createElement("strong"); who.textContent=m.sender===state.me?"You":String(m.sender||"Friend");
    const time=document.createElement("time"); const d=new Date(Number(m.created_at)||Date.now()); time.dateTime=d.toISOString(); time.textContent=d.toLocaleString([],{month:"short",day:"numeric",hour:"2-digit",minute:"2-digit"});
    meta.append(who,time); const bubble=document.createElement("div"); bubble.className="message-bubble"; bubble.textContent=String(m.body||""); row.append(meta,bubble); messages.appendChild(row);
  }
  async function loadMessages(reset) {
    if(!state.selected||!state.token) return; const who=state.selected; const after=reset?0:Math.max(0,(state.cursors[who]||0)-1);
    const d=await api("/api/messages?with="+encodeURIComponent(who)+"&after="+String(after)); if(state.selected!==who) return;
    if(reset) messages.replaceChildren(); const rows=Array.isArray(d.messages)?d.messages:[];
    if(reset&&!rows.length){const p=document.createElement("p");p.className="no-messages";p.textContent="No messages yet. Say hello to start your conversation.";messages.appendChild(p);}
    rows.forEach(appendMessage); state.cursors[who]=Math.max(state.cursors[who]||0,Number(d.nextAfter)||0); if(rows.length) messages.scrollTop=messages.scrollHeight;
  }
  function startPolling() { stopPolling(); state.poll=setInterval(async()=>{if(!state.token||!state.selected||document.visibilityState!=="visible")return;try{await loadMessages(false);feedback("thread-feedback","","info");}catch(e){feedback("thread-feedback",e.message,"error");}},7000); }
  el("login-tab").addEventListener("click",()=>mode("login")); el("register-tab").addEventListener("click",()=>mode("register"));
  el("auth-form").addEventListener("submit",async ev=>{
    ev.preventDefault();if(state.busy)return;const username=el("username").value.trim(),password=el("password").value,reg=state.mode==="register";
    if(!/^[A-Za-z0-9_]{3,24}$/.test(username)){feedback("auth-feedback","Use 3–24 letters, numbers or underscores for your username.","error");return;}
    if(reg&&password.length<12){feedback("auth-feedback","Choose a unique password with at least 12 characters.","error");return;}
    if(reg&&password!==el("confirm-password").value){feedback("auth-feedback","The passwords do not match.","error");return;}
    state.busy=true;el("auth-submit").disabled=true;feedback("auth-feedback",reg?"Creating your beta account…":"Signing in…","info");
    try{const d=await api(reg?"/api/register":"/api/login",{method:"POST",auth:false,body:{username,password}});setToken(d.token);el("password").value="";el("confirm-password").value="";showApp(d.user.username);await loadFriends();feedback("app-feedback",reg?"Account created. Add a friend to begin.":"You are signed in.","success");}
    catch(e){feedback("auth-feedback",e.message,"error");}finally{state.busy=false;el("auth-submit").disabled=false;}
  });
  el("add-friend-form").addEventListener("submit",async ev=>{
    ev.preventDefault();const input=el("friend-name"),username=input.value.trim();
    if(!/^[A-Za-z0-9_]{3,24}$/.test(username)){feedback("friend-feedback","Enter a valid username.","error");return;}
    try{await api("/api/friends/requests",{method:"POST",body:{username}});input.value="";feedback("friend-feedback","Friend request sent to "+username+".","success");await loadFriends();}
    catch(e){feedback("friend-feedback",e.message,"error");}
  });
  el("refresh-friends").addEventListener("click",async()=>{try{await loadFriends();feedback("app-feedback","Friend list refreshed.","success");}catch(e){feedback("app-feedback",e.message,"error");}});
  el("refresh-blocked").addEventListener("click",async()=>{try{await loadFriends();}catch(e){feedback("app-feedback",e.message,"error");}});
  el("refresh-messages").addEventListener("click",async()=>{if(!state.selected){feedback("thread-feedback","Choose a friend first.","info");return;}try{await loadMessages(true);feedback("thread-feedback","Conversation refreshed.","success");}catch(e){feedback("thread-feedback",e.message,"error");}});
  el("message-text").addEventListener("input",()=>{el("message-count").textContent=el("message-text").value.length+" / 2000";});
  el("message-form").addEventListener("submit",async ev=>{
    ev.preventDefault();if(!state.selected||state.busy)return;const text=el("message-text").value.trim();if(!text){feedback("thread-feedback","Type a message before sending.","error");return;}
    const who=state.selected;state.busy=true;el("send-message").disabled=true;
    try{await api("/api/messages",{method:"POST",body:{username:who,text}});el("message-text").value="";el("message-count").textContent="0 / 2000";await loadMessages(true);feedback("thread-feedback","Message sent.","success");}
    catch(e){feedback("thread-feedback",e.message,"error");}finally{state.busy=false;el("send-message").disabled=false;}
  });
  el("logout").addEventListener("click",async()=>{try{if(state.token)await api("/api/logout",{method:"POST",body:{}});}catch(_){}showAuth("You have been signed out.","success");});
  el("remove-friend").addEventListener("click",async()=>{const who=state.selected;if(!who||!confirm("Remove "+who+" from your friends?"))return;try{await api("/api/friends/remove",{method:"POST",body:{username:who}});clearChat();await loadFriends();feedback("app-feedback","Friend removed.","success");}catch(e){feedback("thread-feedback",e.message,"error");}});
  el("block-friend").addEventListener("click",async()=>{const who=state.selected;if(!who||!confirm("Block "+who+"? This also removes the friendship and cancels pending requests."))return;try{await api("/api/blocks",{method:"POST",body:{username:who}});clearChat();await loadFriends();feedback("app-feedback","User blocked. They can no longer message you.","success");}catch(e){feedback("thread-feedback",e.message,"error");}});
  el("open-report").addEventListener("click",()=>{if(!state.selected)return;el("report-name").textContent=state.selected;el("report-form").hidden=false;});
  el("cancel-report").addEventListener("click",()=>{el("report-form").hidden=true;});
  el("report-form").addEventListener("submit",async ev=>{ev.preventDefault();if(!state.selected)return;try{await api("/api/reports",{method:"POST",body:{username:state.selected,category:el("report-category").value,details:el("report-details").value.trim()}});el("report-details").value="";el("report-form").hidden=true;feedback("thread-feedback","Report submitted for moderation review.","success");}catch(e){feedback("thread-feedback",e.message,"error");}});
  async function boot(){
    mode("login");
    try{const h=await api("/api/health",{auth:false});status(h.status==="ok"?"Social Hub API is reachable. Messages require accepted friendships.":"Unexpected API status.",h.status==="ok");}
    catch(_){status("Social Hub API could not be reached. Try again later.",false);}
    if(!state.token){authView.hidden=false;appView.hidden=true;return;}
    try{const d=await api("/api/me");showApp(d.user.username);await loadFriends();feedback("app-feedback","Session restored for this browser tab.","success");}
    catch(_){showAuth("Your beta session is no longer valid. Please sign in again.","info");}
  }
  document.addEventListener("visibilitychange",()=>{if(document.visibilityState==="visible"&&state.token&&state.selected)loadMessages(false).catch(e=>feedback("thread-feedback",e.message,"error"));});
  window.addEventListener("pagehide",stopPolling);boot();
})();