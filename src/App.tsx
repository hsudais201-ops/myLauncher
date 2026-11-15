import { useEffect, useMemo, useState } from "react";
import { convertFileSrc } from "@tauri-apps/api/core";
import {
  Activity, Archive, BookOpen, Check, ChevronDown, CircleHelp,
  Cpu, FolderOpen, Gamepad2, HardDrive, Home, Layers3, Moon, Package, Palette,
  Play, RefreshCw, Search, Settings, ShieldCheck, Sparkles, Sun, Terminal,
  UserRound, WandSparkles, X
} from "lucide-react";
import { detectJava, desktopAvailable, getVersions, importMod, installFabric, installForge, installVersion, launchVersion, listRunningGames, stopRunningGame, listMods, listResourcePacks, loadPreferences, removeMod, removeResourcePack, savePreferences, setModEnabled, setResourcePackEnabled, importResourcePack, listWorlds, importWorld, exportWorld, backupWorld, removeWorld, listSkins, importSkin, removeSkin, listInstalledVersions } from "./api";
import type { InstallationProfile, JavaRuntime, Preferences, Version } from "./types";
import type { ModEntry, ResourcePackEntry, WorldEntry, SkinEntry, InstalledVersion, RunningGame } from "./api";

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
const emptyPrefs: Preferences = { microsoftClientId: "", installations: [], selectedInstallationId: "", selectedVersion: "", selectedJavaPath: "", ramMb: 2048, gameDirectory: "", accent: "#8b5cf6", theme: "dark" };

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
  const [runningGames, setRunningGames] = useState<RunningGame[]>([]);
  const [gamesBusy, setGamesBusy] = useState(false);
  const [mods, setMods] = useState<ModEntry[]>([]);
  const [modSourcePath, setModSourcePath] = useState("");
  const [modsBusy, setModsBusy] = useState(false);
  const [resourcePacks, setResourcePacks] = useState<ResourcePackEntry[]>([]);
  const [packSourcePath, setPackSourcePath] = useState("");
  const [packsBusy, setPacksBusy] = useState(false);
  const [worlds, setWorlds] = useState<WorldEntry[]>([]);
  const [worldSourcePath, setWorldSourcePath] = useState("");
  const [worldExportPath, setWorldExportPath] = useState("");
  const [worldsBusy, setWorldsBusy] = useState(false);
  const [skins, setSkins] = useState<SkinEntry[]>([]);
  const [skinSourcePath, setSkinSourcePath] = useState("");
  const [skinsBusy, setSkinsBusy] = useState(false);
  const [newProfileName, setNewProfileName] = useState("");
  const [newProfileDirectory, setNewProfileDirectory] = useState("");
  const [installedProfiles, setInstalledProfiles] = useState<InstalledVersion[]>([]);
  const [profilesBusy, setProfilesBusy] = useState(false);

  async function refreshInstalledProfiles() {
    setProfilesBusy(true); setError("");
    try { setInstalledProfiles(await listInstalledVersions(prefs.gameDirectory)); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not scan installed profiles."); }
    finally { setProfilesBusy(false); }
  }

  async function refresh() {
    setBusy(true); setError("");
    try {
      const [v, j, p] = await Promise.all([getVersions(), detectJava(), loadPreferences()]);
      const profiles = p.installations || [];
      const normalized = profiles.length ? p : { ...p, installations: [{ id: "default", name: "Default", versionId: p.selectedVersion, gameDirectory: p.gameDirectory }], selectedInstallationId: "default" };
      setVersions(v); setJava(j); setPrefs(normalized);
      if (!profiles.length) await savePreferences(normalized);
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

  async function refreshMods() {
    setModsBusy(true);
    try { setMods(await listMods(prefs.gameDirectory)); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not read mods folder."); }
    finally { setModsBusy(false); }
  }
  useEffect(() => { if (page === "Mod Manager" && desktopAvailable) void refreshMods(); }, [page, prefs.gameDirectory]);
  useEffect(() => { if (page === "Play" && desktopAvailable) void refreshRunningGames(); }, [page]);

  async function addMod() {
    if (!modSourcePath.trim()) { setError("Enter the full path to a .jar or .zip mod file."); return; }
    setModsBusy(true); setError("");
    try { await importMod(modSourcePath.trim(), prefs.gameDirectory); setModSourcePath(""); await refreshMods(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not import mod."); }
    finally { setModsBusy(false); }
  }
  async function toggleMod(mod: ModEntry) {
    setModsBusy(true); setError("");
    try { await setModEnabled(mod.fileName, !mod.enabled, prefs.gameDirectory); await refreshMods(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not change mod state."); }
    finally { setModsBusy(false); }
  }
  async function deleteMod(mod: ModEntry) {
    if (!window.confirm(`Permanently remove ${mod.fileName}?`)) return;
    setModsBusy(true); setError("");
    try { await removeMod(mod.fileName, prefs.gameDirectory); await refreshMods(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not remove mod."); }
    finally { setModsBusy(false); }
  }

  async function refreshResourcePacks() {
    setPacksBusy(true);
    try { setResourcePacks(await listResourcePacks(prefs.gameDirectory)); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not read resource packs folder."); }
    finally { setPacksBusy(false); }
  }
  useEffect(() => { if (page === "Resource Packs" && desktopAvailable) void refreshResourcePacks(); }, [page, prefs.gameDirectory]);

  async function addResourcePack() {
    if (!packSourcePath.trim()) { setError("Enter the full path to a resource-pack .zip file."); return; }
    setPacksBusy(true); setError("");
    try { await importResourcePack(packSourcePath.trim(), prefs.gameDirectory); setPackSourcePath(""); await refreshResourcePacks(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not import resource pack."); }
    finally { setPacksBusy(false); }
  }
  async function toggleResourcePack(pack: ResourcePackEntry) {
    setPacksBusy(true); setError("");
    try { await setResourcePackEnabled(pack.fileName, !pack.enabled, prefs.gameDirectory); await refreshResourcePacks(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not change resource pack state."); }
    finally { setPacksBusy(false); }
  }
  async function deleteResourcePack(pack: ResourcePackEntry) {
    if (!window.confirm(`Permanently remove ${pack.fileName}?`)) return;
    setPacksBusy(true); setError("");
    try { await removeResourcePack(pack.fileName, prefs.gameDirectory); await refreshResourcePacks(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not remove resource pack."); }
    finally { setPacksBusy(false); }
  }

  async function refreshWorlds() {
    setWorldsBusy(true);
    try { setWorlds(await listWorlds(prefs.gameDirectory)); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not read Minecraft worlds."); }
    finally { setWorldsBusy(false); }
  }
  useEffect(() => { if (page === "Worlds" && desktopAvailable) void refreshWorlds(); }, [page, prefs.gameDirectory]);

  async function addWorld() {
    if (!worldSourcePath.trim()) { setError("Enter the path to an extracted Minecraft world folder."); return; }
    setWorldsBusy(true); setError("");
    try { await importWorld(worldSourcePath.trim(), prefs.gameDirectory); setWorldSourcePath(""); await refreshWorlds(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not import world."); }
    finally { setWorldsBusy(false); }
  }
  async function createWorldBackup(world: WorldEntry) {
    setWorldsBusy(true); setError("");
    try { const path = await backupWorld(world.folderName, prefs.gameDirectory); setInstallStatus(`World backup created: ${path}`); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not back up world."); }
    finally { setWorldsBusy(false); }
  }
  async function exportWorldCopy(world: WorldEntry) {
    if (!worldExportPath.trim()) { setError("Enter a new destination folder path for the world export."); return; }
    setWorldsBusy(true); setError("");
    try { const path = await exportWorld(world.folderName, worldExportPath.trim(), prefs.gameDirectory); setInstallStatus(`World exported to: ${path}`); setWorldExportPath(""); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not export world."); }
    finally { setWorldsBusy(false); }
  }
  async function deleteWorld(world: WorldEntry) {
    if (!window.confirm(`Permanently delete world “${world.displayName}”? This cannot be undone.`)) return;
    setWorldsBusy(true); setError("");
    try { await removeWorld(world.folderName, prefs.gameDirectory); await refreshWorlds(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not delete world."); }
    finally { setWorldsBusy(false); }
  }

  async function refreshSkins() {
    setSkinsBusy(true);
    try { setSkins(await listSkins(prefs.gameDirectory)); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not read skin library."); }
    finally { setSkinsBusy(false); }
  }
  useEffect(() => { if (page === "Skins" && desktopAvailable) void refreshSkins(); }, [page, prefs.gameDirectory]);

  async function addSkin() {
    if (!skinSourcePath.trim()) { setError("Enter the path to a PNG skin file."); return; }
    setSkinsBusy(true); setError("");
    try { await importSkin(skinSourcePath.trim(), prefs.gameDirectory); setSkinSourcePath(""); await refreshSkins(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not import skin."); }
    finally { setSkinsBusy(false); }
  }
  async function deleteSkin(skin: SkinEntry) {
    if (!window.confirm(`Remove ${skin.fileName} from your local skin library?`)) return;
    setSkinsBusy(true); setError("");
    try { await removeSkin(skin.fileName, prefs.gameDirectory); await refreshSkins(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not remove skin."); }
    finally { setSkinsBusy(false); }
  }

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

  async function runForgeInstall() {
    if (!prefs.selectedVersion) { setError("Choose a Minecraft game version first."); return; }
    setInstallBusy(true); setInstallStatus("Preparing Minecraft and finding a compatible Forge installer…"); setError("");
    try {
      const result = await installForge(prefs.selectedVersion, prefs.gameDirectory, prefs.selectedJavaPath || java[0]?.path || "");
      setInstallStatus(`Installed Forge profile ${result.versionId} for Minecraft ${prefs.selectedVersion}. Location: ${result.gameDirectory}`);
      const next = { ...prefs, gameDirectory: result.gameDirectory, selectedVersion: result.versionId };
      setPrefs(next); await savePreferences(next);
    } catch (e) { setError(e instanceof Error ? e.message : "Forge installation failed."); setInstallStatus(""); }
    finally { setInstallBusy(false); }
  }

  async function refreshRunningGames() {
    setGamesBusy(true);
    try { setRunningGames(await listRunningGames()); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not inspect running Minecraft processes."); }
    finally { setGamesBusy(false); }
  }

  async function stopGame(pid: number) {
    setGamesBusy(true); setError("");
    try { await stopRunningGame(pid); await refreshRunningGames(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not stop Minecraft."); }
    finally { setGamesBusy(false); }
  }

  async function runLaunch() {
    if (!prefs.selectedVersion) { setError("Choose a Minecraft version first."); return; }
    setLaunchBusy(true); setLaunchStatus("Starting Minecraft…"); setError("");
    try {
      const pid = await launchVersion(prefs.selectedVersion, prefs.gameDirectory, playerName, prefs.selectedJavaPath || java[0]?.path || "", prefs.ramMb);
      setLaunchStatus(`Minecraft started successfully (process ${pid}).`);
      await refreshRunningGames();
    } catch (e) { setError(e instanceof Error ? e.message : "Minecraft could not be started."); setLaunchStatus(""); }
    finally { setLaunchBusy(false); }
  }

  async function createInstallationProfile() {
    const name = newProfileName.trim();
    const directory = newProfileDirectory.trim();
    if (!name || !directory) { setError("Enter a profile name and an absolute game directory."); return; }
    if (prefs.installations.some(profile => profile.name.toLowerCase() === name.toLowerCase())) { setError("An installation with that name already exists."); return; }
    if (!directory.startsWith("/")) { setError("Use an absolute game directory path."); return; }
    const id = "profile-" + Date.now().toString(36);
    if (prefs.installations.some(profile => profile.gameDirectory.trim() === directory)) { setError("Each installation profile must use a different game directory."); return; }
    const profile: InstallationProfile = { id, name, versionId: prefs.selectedVersion, gameDirectory: directory };
    await update({ ...prefs, installations: [...prefs.installations, profile], selectedInstallationId: id, gameDirectory: directory });
    setNewProfileName(""); setNewProfileDirectory("");
  }
  async function selectInstallationProfile(profile: InstallationProfile) {
    await update({ ...prefs, selectedInstallationId: profile.id, selectedVersion: profile.versionId || prefs.selectedVersion, gameDirectory: profile.gameDirectory });
  }
  async function renameInstallationProfile(profile: InstallationProfile) {
    const name = window.prompt("Rename installation profile:", profile.name)?.trim();
    if (!name || name === profile.name) return;
    if (prefs.installations.some(item => item.id !== profile.id && item.name.toLowerCase() === name.toLowerCase())) { setError("An installation with that name already exists."); return; }
    await update({ ...prefs, installations: prefs.installations.map(item => item.id === profile.id ? { ...item, name } : item) });
  }
  async function duplicateInstallationProfile(profile: InstallationProfile) {
    const name = window.prompt("Name for the duplicate installation:", profile.name + " Copy")?.trim();
    if (!name) return;
    if (prefs.installations.some(item => item.name.toLowerCase() === name.toLowerCase())) { setError("An installation with that name already exists."); return; }
    const directory = window.prompt("Enter a new, separate absolute game directory:", profile.gameDirectory + "-copy")?.trim();
    if (!directory) return;
    if (!directory.startsWith("/") || prefs.installations.some(item => item.gameDirectory.trim() === directory)) { setError("Choose a unique absolute game directory."); return; }
    const duplicate: InstallationProfile = { ...profile, id: "profile-" + Date.now().toString(36), name, gameDirectory: directory };
    await update({ ...prefs, installations: [...prefs.installations, duplicate] });
  }
  async function removeInstallationProfile(profile: InstallationProfile) {
    if (profile.id === prefs.selectedInstallationId) { setError("Select another installation before removing the active profile."); return; }
    if (!window.confirm("Remove this profile from Flame Launcher? Its game files will not be deleted.")) return;
    await update({ ...prefs, installations: prefs.installations.filter(item => item.id !== profile.id) });
  }
  async function update(next: Preferences) {
    const synchronized = { ...next, installations: next.installations.map(profile => profile.id === next.selectedInstallationId ? { ...profile, versionId: next.selectedVersion, gameDirectory: next.gameDirectory } : profile) };
    setPrefs(synchronized); setSaved(false);
    try { await savePreferences(synchronized); setSaved(true); window.setTimeout(() => setSaved(false), 1800); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not save preferences."); }
  }

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><span>MF</span></div><div><strong>Flame <span>Launcher</span></strong><small>MINECRAFT JAVA EDITION</small></div></div>
      <div className="profile-card"><div className="avatar">M</div><div className="profile-copy"><strong>Local profile</strong><span>Offline mode</span></div><span className="status-dot" /></div>
      {["LIBRARY", "CUSTOMIZE", "SYSTEM"].map(group => <div className="nav-group" key={group}><div className="nav-heading">{group}</div>{nav.filter(n => n.group === group).map(item => {
        const Icon = item.icon; return <button key={item.label} className={page === item.label ? "nav-item active" : "nav-item"} onClick={() => setPage(item.label)}><Icon size={17}/><span>{item.label}</span>{item.label === "Versions" && <span className="nav-count">{versions.length || "—"}</span>}</button>;
      })}</div>)}
      <div className="sidebar-bottom"><div className="connection"><span className="pulse" />{desktopAvailable ? "Desktop environment" : "Preview mode"}</div><span>v0.1.0 · Flame Launcher</span></div>
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
        {page === "Settings" && <><PageHeader eyebrow="PREFERENCES" title="Settings" subtitle="Tune Flame Launcher for your system and play style."/><div className="settings-layout"><div className="settings-card"><div className="settings-heading"><div className="setting-icon"><ShieldCheck size={18}/></div><div><strong>Microsoft account setup</strong><span>Prepare Flame Launcher for official Microsoft device-code authentication.</span></div></div><label className="field-label" htmlFor="microsoft-client-id">MICROSOFT ENTRA APPLICATION CLIENT ID</label><input id="microsoft-client-id" className="text-input" value={prefs.microsoftClientId} onChange={e=>void update({...prefs,microsoftClientId:e.target.value.trim()})} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"/><p className="setting-note">Register a public desktop application in Microsoft Entra and enable the device-code flow. Enter its Application (client) ID here. This is a public identifier, not a password. Flame Launcher does not yet perform sign-in or store Microsoft tokens.</p></div><div className="settings-card"><div className="settings-heading"><div className="setting-icon"><Layers3 size={18}/></div><div><strong>Installation profiles</strong><span>Keep versions, mods, worlds, and resource packs in separate game directories.</span></div></div><div className="mod-list">{prefs.installations.map(profile=><div className="mod-row" key={profile.id}><div className="mod-file"><Layers3 size={18}/><div><strong>{profile.name}</strong><small>{profile.versionId || "No version selected"} · {profile.gameDirectory || "Default game directory"}</small></div></div><div className="mod-actions"><button className={profile.id===prefs.selectedInstallationId?"selected-version":"choose-version"} onClick={()=>void selectInstallationProfile(profile)}>{profile.id===prefs.selectedInstallationId?"Active":"Use"}</button><button className="outline-button" onClick={()=>void renameInstallationProfile(profile)}>Rename</button><button className="outline-button" onClick={()=>void duplicateInstallationProfile(profile)}>Duplicate</button>{profile.id!=="default"&&<button className="danger-button" onClick={()=>void removeInstallationProfile(profile)}>Remove</button>}</div></div>)}</div><div className="setting-row"><input className="text-input" value={newProfileName} onChange={e=>setNewProfileName(e.target.value)} placeholder="New profile name"/><input className="text-input" value={newProfileDirectory} onChange={e=>setNewProfileDirectory(e.target.value)} placeholder="/absolute/path/to/game-data"/></div><button className="outline-button" disabled={!newProfileName.trim()||!newProfileDirectory.trim()} onClick={()=>void createInstallationProfile()}>Create isolated profile</button><p className="setting-note">Each profile uses its own game directory. Removing a profile only removes its launcher entry; files are kept untouched.</p></div><div className="settings-card"><div className="settings-heading"><div className="setting-icon"><Terminal size={18}/></div><div><strong>Java runtime</strong><span>Choose which Java installation launches Minecraft.</span></div></div><select className="search-box" value={prefs.selectedJavaPath} onChange={e=>void update({...prefs,selectedJavaPath:e.target.value})}><option value="">Automatic (first detected runtime)</option>{java.map(j=><option key={j.path} value={j.path}>Java {j.version} · {j.path}</option>)}</select><p className="setting-note">{java.length ? "Detected runtimes are listed above. Minecraft version compatibility is checked when you launch." : "No Java runtime detected. Install OpenJDK and use Rescan in Java Runtime."}</p></div><div className="settings-card"><div className="settings-heading"><div className="setting-icon"><HardDrive size={18}/></div><div><strong>Memory allocation</strong><span>Maximum RAM reserved for Minecraft.</span></div></div><div className="range-value">{(prefs.ramMb/1024).toFixed(1)} GB</div><input className="range" type="range" min="1024" max="8192" step="512" value={prefs.ramMb} onChange={e=>void update({...prefs,ramMb:Number(e.target.value)})}/><div className="range-labels"><span>1 GB · Low-memory default</span><span>8 GB</span></div><p className="setting-note">On a 4 GB device, keep allocation near 1–2 GB and leave memory for Linux and the desktop.</p></div><div className="settings-card"><div className="settings-heading"><div className="setting-icon"><Palette size={18}/></div><div><strong>Appearance</strong><span>Choose your preferred interface theme.</span></div></div><div className="choice-row"><button className={prefs.theme==="dark"?"choice active-choice": "choice"} onClick={()=>void update({...prefs,theme:"dark"})}><Moon size={16}/> Dark</button><button className={prefs.theme==="light"?"choice active-choice": "choice"} onClick={()=>void update({...prefs,theme:"light"})}><Sun size={16}/> Light</button></div><label className="field-label">ACCENT COLOR</label><div className="accent-row">{["#8b5cf6","#22c55e","#38bdf8","#f97316","#ec4899"].map(c=><button key={c} aria-label={"Set accent "+c} className={prefs.accent===c?"accent-swatch swatch-active":"accent-swatch"} style={{background:c}} onClick={()=>void update({...prefs,accent:c})}/>)}</div></div><div className="settings-card"><div className="settings-heading"><div className="setting-icon"><FolderOpen size={18}/></div><div><strong>Game directory</strong><span>Current game data location.</span></div></div><input className="text-input" aria-label="Minecraft game directory" value={prefs.gameDirectory} onChange={e=>void update({...prefs,gameDirectory:e.target.value})} placeholder="Leave empty for the default game directory"/><p className="setting-note">Use an absolute folder path. Flame Launcher stores versions, mods, resource packs, worlds, and skins in this directory. Changing it does not move existing files.</p></div></div>{saved&&<div className="save-indicator"><Check size={14}/> Preferences saved</div>}</>}
        {page === "Installations" && <><PageHeader eyebrow="PROFILES" title="Installations" subtitle="Install vanilla Minecraft or create a Fabric mod-loader profile."/><div className="settings-card"><div className="setting-row"><div><strong>Installed profiles</strong><small>Vanilla, Fabric, Forge, and custom version metadata found in this game directory.</small></div><button className="outline-button" disabled={!desktopAvailable || profilesBusy} onClick={()=>void refreshInstalledProfiles()}><RefreshCw size={15}/> Scan</button></div>{profilesBusy ? <p className="setting-note">Scanning installed profiles…</p> : installedProfiles.length ? <div className="mod-list">{installedProfiles.map(profile=><div className="mod-row" key={profile.id}><div className="mod-file"><Layers3 size={18}/><div><strong>{profile.id}</strong><small>{profile.versionType}{profile.inheritsFrom ? " · based on "+profile.inheritsFrom : ""}{profile.mainClass ? " · "+profile.mainClass : ""}</small></div></div><button className={prefs.selectedVersion===profile.id?"selected-version":"choose-version"} onClick={()=>void update({...prefs,selectedVersion:profile.id})}>{prefs.selectedVersion===profile.id?"Selected":"Select"}</button></div>)}</div> : <p className="setting-note">No profiles scanned yet. Select Scan to find installed versions.</p>}</div><div className="feature-notice"><div className="notice-icon"><Layers3 size={22}/></div><h2>{prefs.selectedVersion || "Choose a version"}</h2><p>{prefs.selectedVersion ? "Install the official Mojang game files, or create a Fabric profile for the selected Minecraft version. Fabric libraries are downloaded now; the inherited Minecraft parent files are prepared automatically at launch." : "Select a release or snapshot in the Versions section before installing."}</p><div className="choice-row"><button className="play-button" disabled={installBusy || !prefs.selectedVersion || !desktopAvailable} onClick={() => void runInstall()}>{installBusy ? <><RefreshCw size={16}/> Working…</> : <><HardDrive size={16}/> Install vanilla</>}</button><button className="outline-button" disabled={installBusy || !prefs.selectedVersion || !desktopAvailable} onClick={() => void runFabricInstall()}><Package size={16}/> Install Fabric</button><button className="outline-button" disabled={installBusy || !prefs.selectedVersion || !desktopAvailable} onClick={() => void runForgeInstall()}><Package size={16}/> Install Forge</button></div><button className="outline-button" onClick={() => setPage("Versions")}>Browse versions</button>{!desktopAvailable && <p>Installation requires the native Tauri desktop app; browser preview cannot write game files.</p>}{installStatus && <p className="setting-note">{installStatus}</p>}</div></>}
        {page === "Play" && <><PageHeader eyebrow="GAME SESSION" title="Play Minecraft" subtitle="Launch an installed vanilla Java Edition version."/><div className="feature-notice"><div className="notice-icon"><Play size={22}/></div><h2>{prefs.selectedVersion || "Choose a version"}</h2><p>Offline launch is available for local worlds and servers that permit offline-mode connections. Microsoft account authentication is not implemented yet, so official online-mode servers will not accept this session.</p><label className="field-label" htmlFor="player-name">PLAYER NAME</label><input id="player-name" className="search-box" value={playerName} maxLength={16} onChange={e=>setPlayerName(e.target.value)} placeholder="Player name"/><p className="setting-note">Java: {java[0]?.version || "System Java (java on PATH)"} · Memory: ${(prefs.ramMb/1024).toFixed(1)} GB</p><button className="play-button" disabled={launchBusy || !desktopAvailable || !prefs.selectedVersion} onClick={()=>void runLaunch()}>{launchBusy?<><RefreshCw size={16}/> Starting…</>:<><Play size={17} fill="currentColor"/> Launch Minecraft</>}</button><button className="outline-button" onClick={()=>setPage("Installations")}>Install or repair version</button>{!desktopAvailable&&<p>Launching requires the native desktop app.</p>}{launchStatus&&<p className="setting-note">{launchStatus}</p>}</div><div className="settings-card"><div className="setting-row"><div><strong>Running Minecraft sessions</strong><small>Processes started by this Flame Launcher instance.</small></div><button className="outline-button" disabled={!desktopAvailable || gamesBusy} onClick={()=>void refreshRunningGames()}><RefreshCw size={15}/> Refresh</button></div>{runningGames.length===0?<p className="muted-note">{gamesBusy?"Checking running sessions…":"No tracked Minecraft sessions are running."}</p>:<div className="mod-list">{runningGames.map(game=><div className="mod-row" key={game.pid}><div className="mod-file"><Activity size={18}/><div><strong>{game.versionId} · {game.username}</strong><small>PID {game.pid} · Started {new Date(game.startedAt*1000).toLocaleString()}</small></div></div><div className="mod-actions"><button className="outline-button danger-button" disabled={gamesBusy} onClick={()=>void stopGame(game.pid)}>Stop</button></div></div>)}</div>}<p className="muted-note">Only processes launched by this running Flame Launcher session are tracked. Closing Flame Launcher may leave Minecraft running.</p></div></>}
        {page === "Mod Manager" && <><PageHeader eyebrow="CUSTOMIZE" title="Mod manager" subtitle="Import and manage local mod files for the selected game directory."/><div className="settings-card"><div className="setting-row"><div><strong>Mods folder</strong><small>{prefs.gameDirectory ? `${prefs.gameDirectory}/mods` : "Default Flame Launcher game directory /mods"}</small></div><button className="outline-button" disabled={!desktopAvailable || modsBusy} onClick={() => void refreshMods()}><RefreshCw size={15}/> Refresh</button></div><div className="setting-row"><div><strong>Import a mod</strong><small>Use the full file path to a downloaded .jar or .zip mod.</small></div></div><div className="choice-row"><input className="text-input" value={modSourcePath} onChange={e=>setModSourcePath(e.target.value)} placeholder="/path/to/mod.jar"/><button className="play-button" disabled={!desktopAvailable || modsBusy || !modSourcePath.trim()} onClick={()=>void addMod()}>Import mod</button></div></div><div className="section-title-row"><div><div className="section-kicker">LOCAL MOD FILES</div><h2>{mods.length} mods</h2></div></div>{!desktopAvailable ? <FeatureNotice title="Desktop app required" text="Mod files can only be managed by the native Flame Launcher app." icon={Package}/> : modsBusy && !mods.length ? <p>Reading mods folder…</p> : mods.length === 0 ? <FeatureNotice title="No mods found" text="Import a compatible Fabric or Forge .jar mod to get started." icon={Package}/> : <div className="mod-list">{mods.map(mod=><div className="mod-row" key={mod.fileName}><div className="mod-file"><Package size={18}/><div><strong>{mod.fileName}</strong><small>{(mod.sizeBytes/1024/1024).toFixed(2)} MB · {mod.enabled ? "Enabled" : "Disabled"}</small></div></div><div className="mod-actions"><button className="outline-button" disabled={modsBusy} onClick={()=>void toggleMod(mod)}>{mod.enabled ? "Disable" : "Enable"}</button><button className="outline-button danger-button" disabled={modsBusy} onClick={()=>void deleteMod(mod)}>Remove</button></div></div>)}</div>}<p className="muted-note">Only place mods compatible with your selected loader and Minecraft version in this folder. Removing a mod permanently deletes its file.</p></>}
        {page === "Resource Packs" && <><PageHeader eyebrow="CUSTOMIZE" title="Resource packs" subtitle="Import and manage local Minecraft resource-pack archives."/><div className="settings-card"><div className="setting-row"><div><strong>Resource packs folder</strong><small>{prefs.gameDirectory ? `${prefs.gameDirectory}/resourcepacks` : "Default Flame Launcher game directory /resourcepacks"}</small></div><button className="outline-button" disabled={!desktopAvailable || packsBusy} onClick={() => void refreshResourcePacks()}><RefreshCw size={15}/> Refresh</button></div><div className="setting-row"><div><strong>Import a resource pack</strong><small>Select the full path to a downloaded .zip pack archive.</small></div></div><div className="choice-row"><input className="text-input" value={packSourcePath} onChange={e=>setPackSourcePath(e.target.value)} placeholder="/path/to/resource-pack.zip"/><button className="play-button" disabled={!desktopAvailable || packsBusy || !packSourcePath.trim()} onClick={()=>void addResourcePack()}>Import pack</button></div></div><div className="section-title-row"><div><div className="section-kicker">LOCAL RESOURCE PACKS</div><h2>{resourcePacks.length} packs</h2></div></div>{!desktopAvailable ? <FeatureNotice title="Desktop app required" text="Resource packs can only be managed by the native Flame Launcher app." icon={WandSparkles}/> : packsBusy && !resourcePacks.length ? <p>Reading resource packs folder…</p> : resourcePacks.length === 0 ? <FeatureNotice title="No resource packs found" text="Import a Minecraft resource-pack .zip archive to get started." icon={WandSparkles}/> : <div className="mod-list">{resourcePacks.map(pack=><div className="mod-row" key={pack.fileName}><div className="mod-file"><WandSparkles size={18}/><div><strong>{pack.fileName}</strong><small>{(pack.sizeBytes/1024/1024).toFixed(2)} MB · {pack.enabled ? "Available in Minecraft" : "Disabled"}</small></div></div><div className="mod-actions"><button className="outline-button" disabled={packsBusy} onClick={()=>void toggleResourcePack(pack)}>{pack.enabled ? "Disable" : "Enable"}</button><button className="outline-button danger-button" disabled={packsBusy} onClick={()=>void deleteResourcePack(pack)}>Remove</button></div></div>)}</div>}<p className="muted-note">Only .zip archives are supported. Disabled packs are renamed with .disabled and will not appear in Minecraft's resource-pack list. Choose enabled packs inside Minecraft's Options → Resource Packs screen.</p></>}
        {page === "Worlds" && <><PageHeader eyebrow="YOUR BUILDS" title="Worlds" subtitle="Browse, import, export, and back up your Minecraft saves."/><div className="settings-card"><div className="setting-row"><div><strong>Worlds folder</strong><small>{prefs.gameDirectory ? `${prefs.gameDirectory}/saves` : "Default Flame Launcher game directory /saves"}</small></div><button className="outline-button" disabled={!desktopAvailable || worldsBusy} onClick={()=>void refreshWorlds()}><RefreshCw size={15}/> Refresh</button></div><div className="setting-row"><div><strong>Import a world</strong><small>Enter the path to an extracted world folder containing level.dat.</small></div></div><div className="choice-row"><input className="text-input" value={worldSourcePath} onChange={e=>setWorldSourcePath(e.target.value)} placeholder="/path/to/My World"/><button className="play-button" disabled={!desktopAvailable || worldsBusy || !worldSourcePath.trim()} onClick={()=>void addWorld()}>Import world</button></div><div className="setting-row"><div><strong>Export destination</strong><small>Choose a new folder path; each world's export uses this destination exactly.</small></div></div><input className="text-input" value={worldExportPath} onChange={e=>setWorldExportPath(e.target.value)} placeholder="/path/to/exported-world"/></div><div className="section-title-row"><div><div className="section-kicker">SAVED WORLDS</div><h2>{worlds.length} worlds</h2></div></div>{!desktopAvailable ? <FeatureNotice title="Desktop app required" text="World files can only be managed by the native Flame Launcher app." icon={FolderOpen}/> : worldsBusy && !worlds.length ? <p>Scanning saved worlds…</p> : worlds.length === 0 ? <FeatureNotice title="No worlds found" text="Create a world in Minecraft or import an extracted world folder to see it here." icon={FolderOpen}/> : <div className="mod-list">{worlds.map(world=><div className="mod-row" key={world.folderName}><div className="mod-file"><FolderOpen size={18}/><div><strong>{world.displayName}</strong><small>{(world.sizeBytes/1024/1024).toFixed(1)} MB · {world.modifiedUnix ? new Date(world.modifiedUnix*1000).toLocaleString() : "Date unavailable"}</small></div></div><div className="mod-actions"><button className="outline-button" disabled={worldsBusy} onClick={()=>void createWorldBackup(world)}>Backup</button><button className="outline-button" disabled={worldsBusy || !worldExportPath.trim()} onClick={()=>void exportWorldCopy(world)}>Export</button><button className="outline-button danger-button" disabled={worldsBusy} onClick={()=>void deleteWorld(world)}>Delete</button></div></div>)}</div>}<p className="muted-note">World backups are stored inside the game directory's backups folder. Import requires an extracted world folder with level.dat; zipped world archives must be extracted first. Avoid modifying worlds while Minecraft is running.</p></>}
        {page === "Skins" && <><PageHeader eyebrow="IDENTITY" title="Player skins" subtitle="Build a local library of Minecraft skin textures."/><div className="settings-card"><div className="setting-row"><div><strong>Import a skin PNG</strong><small>Supports standard 64×64 modern skins and 64×32 classic skins, including scaled images.</small></div><button className="outline-button" disabled={!desktopAvailable || skinsBusy} onClick={()=>void refreshSkins()}><RefreshCw size={15}/> Refresh</button></div><div className="choice-row"><input className="text-input" value={skinSourcePath} onChange={e=>setSkinSourcePath(e.target.value)} placeholder="/path/to/skin.png"/><button className="play-button" disabled={!desktopAvailable || skinsBusy || !skinSourcePath.trim()} onClick={()=>void addSkin()}>Import skin</button></div></div>{!desktopAvailable ? <FeatureNotice title="Desktop app required" text="Skin files can only be managed by the native Flame Launcher app." icon={UserRound}/> : skinsBusy && !skins.length ? <p>Loading skin library…</p> : skins.length === 0 ? <FeatureNotice title="Your skin library is empty" text="Import a Minecraft skin PNG to preview and keep it in your local library." icon={UserRound}/> : <div className="skin-grid">{skins.map(skin=><div className="skin-card" key={skin.fileName}><div className="skin-preview"><img src={convertFileSrc(skin.filePath)} alt={skin.fileName} /></div><div className="skin-card-info"><strong>{skin.fileName}</strong><small>{skin.width} × {skin.height} · {(skin.sizeBytes/1024).toFixed(1)} KB</small><button className="outline-button danger-button" disabled={skinsBusy} onClick={()=>void deleteSkin(skin)}>Remove</button></div></div>)}</div>}<p className="muted-note">This is a local PNG library and preview only. It does not upload or equip skins on your Minecraft profile. Microsoft sign-in and official skin upload are not implemented yet.</p></>}
        {page === "About" && <><PageHeader eyebrow="ABOUT THE PROJECT" title="Flame Launcher" subtitle="Created and maintained by MFLegend."/><div className="about-card"><div className="brand-mark large-mark">FL</div><h2>Flame Launcher <span>0.1.0</span></h2><p>Created and maintained by MFLegend. Built for clarity, control, and low-memory Linux devices.</p><div className="about-line"><span>Desktop shell</span><b>{desktopAvailable?"Tauri 2":"Web preview"}</b></div><div className="about-line"><span>Version metadata</span><b>Official Mojang manifest</b></div><div className="about-line"><span>License</span><b>Project license to be defined</b></div></div></>}
      </div>
      <footer className="footer"><span>© 2026 Flame Launcher · MFLegend</span><span><ShieldCheck size={13}/> Built with safety & performance in mind</span></footer>
    </main>
  </div>;
}
function PageHeader({eyebrow,title,subtitle}:{eyebrow:string;title:string;subtitle:string}) { return <div className="page-header"><div className="eyebrow">{eyebrow}</div><h1>{title}</h1><p>{subtitle}</p></div>; }
function FeatureNotice({title,text,icon:Icon}:{title:string;text:string;icon:typeof Home}) { return <div className="feature-notice"><div className="notice-icon"><Icon size={22}/></div><h2>{title}</h2><p>{text}</p><span className="coming-label">IN DEVELOPMENT</span></div>; }
export default App;
