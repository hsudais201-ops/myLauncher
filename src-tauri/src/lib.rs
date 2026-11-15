use serde::{Deserialize, Serialize};
use std::{collections::HashSet, path::PathBuf, process::Command};

const MANIFEST_URL: &str = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct Version {
    id: String,
    #[serde(rename = "type")]
    kind: String,
    url: String,
    time: String,
    release_time: String,
}
#[derive(Debug, Deserialize)]
struct Manifest { versions: Vec<Version> }

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct JavaRuntime { path: String, version: String, architecture: String }

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct Preferences {
    selected_version: String,
    ram_mb: u32,
    game_directory: String,
    accent: String,
    theme: String,
}
impl Default for Preferences {
    fn default() -> Self {
        Self { selected_version: String::new(), ram_mb: 2048, game_directory: String::new(), accent: "#8b5cf6".into(), theme: "dark".into() }
    }
}

#[tauri::command]
async fn get_versions() -> Result<Vec<Version>, String> {
    let response = reqwest::Client::builder().user_agent("MFLegend/0.1.0").build()
        .map_err(|e| e.to_string())?.get(MANIFEST_URL).send().await.map_err(|e| format!("Mojang manifest request failed: {e}"))?;
    if !response.status().is_success() { return Err(format!("Mojang manifest returned HTTP {}", response.status())); }
    let manifest: Manifest = response.json().await.map_err(|e| format!("Invalid Mojang manifest: {e}"))?;
    Ok(manifest.versions)
}

#[tauri::command]
fn detect_java() -> Vec<JavaRuntime> {
    let mut candidates: Vec<PathBuf> = Vec::new();
    if let Ok(path) = std::env::var("PATH") {
        for dir in std::env::split_paths(&path) { candidates.push(dir.join("java")); }
    }
    for path in ["/usr/bin/java", "/usr/lib/jvm/default-java/bin/java", "/usr/lib/jvm/java-21-openjdk/bin/java", "/usr/lib/jvm/java-17-openjdk/bin/java", "/opt/java/bin/java"] {
        candidates.push(PathBuf::from(path));
    }
    let mut seen = HashSet::new();
    let mut found = Vec::new();
    for path in candidates {
        if !path.is_file() { continue; }
        let canonical = path.canonicalize().unwrap_or(path.clone());
        if !seen.insert(canonical.clone()) { continue; }
        let output = match Command::new(&canonical).arg("-XshowSettings:properties").arg("-version").output() { Ok(v) => v, Err(_) => continue };
        let text = format!("{}\n{}", String::from_utf8_lossy(&output.stderr), String::from_utf8_lossy(&output.stdout));
        let version = text.lines().find(|l| l.contains("java.version =")).and_then(|l| l.split('=').nth(1)).map(str::trim).unwrap_or("Unknown").to_string();
        let architecture = text.lines().find(|l| l.contains("os.arch =")).and_then(|l| l.split('=').nth(1)).map(str::trim).unwrap_or(std::env::consts::ARCH).to_string();
        found.push(JavaRuntime { path: canonical.display().to_string(), version, architecture });
    }
    found
}

fn preferences_path() -> Result<PathBuf, String> {
    let base = dirs::config_dir().ok_or("Cannot locate configuration directory")?.join("mflegend");
    std::fs::create_dir_all(&base).map_err(|e| e.to_string())?;
    Ok(base.join("preferences.json"))
}
#[tauri::command]
fn load_preferences() -> Result<Preferences, String> {
    let path = preferences_path()?;
    if !path.exists() { return Ok(Preferences::default()); }
    let content = std::fs::read_to_string(path).map_err(|e| e.to_string())?;
    serde_json::from_str(&content).map_err(|e| format!("Preferences file is invalid: {e}"))
}
#[tauri::command]
fn save_preferences(preferences: Preferences) -> Result<(), String> {
    if !(1024..=16384).contains(&preferences.ram_mb) { return Err("RAM allocation must be between 1024 and 16384 MB".into()); }
    if !preferences.accent.starts_with('#') || preferences.accent.len() != 7 { return Err("Invalid accent color".into()); }
    let path = preferences_path()?;
    let temp = path.with_extension("json.tmp");
    let data = serde_json::to_vec_pretty(&preferences).map_err(|e| e.to_string())?;
    std::fs::write(&temp, data).map_err(|e| e.to_string())?;
    std::fs::rename(&temp, &path).map_err(|e| e.to_string())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![get_versions, detect_java, load_preferences, save_preferences])
        .run(tauri::generate_context!())
        .expect("error while running MFLegend");
}
