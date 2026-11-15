import { invoke } from "@tauri-apps/api/core";
import type { InstallResult, JavaRuntime, Preferences, Version } from "./types";

const isDesktop = "__TAURI_INTERNALS__" in window;
export async function getVersions(): Promise<Version[]> {
  if (isDesktop) return invoke<Version[]>("get_versions");
  const response = await fetch("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json");
  if (!response.ok) throw new Error("Mojang version service is unavailable.");
  const data = await response.json();
  return data.versions;
}
export async function detectJava(): Promise<JavaRuntime[]> {
  return isDesktop ? invoke<JavaRuntime[]>("detect_java") : [];
}
const defaults: Preferences = {
  selectedVersion: "",
  selectedJavaPath: "",
  ramMb: 2048,
  gameDirectory: "",
  accent: "#8b5cf6",
  theme: "dark"
};
export async function loadPreferences(): Promise<Preferences> {
  if (isDesktop) return invoke<Preferences>("load_preferences");
  try {
    return { ...defaults, ...JSON.parse(localStorage.getItem("mflegend-preferences") || "{}") };
  } catch { return defaults; }
}
export async function savePreferences(value: Preferences): Promise<void> {
  if (isDesktop) return invoke("save_preferences", { preferences: value });
  localStorage.setItem("mflegend-preferences", JSON.stringify(value));
}
export const desktopAvailable = isDesktop;

// Downloads the official client, libraries, asset index, and asset objects into the selected game directory.
export async function installVersion(versionId: string, gameDirectory: string): Promise<InstallResult> {
  if (!isDesktop) throw new Error("Minecraft installation is available in the MFLegend desktop app, not browser preview.");
  return invoke<InstallResult>("install_version", { versionId, gameDirectory });
}

export async function installFabric(gameVersion: string, gameDirectory: string): Promise<InstallResult> {
  if (!isDesktop) throw new Error("Fabric installation requires the native MFLegend desktop app.");
  return invoke<InstallResult>("install_fabric", { gameVersion, gameDirectory });
}

export async function installForge(gameVersion: string, gameDirectory: string, javaPath: string): Promise<InstallResult> {
  if (!isDesktop) throw new Error("Forge installation requires the native MFLegend desktop app.");
  return invoke<InstallResult>("install_forge", { gameVersion, gameDirectory, javaPath });
}

export async function launchVersion(versionId: string, gameDirectory: string, username: string, javaPath: string, ramMb: number): Promise<number> {
  if (!isDesktop) throw new Error("Launching Minecraft requires the native MFLegend desktop app.");
  return invoke<number>("launch_version", { versionId, gameDirectory, username, javaPath, ramMb });
}

export type ModEntry = { fileName: string; enabled: boolean; sizeBytes: number };
export async function listMods(gameDirectory: string): Promise<ModEntry[]> {
  if (!isDesktop) throw new Error("Mod management requires the native MFLegend desktop app.");
  return invoke<ModEntry[]>("list_mods", { gameDirectory });
}
export async function importMod(sourcePath: string, gameDirectory: string): Promise<ModEntry> {
  if (!isDesktop) throw new Error("Mod import requires the native MFLegend desktop app.");
  return invoke<ModEntry>("import_mod", { sourcePath, gameDirectory });
}
export async function setModEnabled(fileName: string, enabled: boolean, gameDirectory: string): Promise<void> {
  if (!isDesktop) throw new Error("Mod management requires the native MFLegend desktop app.");
  return invoke("set_mod_enabled", { fileName, enabled, gameDirectory });
}
export async function removeMod(fileName: string, gameDirectory: string): Promise<void> {
  if (!isDesktop) throw new Error("Mod management requires the native MFLegend desktop app.");
  return invoke("remove_mod", { fileName, gameDirectory });
}

export type ResourcePackEntry = { fileName: string; enabled: boolean; sizeBytes: number };
export async function listResourcePacks(gameDirectory: string): Promise<ResourcePackEntry[]> {
  if (!isDesktop) throw new Error("Resource pack management requires the native MFLegend desktop app.");
  return invoke<ResourcePackEntry[]>("list_resource_packs", { gameDirectory });
}
export async function importResourcePack(sourcePath: string, gameDirectory: string): Promise<ResourcePackEntry> {
  if (!isDesktop) throw new Error("Resource pack import requires the native MFLegend desktop app.");
  return invoke<ResourcePackEntry>("import_resource_pack", { sourcePath, gameDirectory });
}
export async function setResourcePackEnabled(fileName: string, enabled: boolean, gameDirectory: string): Promise<void> {
  if (!isDesktop) throw new Error("Resource pack management requires the native MFLegend desktop app.");
  return invoke("set_resource_pack_enabled", { fileName, enabled, gameDirectory });
}
export async function removeResourcePack(fileName: string, gameDirectory: string): Promise<void> {
  if (!isDesktop) throw new Error("Resource pack management requires the native MFLegend desktop app.");
  return invoke("remove_resource_pack", { fileName, gameDirectory });
}

export type WorldEntry = { folderName: string; displayName: string; sizeBytes: number; modifiedUnix: number };
export async function listWorlds(gameDirectory: string): Promise<WorldEntry[]> {
  if (!isDesktop) throw new Error("World management requires the native MFLegend desktop app.");
  return invoke<WorldEntry[]>("list_worlds", { gameDirectory });
}
export async function importWorld(sourcePath: string, gameDirectory: string): Promise<WorldEntry> {
  if (!isDesktop) throw new Error("World import requires the native MFLegend desktop app.");
  return invoke<WorldEntry>("import_world", { sourcePath, gameDirectory });
}
export async function exportWorld(folderName: string, destinationPath: string, gameDirectory: string): Promise<string> {
  if (!isDesktop) throw new Error("World export requires the native MFLegend desktop app.");
  return invoke<string>("export_world", { folderName, destinationPath, gameDirectory });
}
export async function backupWorld(folderName: string, gameDirectory: string): Promise<string> {
  if (!isDesktop) throw new Error("World backups require the native MFLegend desktop app.");
  return invoke<string>("backup_world", { folderName, gameDirectory });
}
export async function removeWorld(folderName: string, gameDirectory: string): Promise<void> {
  if (!isDesktop) throw new Error("World removal requires the native MFLegend desktop app.");
  return invoke("remove_world", { folderName, gameDirectory });
}

export type SkinEntry = { fileName: string; filePath: string; width: number; height: number; sizeBytes: number };
export async function listSkins(gameDirectory: string): Promise<SkinEntry[]> {
  if (!isDesktop) throw new Error("Skin file management requires the native MFLegend desktop app.");
  return invoke<SkinEntry[]>("list_skins", { gameDirectory });
}
export async function importSkin(sourcePath: string, gameDirectory: string): Promise<SkinEntry> {
  if (!isDesktop) throw new Error("Skin import requires the native MFLegend desktop app.");
  return invoke<SkinEntry>("import_skin", { sourcePath, gameDirectory });
}
export async function removeSkin(fileName: string, gameDirectory: string): Promise<void> {
  if (!isDesktop) throw new Error("Skin removal requires the native MFLegend desktop app.");
  return invoke("remove_skin", { fileName, gameDirectory });
}

export type InstalledVersion = { id: string; versionType: string; inheritsFrom?: string; mainClass?: string };
export async function listInstalledVersions(gameDirectory: string): Promise<InstalledVersion[]> {
  if (!isDesktop) throw new Error("Installed profile scanning requires the native MFLegend desktop app.");
  return invoke<InstalledVersion[]>("list_installed_versions", { gameDirectory });
}
