import { useEffect, useMemo, useState } from "react";
import {
  Activity, Archive, BookOpen, Check, ChevronDown, CircleHelp,
  Cpu, FolderOpen, Gamepad2, HardDrive, Home, Layers3, Moon, Package, Palette,
  Play, RefreshCw, Search, Settings, ShieldCheck, Sparkles, Sun, Terminal,
  UserRound, WandSparkles, X
} from "lucide-react";
import { detectJava, desktopAvailable, getVersions, installFabric, installVersion, launchVersion, loadPreferences, savePreferences } from "./api";
import type { JavaRuntime, Preferences, Version } from "./types";

type Page = "Home" | "Play" | "Installations" | "Versions" | "Mod Manager" | "Resource Packs" | "Worlds" | "Skins" | "Java Runtime" | "Settings" | "About";
const nav: { label: Page; icon: typeof Home; group: string }[] = [
  { label: "Home", icon: Home, group: "LIBRARY" },
  { label: "Play", icon: Play, group: "LIBRARY" },
  { label: "Installations", icon: Layers3, group: "LIBRARY" },
  { label: "Versions", icon: Archive, group: "LIBRARY" },
  { label: "Mod Manager", icon: Package, group: "CUSTOMIZE" },
  { label: "Resource Packs", icon: Palette, group: "CUSTOMIZE" },
  { label: "Worlds", icon: FolderOpen, group: "CUSTOMIZE" },
  { label: "Skins", icon: UserRound, group: "CUSTOMIZE" },
  { label: "Java Runtime", icon: Terminal, group: "SYSTEM" },
  { label: "Settings", icon: Settings, group: "SYSTEM" },
  { label: "About", icon: CircleHelp, group: "SYSTEM" }
];
const emptyPrefs: Preferences = { selectedVersion: "", selectedJavaPath: "", ramMb: 2048, gameDirectory: "", accent: "#8b5cf6", theme: "dark" };

function App() {
  const [page, setPage] = useState<Page>("Home");
  const [versions, setVersions] = useState<Version[]>([]);
  const [java, setJava] = useState<JavaRuntime[]>([]);
  const [prefs, setPrefs] = useState<Preferences>(emptyPrefs);
  const [filter, setFilter] = useState<"release" | "snapshot" | "all">("release");
  const [search, setSearch] = useState("");
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState("");
  const [saved, setSaved] = useState(false);
  const [installBusy, setInstallBusy] = useState(false);
  const [installStatus, setInstallStatus] = useState("");
  const [playerName, setPlayerName] = useState("Player");
  const [launchBusy, setLaunchBusy] = useState(false);
  const [launchStatus, setLaunchStatus] = useState("");

  async function refresh() {
    setBusy(true); setError("");
    try {
      const [v, j, p] = await Promise.all([getVersions(), detectJava(), loadPreferences()]);
      setVersions(v); setJava(j); setPrefs(p);
      if (!p.selectedVersion && v.length) setPrefs(old => ({ ...old, selectedVersion: v.find(x => x.type === "release")?.id || v[0].id }));
    } catch (e) { setError(e instanceof Error ? e.message : "Unable to load launcher data."); }
    finally { setBusy(false); }
  }
  useEffect(() => { void refresh(); }, []);
  useEffect(() => {
    document.documentElement.dataset.theme = prefs.theme;
    document.documentElement.style.setProperty("--accent", prefs.accent);
  }, [prefs.theme, prefs.accent]);
  const selected = versions.find(v => v.id === prefs.selectedVersion);
  const shown = useMemo(() => versions.filter(v =>
    (filter === "all" || v.type === filter) && v.id.toLowerCase().includes(search.toLowerCase())
  ).slice(0, 100), [versions, filter, search]);

  async function runInstall() {
    if (!prefs.selectedVersion) { setError("Choose a Minecraft version first."); return; }
    setInstallBusy(true); setInstallStatus("Preparing official game files…"); setError("");
    try {
      const result = await installVersion(prefs.selectedVersion, prefs.gameDirectory);
      setInstallStatus(`Installed ${result.versionId}. Downloaded ${result.downloadedFiles} files (${result.assetsDownloaded} assets); reused ${result.skippedFiles} verified files. Location: ${result.gameDirectory}`);
      if (!prefs.gameDirectory) { const next = { ...prefs, gameDirectory: result.gameDirectory }; setPrefs(next); await savePreferences(next); }
    } catch (e) { setError(e instanceof Error ? e.message : "Minecraft installation failed."); setInstallStatus(""); }
    finally { setInstallBusy(false); }
  }

  async function runFabricInstall() {
    if (!prefs.selectedVersion) { setError("Choose a Minecraft game version first."); return; }
    setInstallBusy(true); setInstallStatus("Finding a compatible Fabric loader…"); setError("");
    try {
      const result = await installFabric(prefs.selectedVersion, prefs.gameDirectory);
      setInstallStatus(`Installed Fabric profile ${result.versionId} for Minecraft ${prefs.selectedVersion}. Downloaded ${result.downloadedFiles} libraries; reused ${result.skippedFiles} verified files. The parent game files are prepared automatically when you launch. Location: ${result.gameDirectory}`);
      if (!prefs.gameDirectory) { const next = { ...prefs, gameDirectory: result.gameDirectory, selectedVersion: result.versionId }; setPrefs(next); await savePreferences(next); }
      else { const next = { ...prefs, selectedVersion: result.versionId }; setPrefs(next); await savePreferences(next); }
    } catch (e) { setError(e instanceof Error ? e.message : "Fabric installation failed."); setInstallStatus(""); }
    finally { setInstallBusy(false); }
  }

  async function runLaunch() {
    if (!prefs.selectedVersion) { setError("Choose a Minecraft version first."); return; }
    setLaunchBusy(true); setLaunchStatus("Starting Minecraft…"); setError("");
    try {
      const pid = await launchVersion(prefs.selectedVersion, prefs.gameDirectory, playerName, prefs.selectedJavaPath || java[0]?.path || "", prefs.ramMb);
      setLaunchStatus(`Minecraft started successfully (process ${pid}).`);
    } catch (e) { setError(e instanceof Error ? e.message : "Minecraft could not be started."); setLaunchStatus(""); }
    finally { setLaunchBusy(false); }
  }

  async function update(next: Preferences) {
    setPrefs(next); setSaved(false);
    try { await savePreferences(next); setSaved(true); window.setTimeout(() => setSaved(false), 1800); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not save preferences."); }
  }

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><span>MF</span></div><div><strong>MF<span>Legend</span></strong><small>JAVA EDITION</small></div></div>
      <div className="profile-card"><div className="avatar">M</div><div className="profile-copy"><strong>Local profile</strong><span>Offline mode</span></div><span className="status-dot" /></div>
      {["LIBRARY", "CUSTOMIZE", "SYSTEM"].map(group => <div className="nav-group" key={group}><div className="nav-heading">{group}</div>{nav.filter(n => n.group === group).map(item => {
        const Icon = item.icon; return <button key={item.label} className={page === item.label ? "nav-item active" : "nav-item"} onClick={() => setPage(item.label)}><Icon size={17}/><span>{item.label}</span>{item.label === "Versions" && <span className="nav-count">{versions.length || "—"}</span>}</button>;
      })}</div>)}
      <div className="sidebar-bottom"><div className="connection"><span className="pulse" />{desktopAvailable ? "Desktop environment" : "Preview mode"}</div><span>v0.1.0 · MFLegend</span></div>
    </aside>
    <main className="main-area">
      <header className="topbar"><div className="breadcrumb"><span>Workspace</span><b>/</b><strong>{page}</strong></div><div className="top-actions"><button className="icon-button" title="Refresh data" onClick={() => void refresh()}><RefreshCw size={17}/></button><button className="icon-button" title="Settings" onClick={() => setPage("Settings")}><Settings size={17}/></button><div className="mini-avatar">M</div></div></header>
      <div className="content">
        {error && <div className="alert"><X size={16}/><span>{error}</span><button onClick={() => setError("")}>Dismiss</button></div>}
        {page === "Home" && <><section className="welcome-row"><div><div className="eyebrow"><Sparkles size={13}/> YOUR MINECRAFT, YOUR WAY</div><h1>Ready for your next<br/><span>adventure?</span></h1><p>Your worlds are waiting. Pick a version and jump back in.</p></div><div className="welcome-art"><div className="art-orb orb-one"/><div className="art-orb orb-two"/><div className="pixel-cube"><div className="cube-top"/><div className="cube-front"/><div className="cube-side"/></div><div className="art-label">BUILD<br/>YOUR WORLD</div></div></section>
          <section className="launch-panel"><div className="launch-info"><div className="section-kicker">QUICK PLAY</div><h2>{selected?.id || "Choose a version"}</h2><p><span className="green-dot"/>{selected ? (selected.type === "release" ? "Stable release" : "Snapshot build") : "No version selected"} <span className="subtle">· Java Edition</span></p><div className="version-select-wrap"><label htmlFor="home-version">GAME VERSION</label><select id="home-version" value={prefs.selectedVersion} onChange={e => void update({...prefs, selectedVersion:e.target.value})}><option value="">Select version</option>{versions.filter(v=>v.type==="release").slice(0,80).map(v=><option key={v.id} value={v.id}>{v.id}</option>)}</select><ChevronDown className="select-chevron" size={15}/></div></div><div className="launch-action"><button className="play-button" onClick={() => setPage("Play")}><Play size={18} fill="currentColor"/> Play Minecraft</button><span>Offline launch · Microsoft sign-in is coming later</span></div></section>
          <div className="section-title-row"><div><div className="section-kicker">AT A GLANCE</div><h2>Your launcher</h2></div><button className="text-button" onClick={()=>setPage("Settings")}>Preferences <span>↗</span></button></div>
          <div className="stats-grid"><div className="stat-card"><div className="stat-icon purple"><Layers3 size={18}/></div><div><span>AVAILABLE VERSIONS</span><strong>{busy ? "…" : versions.length.toLocaleString()}</strong><small>From official Mojang manifest</small></div></div><div className="stat-card"><div className="stat-icon green"><Terminal size={18}/></div><div><span>JAVA RUNTIMES</span><strong>{java.length}</strong><small>{java.length ? "Detected on this device" : "No runtime detected"}</small></div></div><div className="stat-card"><div className="stat-icon blue"><HardDrive size={18}/></div><div><span>MEMORY ALLOCATION</span><strong>{(prefs.ramMb/1024).toFixed(1)} GB</strong><small>Configured for game profile</small></div></div></div>
          <div className="bottom-grid"><div className="info-card"><div className="card-heading"><div><div className="section-kicker">LATEST RELEASE</div><h3>Minecraft Java Edition</h3></div><BookOpen size={19}/></div><p>Browse official releases and snapshots from Mojang's version manifest. Select a version in the Versions section to prepare your installation.</p><button className="outline-button" onClick={()=>setPage("Versions")}>Browse versions <span>→</span></button></div><div className="info-card system-card"><div className="card-heading"><div><div className="section-kicker">SYSTEM CHECK</div><h3>Runtime status</h3></div><Activity size={19}/></div><div className="system-line"><span>Java detection</span><b className={java.length?"good":"muted"}>{java.length ? "Found" : "Not found"}</b></div><div className="system-line"><span>Manifest service</span><b className={versions.length?"good":"muted"}>{versions.length ? "Connected" : busy ? "Checking…" : "Unavailable"}</b></div><button className="text-button" onClick={()=>setPage("Java Runtime")}>View runtimes →</button></div></div>
        </>}
        {page === "Versions" && <><PageHeader eyebrow="GAME LIBRARY" title="Version browser" subtitle="Browse versions published in Mojang's official version manifest."/><div className="toolbar"><div className="search-box"><Search size={16}/><input placeholder="Search versions…" value={search} onChange={e=>setSearch(e.target.value)}/></div><div className="segmented">{(["release","snapshot","all"] as const).map(x=><button className={filter===x?"chosen":""} key={x} onClick={()=>setFilter(x)}>{x==="all"?"All":x[0].toUpperCase()+x.slice(1)}</button>)}</div></div><div className="version-summary"><span>{busy?"Loading…":shown.length+" versions shown"}</span><span>Official metadata · Mojang</span></div><div className="version-list">{shown.map(v=><div className="version-row" key={v.id}><div className="version-icon"><Gamepad2 size={18}/></div><div className="version-name"><strong>{v.id}</strong><span>{new Date(v.releaseTime).toLocaleDateString()} · {v.type.replace("_"," ")}</span></div><span className={"type-pill "+v.type}>{v.type.replace("_"," ")}</span><button className={prefs.selectedVersion===v.id?"selected-version":"choose-version"} onClick={()=>void update({...prefs,selectedVersion:v.id})}>{prefs.selectedVersion===v.id?<><Check size={14}/> Selected</>:"Select"}</button></div>)}</div>{!busy&&!shown.length&&<div className="empty-state">No versions match this search.</div>}</>}
        {page === "Java Runtime" && <><PageHeader eyebrow="ENVIRONMENT" title="Java runtimes" subtitle="Inspect Java installations detected on this Linux device."/><div className="runtime-banner"><div className="runtime-symbol"><Terminal size={24}/></div><div><strong>{java.length ? java.length+" runtime"+(java.length===1?"":"s")+" detected" : "No Java runtime detected"}</strong><span>Java 21 is generally needed for modern Minecraft; older versions may require Java 8 or 17.</span></div><button className="outline-button" onClick={()=>void refresh()}><RefreshCw size={14}/> Rescan</button></div>{java.length?<div className="runtime-list">{java.map(j=><div className="runtime-row" key={j.path}><div className="runtime-symbol small"><Cpu size={18}/></div><div><strong>Java {j.version}</strong><span>{j.path}</span></div><span className="type-pill release">{j.architecture}</span></div>)}</div>:<div className="empty-state"><Terminal size={28}/><strong>Install Java to continue</strong><span>Install a compatible OpenJDK package using your Linux distribution's package manager, then rescan.</span></div>}</>}
        {page === "Settings" && <><PageHeader eyebrow="PREFERENCES" title="Settings" subtitle="Tune MFLegend for your system and play style."/><div className="settings-layout"><div className="settings-card"><div className="settings-heading"><div className="setting-icon"><Terminal size={18}/></div><div><strong>Java runtime</strong><span>Choose which Java installation launches Minecraft.</span></div></div><select className="search-box" value={prefs.selectedJavaPath} onChange={e=>void update({...prefs,selectedJavaPath:e.target.value})}><option value="">Automatic (first detected runtime)</option>{java.map(j=><option key={j.path} value={j.path}>Java {j.version} · {j.path}</option>)}</select><p className="setting-note">{java.length ? "Detected runtimes are listed above. Minecraft version compatibility is checked when you launch." : "No Java runtime detected. Install OpenJDK and use Rescan in Java Runtime."}</p></div><div className="settings-card"><div className="settings-heading"><div className="setting-icon"><HardDrive size={18}/></div><div><strong>Memory allocation</strong><span>Maximum RAM reserved for Minecraft.</span></div></div><div className="range-value">{(prefs.ramMb/1024).toFixed(1)} GB</div><input className="range" type="range" min="1024" max="8192" step="512" value={prefs.ramMb} onChange={e=>void update({...prefs,ramMb:Number(e.target.value)})}/><div className="range-labels"><span>1 GB · Low-memory default</span><span>8 GB</span></div><p className="setting-note">On a 4 GB device, keep allocation near 1–2 GB and leave memory for Linux and the desktop.</p></div><div className="settings-card"><div className="settings-heading"><div className="setting-icon"><Palette size={18}/></div><div><strong>Appearance</strong><span>Choose your preferred interface theme.</span></div></div><div className="choice-row"><button className={prefs.theme==="dark"?"choice active-choice": "choice"} onClick={()=>void update({...prefs,theme:"dark"})}><Moon size={16}/> Dark</button><button className={prefs.theme==="light"?"choice active-choice": "choice"} onClick={()=>void update({...prefs,theme:"light"})}><Sun size={16}/> Light</button></div><label className="field-label">ACCENT COLOR</label><div className="accent-row">{["#8b5cf6","#22c55e","#38bdf8","#f97316","#ec4899"].map(c=><button key={c} aria-label={"Set accent "+c} className={prefs.accent===c?"accent-swatch swatch-active":"accent-swatch"} style={{background:c}} onClick={()=>void update({...prefs,accent:c})}/>)}</div></div><div className="settings-card"><div className="settings-heading"><div className="setting-icon"><FolderOpen size={18}/></div><div><strong>Game directory</strong><span>Current game data location.</span></div></div><div className="path-field">{prefs.gameDirectory || "Default application data directory"}<span>Not configurable yet</span></div></div></div>{saved&&<div className="save-indicator"><Check size={14}/> Preferences saved</div>}</>}
        {page === "Installations" && <><PageHeader eyebrow="PROFILES" title="Installations" subtitle="Install vanilla Minecraft or create a Fabric mod-loader profile."/><div className="feature-notice"><div className="notice-icon"><Layers3 size={22}/></div><h2>{prefs.selectedVersion || "Choose a version"}</h2><p>{prefs.selectedVersion ? "Install the official Mojang game files, or create a Fabric profile for the selected Minecraft version. Fabric libraries are downloaded now; the inherited Minecraft parent files are prepared automatically at launch." : "Select a release or snapshot in the Versions section before installing."}</p><div className="choice-row"><button className="play-button" disabled={installBusy || !prefs.selectedVersion || !desktopAvailable} onClick={() => void runInstall()}>{installBusy ? <><RefreshCw size={16}/> Working…</> : <><HardDrive size={16}/> Install vanilla</>}</button><button className="outline-button" disabled={installBusy || !prefs.selectedVersion || !desktopAvailable} onClick={() => void runFabricInstall()}><Package size={16}/> Install Fabric</button></div><button className="outline-button" onClick={() => setPage("Versions")}>Browse versions</button>{!desktopAvailable && <p>Installation requires the native Tauri desktop app; browser preview cannot write game files.</p>}{installStatus && <p className="setting-note">{installStatus}</p>}</div></>}
        {page === "Play" && <><PageHeader eyebrow="GAME SESSION" title="Play Minecraft" subtitle="Launch an installed vanilla Java Edition version."/><div className="feature-notice"><div className="notice-icon"><Play size={22}/></div><h2>{prefs.selectedVersion || "Choose a version"}</h2><p>Offline launch is available for local worlds and servers that permit offline-mode connections. Microsoft account authentication is not implemented yet, so official online-mode servers will not accept this session.</p><label className="field-label" htmlFor="player-name">PLAYER NAME</label><input id="player-name" className="search-box" value={playerName} maxLength={16} onChange={e=>setPlayerName(e.target.value)} placeholder="Player name"/><p className="setting-note">Java: {java[0]?.version || "System Java (java on PATH)"} · Memory: ${(prefs.ramMb/1024).toFixed(1)} GB</p><button className="play-button" disabled={launchBusy || !desktopAvailable || !prefs.selectedVersion} onClick={()=>void runLaunch()}>{launchBusy?<><RefreshCw size={16}/> Starting…</>:<><Play size={17} fill="currentColor"/> Launch Minecraft</>}</button><button className="outline-button" onClick={()=>setPage("Installations")}>Install or repair version</button>{!desktopAvailable&&<p>Launching requires the native desktop app.</p>}{launchStatus&&<p className="setting-note">{launchStatus}</p>}</div></>}
        {page === "Mod Manager" && <><PageHeader eyebrow="CUSTOMIZE" title="Mod manager" subtitle="Manage mod loaders and per-profile mods."/><FeatureNotice title="Mod support is planned" text="Forge, Fabric, NeoForge, and Quilt integration requires installation profiles and the verified game installation pipeline first." icon={Package}/></>}
        {page === "Resource Packs" && <><PageHeader eyebrow="CUSTOMIZE" title="Resource packs" subtitle="Your textures, your style."/><FeatureNotice title="Resource pack management is not connected" text="Pack discovery, import, enable/disable, and previews will be added after local game-directory management." icon={WandSparkles}/></>}
        {page === "Worlds" && <><PageHeader eyebrow="YOUR BUILDS" title="Worlds" subtitle="Keep your worlds organized and backed up."/><FeatureNotice title="World scanning is not enabled yet" text="World listing, backups, import/export, and safe deletion need a configured game directory." icon={FolderOpen}/></>}
        {page === "Skins" && <><PageHeader eyebrow="IDENTITY" title="Player skins" subtitle="Your character, your look."/><FeatureNotice title="Account connection is not enabled yet" text="Microsoft sign-in and skin changes will use official account flows. Credentials will never be stored as plain text." icon={UserRound}/></>}
        {page === "About" && <><PageHeader eyebrow="ABOUT THE PROJECT" title="MFLegend" subtitle="A lightweight, native Minecraft Java Edition launcher."/><div className="about-card"><div className="brand-mark large-mark">MF</div><h2>MFLegend <span>0.1.0</span></h2><p>Built for clarity, control, and low-memory Linux devices.</p><div className="about-line"><span>Desktop shell</span><b>{desktopAvailable?"Tauri 2":"Web preview"}</b></div><div className="about-line"><span>Version metadata</span><b>Official Mojang manifest</b></div><div className="about-line"><span>License</span><b>Project license to be defined</b></div></div></>}
      </div>
      <footer className="footer"><span>© 2026 MFLegend</span><span><ShieldCheck size={13}/> Built with safety & performance in mind</span></footer>
    </main>
  </div>;
}
function PageHeader({eyebrow,title,subtitle}:{eyebrow:string;title:string;subtitle:string}) { return <div className="page-header"><div className="eyebrow">{eyebrow}</div><h1>{title}</h1><p>{subtitle}</p></div>; }
function FeatureNotice({title,text,icon:Icon}:{title:string;text:string;icon:typeof Home}) { return <div className="feature-notice"><div className="notice-icon"><Icon size={22}/></div><h2>{title}</h2><p>{text}</p><span className="coming-label">IN DEVELOPMENT</span></div>; }
export default App;
