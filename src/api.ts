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
