use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};
use serde_json::Value;
use std::{collections::{HashMap, HashSet}, path::{Component, Path, PathBuf}, process::{Child, Command}, sync::{atomic::{AtomicU64, Ordering}, Mutex, OnceLock}, time::{SystemTime, UNIX_EPOCH}};

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
struct InstallationProfile {
    id: String,
    name: String,
    version_id: String,
    game_directory: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
struct Preferences {
    #[serde(default)]
    microsoft_client_id: String,
    #[serde(default)]
    installations: Vec<InstallationProfile>,
    #[serde(default)]
    selected_installation_id: String,
    selected_version: String,
    #[serde(default)]
    selected_java_path: String,
    ram_mb: u32,
    game_directory: String,
    accent: String,
    theme: String,
}
impl Default for Preferences {
    fn default() -> Self {
        Self { microsoft_client_id: String::new(), installations: Vec::new(), selected_installation_id: String::new(), selected_version: String::new(), selected_java_path: String::new(), ram_mb: 2048, game_directory: String::new(), accent: "#8b5cf6".into(), theme: "dark".into() }
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct InstallResult {
    version_id: String,
    game_directory: String,
    downloaded_files: usize,
    skipped_files: usize,
    assets_downloaded: usize,
}

fn http_client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder().user_agent("MFLegend/0.1.0").build().map_err(|e| e.to_string())
}

#[tauri::command]
async fn get_versions() -> Result<Vec<Version>, String> {
    let response = http_client()?.get(MANIFEST_URL).send().await.map_err(|e| format!("Mojang manifest request failed: {e}"))?;
    if !response.status().is_success() { return Err(format!("Mojang manifest returned HTTP {}", response.status())); }
    let manifest: Manifest = response.json().await.map_err(|e| format!("Invalid Mojang manifest: {e}"))?;
    Ok(manifest.versions)
}

fn safe_join(root: &Path, relative: &str) -> Result<PathBuf, String> {
    let rel = Path::new(relative);
    if rel.as_os_str().is_empty() || rel.components().any(|c| !matches!(c, Component::Normal(_))) {
        return Err(format!("Unsafe installation path: {relative}"));
    }
    Ok(root.join(rel))
}

async fn write_file_atomic(destination: &Path, bytes: &[u8]) -> Result<(), String> {
    if let Some(parent) = destination.parent() {
        tokio::fs::create_dir_all(parent).await.map_err(|e| format!("Could not create metadata directory: {e}"))?;
    }
    let temporary = destination.with_extension("mflegend-tmp");
    tokio::fs::write(&temporary, bytes).await.map_err(|e| format!("Could not write temporary metadata: {e}"))?;
    if let Err(error) = tokio::fs::rename(&temporary, destination).await {
        let _ = tokio::fs::remove_file(&temporary).await;
        return Err(format!("Could not atomically finalize metadata: {error}"));
    }
    Ok(())
}

static DOWNLOAD_TEMP_SEQUENCE: AtomicU64 = AtomicU64::new(0);

fn valid_sha1(value: &str) -> bool {
    value.len() == 40 && value.chars().all(|c| c.is_ascii_hexdigit())
}

async fn download_verified(client: &reqwest::Client, url: &str, destination: &Path, expected_sha1: Option<&str>) -> Result<bool, String> {
    if !url.starts_with("https://") { return Err("Refusing a non-HTTPS download URL".into()); }
    if let Some(expected) = expected_sha1 {
        if !valid_sha1(expected) {
            return Err(format!("Invalid SHA-1 checksum supplied for {url}"));
        }
    }
    if let Some(parent) = destination.parent() {
        tokio::fs::create_dir_all(parent).await.map_err(|e| e.to_string())?;
    }
    if destination.is_file() {
        if let Some(expected) = expected_sha1 {
            let existing = tokio::fs::read(destination).await.map_err(|e| e.to_string())?;
            let digest = format!("{:x}", Sha1::digest(&existing));
            if digest.eq_ignore_ascii_case(expected) { return Ok(false); }
        }
    }
    let response = client.get(url).send().await.map_err(|e| format!("Download failed for {url}: {e}"))?;
    if !response.status().is_success() {
        return Err(format!("Download returned HTTP {} for {url}", response.status()));
    }
    let bytes = response.bytes().await.map_err(|e| format!("Could not read download: {e}"))?;
    if let Some(expected) = expected_sha1 {
        let digest = format!("{:x}", Sha1::digest(&bytes));
        if !digest.eq_ignore_ascii_case(expected) {
            return Err(format!("SHA-1 verification failed for {url}"));
        }
    }
    let sequence = DOWNLOAD_TEMP_SEQUENCE.fetch_add(1, Ordering::Relaxed);
    let file_name = destination.file_name().and_then(|name| name.to_str()).ok_or("Invalid download destination filename")?;
    let temporary = destination.with_file_name(format!(".{file_name}.{}.{}.tmp", std::process::id(), sequence));
    if let Err(error) = tokio::fs::write(&temporary, &bytes).await {
        let _ = tokio::fs::remove_file(&temporary).await;
        return Err(format!("Could not write temporary download: {error}"));
    }
    if let Err(error) = tokio::fs::rename(&temporary, destination).await {
        let _ = tokio::fs::remove_file(&temporary).await;
        return Err(format!("Could not finalize downloaded file: {error}"));
    }
    Ok(true)
}

fn rule_matches(rule: &Value, features: &serde_json::Map<String, Value>) -> bool {
    let os = rule.get("os");
    let name_matches = os.and_then(|v| v.get("name")).and_then(Value::as_str)
        .map(|name| name == launch_os_name()).unwrap_or(true);
    let arch_matches = os.and_then(|v| v.get("arch")).and_then(Value::as_str)
        .map(|arch| arch == std::env::consts::ARCH).unwrap_or(true);
    let features_match = rule.get("features").and_then(Value::as_object).map(|required| {
        required.iter().all(|(key, expected)| features.get(key) == Some(expected))
    }).unwrap_or(true);
    name_matches && arch_matches && features_match
}

fn rules_allow(rules: Option<&Vec<Value>>, features: &serde_json::Map<String, Value>) -> bool {
    let Some(rules) = rules else { return true; };
    let mut allowed = false;
    for rule in rules {
        if rule_matches(rule, features) {
            allowed = rule.get("action").and_then(Value::as_str) == Some("allow");
        }
    }
    allowed
}

fn default_features() -> serde_json::Map<String, Value> {
    // These flags describe the launch modes MFLegend currently supports.
    let mut features = serde_json::Map::new();
    for name in ["is_demo_user", "has_custom_resolution", "has_quick_plays_support",
                 "is_quick_play_singleplayer", "is_quick_play_multiplayer", "is_quick_play_realms"] {
        features.insert(name.into(), Value::Bool(false));
    }
    features
}

fn library_allowed(library: &Value) -> bool {
    rules_allow(library.get("rules").and_then(Value::as_array), &default_features())
}

#[tauri::command]
async fn install_version(version_id: String, game_directory: String) -> Result<InstallResult, String> {
    if version_id.is_empty() || version_id.len() > 64 || !version_id.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '_' || c == '-') {
        return Err("Invalid Minecraft version identifier".into());
    }
    let root = if game_directory.trim().is_empty() {
        dirs::data_dir().ok_or("Cannot locate application data directory")?.join("mflegend").join("game")
    } else { PathBuf::from(game_directory.trim()) };
    tokio::fs::create_dir_all(&root).await.map_err(|e| format!("Cannot create game directory: {e}"))?;
    let client = http_client()?;
    let manifest_response = client.get(MANIFEST_URL).send().await.map_err(|e| e.to_string())?;
    if !manifest_response.status().is_success() { return Err(format!("Mojang manifest returned HTTP {}", manifest_response.status())); }
    let manifest: Manifest = manifest_response.json().await.map_err(|e| e.to_string())?;
    let entry = manifest.versions.into_iter().find(|v| v.id == version_id).ok_or("Selected version is not present in the official Mojang manifest")?;
    let metadata_response = client.get(&entry.url).send().await.map_err(|e| format!("Version metadata request failed: {e}"))?;
    if !metadata_response.status().is_success() { return Err(format!("Version metadata returned HTTP {}", metadata_response.status())); }
    let metadata: Value = metadata_response.json().await.map_err(|e| format!("Invalid version metadata: {e}"))?;
    if metadata.get("id").and_then(Value::as_str) != Some(version_id.as_str()) { return Err("Mojang metadata version ID did not match the selected version".into()); }

    let mut downloaded = 0usize;
    let mut skipped = 0usize;
    let version_dir = root.join("versions").join(&version_id);
    tokio::fs::create_dir_all(&version_dir).await.map_err(|e| e.to_string())?;
    let metadata_path = version_dir.join(format!("{version_id}.json"));
    let metadata_bytes = serde_json::to_vec_pretty(&metadata).map_err(|e| e.to_string())?;
    write_file_atomic(&metadata_path, &metadata_bytes).await?;

    let client_download = metadata.get("downloads").and_then(|v| v.get("client")).ok_or("This Minecraft version has no downloadable client JAR")?;
    let client_url = client_download.get("url").and_then(Value::as_str).ok_or("Client download URL is missing")?;
    let client_sha = client_download.get("sha1").and_then(Value::as_str);
    if download_verified(&client, client_url, &version_dir.join(format!("{version_id}.jar")), client_sha).await? { downloaded += 1; } else { skipped += 1; }

    if let Some(libraries) = metadata.get("libraries").and_then(Value::as_array) {
        for library in libraries {
            if !library_allowed(library) { continue; }
            let Some(artifact) = library.get("downloads").and_then(|v| v.get("artifact")) else { continue; };
            let Some(url) = artifact.get("url").and_then(Value::as_str) else { continue; };
            let relative = artifact.get("path").and_then(Value::as_str).ok_or("A library artifact is missing its relative path")?;
            let destination = safe_join(&root.join("libraries"), relative)?;
            let sha = artifact.get("sha1").and_then(Value::as_str);
            if download_verified(&client, url, &destination, sha).await? { downloaded += 1; } else { skipped += 1; }
        if let Some(classifiers) = library.get("downloads").and_then(|v| v.get("classifiers")) {
            let native_key = library.get("natives").and_then(|v| v.get(launch_os_name())).and_then(Value::as_str).unwrap_or("");
            if !native_key.is_empty() {
                let arch = if std::env::consts::ARCH == "x86_64" { "64" } else { "32" };
                if let Some(native) = classifiers.get(&native_key.replace("${arch}", arch)) {
                    if let (Some(url), Some(relative)) = (native.get("url").and_then(Value::as_str), native.get("path").and_then(Value::as_str)) {
                        let destination = safe_join(&root.join("libraries"), relative)?;
                        if download_verified(&client, url, &destination, native.get("sha1").and_then(Value::as_str)).await? { downloaded += 1; } else { skipped += 1; }
                    }
                }
            }
        }
        }
    }

    let mut assets_downloaded = 0usize;
    if let Some(index) = metadata.get("assetIndex") {
        let index_id = index.get("id").and_then(Value::as_str).ok_or("Asset index ID is missing")?;
        let index_url = index.get("url").and_then(Value::as_str).ok_or("Asset index URL is missing")?;
        let index_sha = index.get("sha1").and_then(Value::as_str);
        let index_path = root.join("assets").join("indexes").join(format!("{index_id}.json"));
        if download_verified(&client, index_url, &index_path, index_sha).await? { downloaded += 1; } else { skipped += 1; }
        let index_data: Value = serde_json::from_slice(&tokio::fs::read(&index_path).await.map_err(|e| e.to_string())?).map_err(|e| format!("Invalid asset index: {e}"))?;
        if let Some(objects) = index_data.get("objects").and_then(Value::as_object) {
            for object in objects.values() {
                let hash = object.get("hash").and_then(Value::as_str).ok_or("Asset hash is missing")?;
                if hash.len() != 40 || !hash.chars().all(|c| c.is_ascii_hexdigit()) { return Err("Asset index contains an invalid SHA-1 hash".into()); }
                let relative = format!("{}/{}", &hash[..2], hash);
                let destination = safe_join(&root.join("assets").join("objects"), &relative)?;
                let url = format!("https://resources.download.minecraft.net/{}/{}", &hash[..2], hash);
                if download_verified(&client, &url, &destination, Some(hash)).await? { downloaded += 1; assets_downloaded += 1; } else { skipped += 1; }
            }
        }
    }

    if let Some(logging) = metadata.get("logging").and_then(|v| v.get("client")).and_then(|v| v.get("file")) {
        if let (Some(url), Some(id)) = (logging.get("url").and_then(Value::as_str), logging.get("id").and_then(Value::as_str)) {
            let destination = safe_join(&root.join("assets").join("log_configs"), id)?;
            if download_verified(&client, url, &destination, logging.get("sha1").and_then(Value::as_str)).await? { downloaded += 1; } else { skipped += 1; }
        }
    }

    Ok(InstallResult { version_id, game_directory: root.display().to_string(), downloaded_files: downloaded, skipped_files: skipped, assets_downloaded })
}



fn parse_java_major(version: &str) -> Option<u32> {
    let version = version.trim();
    let first = version.split('.').next()?;
    let major = if first == "1" {
        version.split('.').nth(1)?
    } else {
        first
    };
    let digits: String = major.chars().take_while(|c| c.is_ascii_digit()).collect();
    if digits.is_empty() { None } else { digits.parse::<u32>().ok() }
}

fn java_major_from_output(output: &str) -> Option<u32> {
    output.lines().find_map(|line| {
        let start = line.find('"')? + 1;
        let version = line.get(start..)?.split('"').next()?;
        parse_java_major(version)
    })
}

fn valid_version_id(id: &str) -> bool {
    !id.is_empty() && id.len() <= 64 &&
        id.chars().all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '_' || c == '-')
}

fn merge_inherited_metadata(parent: &mut Value, child: Value) {
    match (parent, child) {
        (Value::Object(parent), Value::Object(child)) => {
            for (key, child_value) in child {
                if key == "inheritsFrom" { continue; }
                match (parent.get_mut(&key), child_value) {
                    (Some(Value::Array(parent_array)), Value::Array(child_array))
                        if key == "libraries" => parent_array.extend(child_array),
                    (Some(Value::Object(parent_object)), Value::Object(child_object))
                        if key == "arguments" => {
                            let mut nested_parent = Value::Object(parent_object.clone());
                            merge_inherited_metadata(&mut nested_parent, Value::Object(child_object));
                            if let Value::Object(merged) = nested_parent {
                                *parent_object = merged;
                            }
                        }
                    (Some(existing), Value::Array(child_array))
                        if key == "jvm" || key == "game" => {
                            if let Value::Array(parent_array) = existing {
                                parent_array.extend(child_array);
                            } else {
                                *existing = Value::Array(child_array);
                            }
                        }
                    (Some(existing), child_value) => merge_inherited_metadata(existing, child_value),
                    (None, child_value) => { parent.insert(key, child_value); }
                }
            }
        }
        (parent, child) => *parent = child,
    }
}

fn resolve_version_metadata(root: &Path, version_id: &str) -> Result<(Value, String), String> {
    if !valid_version_id(version_id) { return Err("Invalid version ID".into()); }
    let mut current_id = version_id.to_string();
    let mut seen = HashSet::new();
    let mut chain = Vec::<(String, Value)>::new();

    loop {
        if !seen.insert(current_id.clone()) {
            return Err(format!("Version inheritance cycle detected at {current_id}"));
        }
        if chain.len() >= 32 {
            return Err("Version inheritance exceeds the 32-version safety limit".into());
        }
        let path = root.join("versions").join(&current_id).join(format!("{current_id}.json"));
        let bytes = std::fs::read(&path)
            .map_err(|e| format!("Could not read version metadata for {current_id}: {e}. Install the parent version first."))?;
        let metadata: Value = serde_json::from_slice(&bytes)
            .map_err(|e| format!("Invalid metadata for version {current_id}: {e}"))?;
        let parent_id = metadata.get("inheritsFrom").and_then(Value::as_str).map(str::to_string);
        chain.push((current_id.clone(), metadata));
        match parent_id {
            Some(parent) => {
                if !valid_version_id(&parent) {
                    return Err(format!("Version {current_id} contains an invalid inheritsFrom ID"));
                }
                current_id = parent;
            }
            None => break,
        }
    }

    let base_jar_id = chain.iter()
        .find_map(|(_, metadata)| metadata.get("jar").and_then(Value::as_str).map(str::to_string))
        .or_else(|| chain.last().map(|(id, _)| id.clone()))
        .unwrap_or_else(|| version_id.to_string());
    let mut iter = chain.into_iter().rev();
    let (_, mut merged) = iter.next().ok_or("Version metadata chain is empty")?;
    for (_, child) in iter {
        merge_inherited_metadata(&mut merged, child);
    }
    Ok((merged, base_jar_id))
}

fn launch_os_name() -> &'static str { if cfg!(target_os = "windows") { "windows" } else if cfg!(target_os = "macos") { "osx" } else { "linux" } }
fn launch_rules_allow(rules: Option<&Vec<Value>>) -> bool {
    rules_allow(rules, &default_features())
}
fn offline_uuid(username: &str) -> String {
    let h = format!("{:x}", md5::compute(format!("OfflinePlayer:{username}")));
    format!("{}-{}-{}-{}-{}", &h[0..8], &h[8..12], &h[12..16], &h[16..20], &h[20..32])
}
fn extract_native_archive(path: &Path, out_dir: &Path) -> Result<(), String> {
    use std::io;
    let file = std::fs::File::open(path).map_err(|e| e.to_string())?;
    let mut zip = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    std::fs::create_dir_all(out_dir).map_err(|e| e.to_string())?;
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i).map_err(|e| e.to_string())?;
        let name = entry.name().to_string();
        if name.starts_with("META-INF/") || name.ends_with('/') { continue; }
        let rel = Path::new(&name);
        if rel.components().any(|c| !matches!(c, Component::Normal(_))) { return Err("Unsafe path in native archive".into()); }
        let target = out_dir.join(rel);
        if let Some(parent) = target.parent() { std::fs::create_dir_all(parent).map_err(|e| e.to_string())?; }
        let mut output = std::fs::File::create(target).map_err(|e| e.to_string())?;
        io::copy(&mut entry, &mut output).map_err(|e| e.to_string())?;
    }
    Ok(())
}
fn collect_launch_args(v: &Value) -> Vec<String> {
    match v {
        Value::String(s) => vec![s.clone()],
        Value::Array(a) => a.iter().flat_map(collect_launch_args).collect(),
        Value::Object(o) => if launch_rules_allow(o.get("rules").and_then(Value::as_array)) { collect_launch_args(o.get("value").unwrap_or(&Value::Null)) } else { Vec::new() },
        _ => Vec::new()
    }
}

fn tokenize_legacy_arguments(input: &str) -> Result<Vec<String>, String> {
    let mut args = Vec::new();
    let mut current = String::new();
    let mut quote: Option<char> = None;
    let mut escaped = false;
    let mut started = false;

    for ch in input.chars() {
        if escaped {
            current.push(ch);
            escaped = false;
            started = true;
            continue;
        }
        if ch == char::from(92) {
            escaped = true;
            started = true;
            continue;
        }
        if ch == '"' || ch == '\'' {
            if quote == Some(ch) {
                quote = None;
            } else if quote.is_none() {
                quote = Some(ch);
            } else {
                current.push(ch);
            }
            started = true;
            continue;
        }
        if ch.is_whitespace() && quote.is_none() {
            if started {
                args.push(std::mem::take(&mut current));
                started = false;
            }
        } else {
            current.push(ch);
            started = true;
        }
    }
    if escaped { current.push(char::from(92)); }
    if quote.is_some() { return Err("Minecraft legacy launch arguments contain an unterminated quote".into()); }
    if started { args.push(current); }
    Ok(args)
}



#[derive(Deserialize)]
struct ForgePromotions {
    promos: std::collections::HashMap<String, String>,
}

async fn install_forge_profile(game_version: &str, game_directory: &str, java_path: &str) -> Result<InstallResult, String> {
    if !valid_version_id(game_version) { return Err("Invalid Minecraft game version".into()); }
    let root = if game_directory.trim().is_empty() {
        dirs::data_dir().ok_or("Cannot locate application data directory")?.join("mflegend").join("game")
    } else { PathBuf::from(game_directory.trim()) };
    // Forge's client installer expects the base Minecraft files to be present.
    install_version(game_version.to_string(), root.display().to_string()).await?;

    let client = http_client()?;
    let response = client.get("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
        .send().await.map_err(|e| format!("Forge version lookup failed: {e}"))?;
    if !response.status().is_success() { return Err(format!("Forge promotions returned HTTP {}", response.status())); }
    let promotions: ForgePromotions = response.json().await.map_err(|e| format!("Invalid Forge promotions: {e}"))?;
    let forge_version = promotions.promos.get(&format!("{game_version}-recommended"))
        .or_else(|| promotions.promos.get(&format!("{game_version}-latest")))
        .ok_or_else(|| format!("Forge does not publish a recommended or latest installer for Minecraft {game_version}"))?;
    if !valid_version_id(forge_version) { return Err("Forge returned an invalid loader version".into()); }

    let artifact = format!("{game_version}-{forge_version}");
    let installer_name = format!("forge-{artifact}-installer.jar");
    let installer_url = format!("https://maven.minecraftforge.net/net/minecraftforge/forge/{artifact}/{installer_name}");
    let installer_path = safe_join(&root.join("mflegend-downloads"), &installer_name)?;
    download_verified(&client, &installer_url, &installer_path, None).await?;

    let java = if java_path.trim().is_empty() { "java".to_string() } else { java_path.trim().to_string() };
    let install_root = root.clone();
    let installer_for_run = installer_path.clone();
    let output = tokio::task::spawn_blocking(move || {
        Command::new(&java).arg("-jar").arg(&installer_for_run).arg("--installClient").arg(&install_root).output()
            .map_err(|e| format!("Could not run Forge installer with {java}: {e}"))
    }).await.map_err(|e| format!("Forge installer task failed: {e}"))??;
    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    if !output.status.success() {
        return Err(format!("Forge installer failed ({}). {} {}", output.status, stdout.trim(), stderr.trim()));
    }

    let prefix = format!("{game_version}-forge-");
    let mut installed_id = None;
    let versions_dir = root.join("versions");
    for entry in std::fs::read_dir(&versions_dir).map_err(|e| format!("Cannot inspect installed Forge profiles: {e}"))? {
        let entry = entry.map_err(|e| e.to_string())?;
        let name = entry.file_name().to_string_lossy().to_string();
        if name.starts_with(&prefix) && valid_version_id(&name) {
            let metadata = entry.path().join(format!("{name}.json"));
            if metadata.is_file() { installed_id = Some(name); break; }
        }
    }
    let version_id = installed_id.ok_or_else(|| format!("Forge installer completed but did not create a recognizable profile. Installer output: {}", stdout.trim()))?;
    let _ = tokio::fs::remove_file(&installer_path).await;
    Ok(InstallResult {
        version_id,
        game_directory: root.display().to_string(),
        downloaded_files: 1,
        skipped_files: 0,
        assets_downloaded: 0,
    })
}

#[tauri::command]
async fn install_forge(game_version: String, game_directory: String, java_path: String) -> Result<InstallResult, String> {
    install_forge_profile(&game_version, &game_directory, &java_path).await
}

fn maven_artifact_path(coordinate: &str) -> Result<String, String> {
    let (coordinate, extension) = coordinate.split_once('@').unwrap_or((coordinate, "jar"));
    let parts: Vec<&str> = coordinate.split(':').collect();
    if !(3..=4).contains(&parts.len()) || parts.iter().any(|part| part.is_empty() || part.contains('/') || part.contains('\\') || *part == "." || *part == "..") {
        return Err(format!("Invalid Maven library coordinate: {coordinate}"));
    }
    let group = parts[0].split('.').collect::<Vec<_>>();
    if group.iter().any(|part| part.is_empty() || !part.chars().all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '-')) {
        return Err(format!("Invalid Maven group in library coordinate: {coordinate}"));
    }
    if !extension.chars().all(|c| c.is_ascii_alphanumeric()) {
        return Err(format!("Invalid Maven extension in library coordinate: {coordinate}"));
    }
    let artifact = parts[1];
    let version = parts[2];
    let classifier = parts.get(3).copied();
    let file = match classifier {
        Some(classifier) => format!("{artifact}-{version}-{classifier}.{extension}"),
        None => format!("{artifact}-{version}.{extension}"),
    };
    Ok(format!("{}/{}/{}/{}", group.join("/"), artifact, version, file))
}

async fn install_fabric_profile(game_version: &str, game_directory: &str) -> Result<InstallResult, String> {
    if !valid_version_id(game_version) { return Err("Invalid Minecraft game version".into()); }
    let root = if game_directory.trim().is_empty() {
        dirs::data_dir().ok_or("Cannot locate application data directory")?.join("mflegend").join("game")
    } else { PathBuf::from(game_directory.trim()) };
    tokio::fs::create_dir_all(&root).await.map_err(|e| format!("Cannot create game directory: {e}"))?;
    let client = http_client()?;

    let versions_url = format!("https://meta.fabricmc.net/v2/versions/loader/{game_version}");
    let response = client.get(&versions_url).send().await.map_err(|e| format!("Fabric version lookup failed: {e}"))?;
    if !response.status().is_success() { return Err(format!("Fabric metadata returned HTTP {}", response.status())); }
    let loaders: Vec<Value> = response.json().await.map_err(|e| format!("Invalid Fabric loader list: {e}"))?;
    let loader = loaders.iter().find(|entry| entry.get("loader").and_then(|v| v.get("stable")).and_then(Value::as_bool) == Some(true))
        .or_else(|| loaders.first()).ok_or_else(|| format!("No Fabric loader is available for Minecraft {game_version}"))?;
    let loader_version = loader.get("loader").and_then(|v| v.get("version")).and_then(Value::as_str)
        .ok_or("Fabric loader version is missing")?;
    let profile_url = format!("https://meta.fabricmc.net/v2/versions/loader/{game_version}/{loader_version}/profile/json");
    let response = client.get(&profile_url).send().await.map_err(|e| format!("Fabric profile download failed: {e}"))?;
    if !response.status().is_success() { return Err(format!("Fabric profile returned HTTP {}", response.status())); }
    let profile: Value = response.json().await.map_err(|e| format!("Invalid Fabric profile: {e}"))?;
    let version_id = profile.get("id").and_then(Value::as_str).ok_or("Fabric profile has no version ID")?.to_string();
    if !valid_version_id(&version_id) { return Err("Fabric returned an unsafe version ID".into()); }
    if profile.get("inheritsFrom").and_then(Value::as_str) != Some(game_version) {
        return Err("Fabric profile does not inherit from the selected Minecraft version".into());
    }
    let version_dir = root.join("versions").join(&version_id);
    tokio::fs::create_dir_all(&version_dir).await.map_err(|e| e.to_string())?;
    let metadata_path = version_dir.join(format!("{version_id}.json"));
    let bytes = serde_json::to_vec_pretty(&profile).map_err(|e| e.to_string())?;
    write_file_atomic(&metadata_path, &bytes).await?;

    let mut downloaded = 0usize;
    let mut skipped = 0usize;
    if let Some(libraries) = profile.get("libraries").and_then(Value::as_array) {
        for library in libraries {
            if !library_allowed(library) { continue; }
            let coordinate = library.get("name").and_then(Value::as_str)
                .ok_or("A Fabric library is missing its Maven coordinate")?;
            let relative = maven_artifact_path(coordinate)?;
            let base_url = library.get("url").and_then(Value::as_str).unwrap_or("https://maven.fabricmc.net/");
            if !base_url.starts_with("https://") { return Err(format!("Refusing non-HTTPS Fabric library URL: {base_url}")); }
            let url = format!("{}/{}", base_url.trim_end_matches('/'), relative);
            let destination = safe_join(&root.join("libraries"), &relative)?;
            if download_verified(&client, &url, &destination, library.get("sha1").and_then(Value::as_str)).await? {
                downloaded += 1;
            } else {
                skipped += 1;
            }
        }
    }
    Ok(InstallResult {
        version_id,
        game_directory: root.display().to_string(),
        downloaded_files: downloaded,
        skipped_files: skipped,
        assets_downloaded: 0,
    })
}

#[tauri::command]
async fn install_fabric(game_version: String, game_directory: String) -> Result<InstallResult, String> {
    install_fabric_profile(&game_version, &game_directory).await
}

async fn install_official_parent(root: &Path, version_id: &str) -> Result<(), String> {
    if !valid_version_id(version_id) { return Err("Invalid inherited parent version ID".into()); }
    let version_dir = root.join("versions").join(version_id);
    let metadata_path = version_dir.join(format!("{version_id}.json"));
    let jar_path = version_dir.join(format!("{version_id}.jar"));
    if metadata_path.is_file() && jar_path.is_file() { return Ok(()); }

    let client = http_client()?;
    let response = client.get(MANIFEST_URL).send().await.map_err(|e| format!("Could not retrieve parent version manifest: {e}"))?;
    if !response.status().is_success() { return Err(format!("Parent version manifest returned HTTP {}", response.status())); }
    let manifest: Manifest = response.json().await.map_err(|e| format!("Invalid parent version manifest: {e}"))?;
    let entry = manifest.versions.into_iter().find(|entry| entry.id == version_id)
        .ok_or_else(|| format!("Parent version {version_id} is not in the official Mojang manifest. Install it manually or provide its files."))?;
    let response = client.get(&entry.url).send().await.map_err(|e| format!("Could not retrieve parent metadata: {e}"))?;
    if !response.status().is_success() { return Err(format!("Parent metadata returned HTTP {}", response.status())); }
    let metadata: Value = response.json().await.map_err(|e| format!("Invalid parent metadata: {e}"))?;
    if metadata.get("id").and_then(Value::as_str) != Some(version_id) {
        return Err(format!("Mojang returned mismatched metadata for parent {version_id}"));
    }
    tokio::fs::create_dir_all(&version_dir).await.map_err(|e| e.to_string())?;
    let bytes = serde_json::to_vec_pretty(&metadata).map_err(|e| e.to_string())?;
    write_file_atomic(&metadata_path, &bytes).await?;

    let download = metadata.get("downloads").and_then(|v| v.get("client"))
        .ok_or_else(|| format!("Parent version {version_id} has no client download"))?;
    let url = download.get("url").and_then(Value::as_str).ok_or("Parent client URL is missing")?;
    download_verified(&client, url, &jar_path, download.get("sha1").and_then(Value::as_str)).await?;

    if let Some(libraries) = metadata.get("libraries").and_then(Value::as_array) {
        for library in libraries {
            if !library_allowed(library) { continue; }
            if let Some(artifact) = library.get("downloads").and_then(|v| v.get("artifact")) {
                if let (Some(url), Some(path)) = (artifact.get("url").and_then(Value::as_str), artifact.get("path").and_then(Value::as_str)) {
                    let destination = safe_join(&root.join("libraries"), path)?;
                    download_verified(&client, url, &destination, artifact.get("sha1").and_then(Value::as_str)).await?;
                }
            }
            if let Some(classifiers) = library.get("downloads").and_then(|v| v.get("classifiers")) {
                let native_key = library.get("natives").and_then(|v| v.get(launch_os_name())).and_then(Value::as_str).unwrap_or("");
                let arch = if std::env::consts::ARCH == "x86_64" { "64" } else { "32" };
                if !native_key.is_empty() {
                    if let Some(native) = classifiers.get(&native_key.replace("${arch}", arch)) {
                        if let (Some(url), Some(path)) = (native.get("url").and_then(Value::as_str), native.get("path").and_then(Value::as_str)) {
                            let destination = safe_join(&root.join("libraries"), path)?;
                            download_verified(&client, url, &destination, native.get("sha1").and_then(Value::as_str)).await?;
                        }
                    }
                }
            }
        }
    }

    if let Some(index) = metadata.get("assetIndex") {
        let id = index.get("id").and_then(Value::as_str).ok_or("Parent asset index ID is missing")?;
        let url = index.get("url").and_then(Value::as_str).ok_or("Parent asset index URL is missing")?;
        let index_path = root.join("assets").join("indexes").join(format!("{id}.json"));
        download_verified(&client, url, &index_path, index.get("sha1").and_then(Value::as_str)).await?;
        let data: Value = serde_json::from_slice(&tokio::fs::read(&index_path).await.map_err(|e| e.to_string())?)
            .map_err(|e| format!("Invalid parent asset index: {e}"))?;
        if let Some(objects) = data.get("objects").and_then(Value::as_object) {
            for object in objects.values() {
                let hash = object.get("hash").and_then(Value::as_str).ok_or("Parent asset hash is missing")?;
                if hash.len() != 40 || !hash.chars().all(|c| c.is_ascii_hexdigit()) { return Err("Parent asset index contains an invalid SHA-1 hash".into()); }
                let relative = format!("{}/{}", &hash[..2], hash);
                let destination = safe_join(&root.join("assets").join("objects"), &relative)?;
                let asset_url = format!("https://resources.download.minecraft.net/{}/{}", &hash[..2], hash);
                download_verified(&client, &asset_url, &destination, Some(hash)).await?;
            }
        }
    }
    if let Some(logging) = metadata.get("logging").and_then(|v| v.get("client")).and_then(|v| v.get("file")) {
        if let (Some(url), Some(id)) = (logging.get("url").and_then(Value::as_str), logging.get("id").and_then(Value::as_str)) {
            let destination = safe_join(&root.join("assets").join("log_configs"), id)?;
            download_verified(&client, url, &destination, logging.get("sha1").and_then(Value::as_str)).await?;
        }
    }
    Ok(())
}

async fn ensure_inherited_parents(root: &Path, version_id: &str) -> Result<(), String> {
    let mut current = version_id.to_string();
    let mut seen = HashSet::new();
    for _ in 0..32 {
        if !seen.insert(current.clone()) {
            return Err(format!("Version inheritance cycle detected at {current}"));
        }
        let path = root.join("versions").join(&current).join(format!("{current}.json"));
        let bytes = tokio::fs::read(&path).await
            .map_err(|e| format!("Could not read version metadata for {current}: {e}"))?;
        let metadata: Value = serde_json::from_slice(&bytes)
            .map_err(|e| format!("Invalid metadata for version {current}: {e}"))?;
        let Some(parent) = metadata.get("inheritsFrom").and_then(Value::as_str) else { return Ok(()); };
        if !valid_version_id(parent) { return Err(format!("Version {current} contains an invalid inheritsFrom ID")); }
        let parent_path = root.join("versions").join(parent).join(format!("{parent}.json"));
        let parent_jar = root.join("versions").join(parent).join(format!("{parent}.jar"));
        if !parent_path.is_file() || !parent_jar.is_file() {
            install_official_parent(root, parent).await?;
        }
        current = parent.to_string();
    }
    Err("Version inheritance exceeds the 32-version safety limit".into())
}

struct RunningGame {
    child: Child,
    version_id: String,
    username: String,
    started_at: u64,
}
static RUNNING_GAMES: OnceLock<Mutex<HashMap<u32, RunningGame>>> = OnceLock::new();

fn running_games() -> &'static Mutex<HashMap<u32, RunningGame>> {
    RUNNING_GAMES.get_or_init(|| Mutex::new(HashMap::new()))
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct RunningGameInfo {
    pid: u32,
    version_id: String,
    username: String,
    started_at: u64,
}

#[tauri::command]
fn list_running_games() -> Result<Vec<RunningGameInfo>, String> {
    let mut games = running_games().lock().map_err(|_| "Running game registry is unavailable".to_string())?;
    let mut finished = Vec::new();
    for (pid, game) in games.iter_mut() {
        if game.child.try_wait().map_err(|e| format!("Could not inspect Minecraft process {pid}: {e}"))?.is_some() {
            finished.push(*pid);
        }
    }
    for pid in finished { games.remove(&pid); }
    let mut result: Vec<_> = games.iter().map(|(pid, game)| RunningGameInfo {
        pid: *pid,
        version_id: game.version_id.clone(),
        username: game.username.clone(),
        started_at: game.started_at,
    }).collect();
    result.sort_by_key(|game| game.started_at);
    Ok(result)
}

#[tauri::command]
fn stop_running_game(pid: u32) -> Result<(), String> {
    let mut games = running_games().lock().map_err(|_| "Running game registry is unavailable".to_string())?;
    let game = games.get_mut(&pid).ok_or_else(|| format!("Minecraft process {pid} is not tracked or has already exited"))?;
    if let Some(status) = game.child.try_wait().map_err(|e| format!("Could not inspect Minecraft process {pid}: {e}"))? {
        games.remove(&pid);
        return Err(format!("Minecraft process {pid} has already exited ({status})"));
    }
    game.child.kill().map_err(|e| format!("Could not stop Minecraft process {pid}: {e}"))?;
    let _ = game.child.wait();
    games.remove(&pid);
    Ok(())
}

#[tauri::command]
async fn launch_version(version_id: String, game_directory: String, username: String, java_path: String, ram_mb: u32) -> Result<u32, String> {
    let username = username.trim();
    if username.len() < 3 || username.len() > 16 || !username.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') { return Err("Enter a valid offline username (3–16 letters, numbers, or underscores).".into()); }
    if !(1024..=16384).contains(&ram_mb) { return Err("RAM allocation must be between 1024 and 16384 MB".into()); }
    if !valid_version_id(&version_id) { return Err("Invalid version ID".into()); }
    let root = if game_directory.trim().is_empty() { dirs::data_dir().ok_or("Cannot locate application data directory")?.join("mflegend").join("game") } else { PathBuf::from(game_directory.trim()) };
    let version_dir = root.join("versions").join(&version_id);
    let metadata_path = version_dir.join(format!("{version_id}.json"));
    if !metadata_path.is_file() { return Err("Install this Minecraft version before playing.".into()); }
    ensure_inherited_parents(&root, &version_id).await?;
    let (metadata, base_jar_id) = resolve_version_metadata(&root, &version_id)?;
    let native_dir = version_dir.join("natives");
    tokio::fs::create_dir_all(&native_dir).await.map_err(|e| e.to_string())?;
    let mut cp = Vec::<String>::new();
    let mut missing_libraries = Vec::<String>::new();
    if let Some(libs) = metadata.get("libraries").and_then(Value::as_array) {
        for lib in libs {
            if !library_allowed(lib) { continue; }
            let library_name = lib.get("name").and_then(Value::as_str).unwrap_or("unnamed library");
            if let Some(artifact) = lib.get("downloads").and_then(|v| v.get("artifact")) {
                if let Some(p) = artifact.get("path").and_then(Value::as_str) {
                    let full = safe_join(&root.join("libraries"), p)?;
                    if full.is_file() {
                        cp.push(full.display().to_string());
                    } else {
                        missing_libraries.push(format!("{library_name} ({p})"));
                    }
                } else {
                    missing_libraries.push(format!("{library_name} (artifact path missing from metadata)"));
                }
            } else {
                // Forge and older mod-loader profiles often use Maven coordinates instead of Mojang's downloads.artifact shape.
                let relative = maven_artifact_path(library_name)?;
                let full = safe_join(&root.join("libraries"), &relative)?;
                if full.is_file() {
                    if library_name.contains(":natives-") {
                        let dest = native_dir.clone();
                        tokio::task::spawn_blocking(move || extract_native_archive(&full, &dest)).await.map_err(|e| e.to_string())??;
                    } else {
                        cp.push(full.display().to_string());
                    }
                } else {
                    missing_libraries.push(format!("{library_name} ({relative})"));
                }
            }
            if let Some(classifiers) = lib.get("downloads").and_then(|v| v.get("classifiers")) {
                let key = lib.get("natives").and_then(|v| v.get(launch_os_name())).and_then(Value::as_str).unwrap_or("");
                let arch = if std::env::consts::ARCH == "x86_64" { "64" } else { "32" };
                if !key.is_empty() {
                    let classifier_key = key.replace("${arch}", arch);
                    if let Some(native) = classifiers.get(&classifier_key) {
                        if let Some(p) = native.get("path").and_then(Value::as_str) {
                            let full = safe_join(&root.join("libraries"), p)?;
                            if full.is_file() {
                                let dest = native_dir.clone();
                                tokio::task::spawn_blocking(move || extract_native_archive(&full, &dest)).await.map_err(|e| e.to_string())??;
                            } else {
                                missing_libraries.push(format!("{library_name} native ({p})"));
                            }
                        } else {
                            missing_libraries.push(format!("{library_name} native (classifier path missing)"));
                        }
                    } else {
                        missing_libraries.push(format!("{library_name} native (missing classifier {classifier_key})"));
                    }
                }
            }
        }
    }
    if !missing_libraries.is_empty() {
        let shown = missing_libraries.iter().take(5).cloned().collect::<Vec<_>>().join(", ");
        let remaining = missing_libraries.len().saturating_sub(5);
        let suffix = if remaining > 0 { format!(" and {remaining} more") } else { String::new() };
        return Err(format!(
            "This installation is incomplete: {} required library file(s) are missing: {shown}{suffix}. Open Installations and reinstall this version.",
            missing_libraries.len()
        ));
    }
    let jar = root.join("versions").join(&base_jar_id).join(format!("{base_jar_id}.jar"));
    if !jar.is_file() {
        return Err(format!("Minecraft client JAR for base version {base_jar_id} is missing. Install that parent version first."));
    }
    cp.push(jar.display().to_string());
    let cp_value = cp.join(if cfg!(windows) { ";" } else { ":" });
    let assets = root.join("assets");
    let asset_index = metadata.get("assets").and_then(Value::as_str).or_else(|| metadata.get("assetIndex").and_then(|v| v.get("id")).and_then(Value::as_str)).unwrap_or("");
    let mut vars = std::collections::HashMap::<String, String>::new();
    vars.insert("${auth_player_name}".into(), username.into());
    vars.insert("${version_name}".into(), version_id.clone());
    vars.insert("${game_directory}".into(), root.display().to_string());
    vars.insert("${assets_root}".into(), assets.display().to_string());
    vars.insert("${assets_index_name}".into(), asset_index.into());
    vars.insert("${auth_uuid}".into(), offline_uuid(username));
    vars.insert("${auth_access_token}".into(), "0".into());
    vars.insert("${auth_session}".into(), "0".into());
    vars.insert("${user_properties}".into(), "{}".into());
    vars.insert("${user_type}".into(), "legacy".into());
    vars.insert("${version_type}".into(), "release".into());
    vars.insert("${natives_directory}".into(), native_dir.display().to_string());
    vars.insert("${launcher_name}".into(), "MFLegend".into());
    vars.insert("${launcher_version}".into(), "0.1.0".into());
    vars.insert("${classpath}".into(), cp_value.clone());
    vars.insert("${classpath_separator}".into(), if cfg!(windows) { ";" } else { ":" }.into());
    vars.insert("${library_directory}".into(), root.join("libraries").display().to_string());
    let logging_file = metadata.get("logging").and_then(|v| v.get("client")).and_then(|v| v.get("file"));
    if let Some(id) = logging_file.and_then(|v| v.get("id")).and_then(Value::as_str) {
        let path = safe_join(&assets.join("log_configs"), id)?;
        vars.insert("${path}".into(), path.display().to_string());
    }
    vars.insert("${clientid}".into(), "".into());
    vars.insert("${auth_xuid}".into(), "".into());
    vars.insert("${resolution_width}".into(), "1280".into());
    vars.insert("${resolution_height}".into(), "720".into());
    let main = metadata.get("mainClass").and_then(Value::as_str).ok_or("Minecraft mainClass missing")?;
    let mut args = vec![format!("-Xmx{}M", ram_mb), format!("-Xms{}M", std::cmp::min(1024, ram_mb)), format!("-Djava.library.path={}", native_dir.display()), "-cp".into(), cp_value, main.into()];
    if let Some(logging) = metadata.get("logging").and_then(|v| v.get("client")) {
        if let Some(argument) = logging.get("argument").and_then(Value::as_str) {
            let mut resolved = argument.to_string();
            for (key, value) in &vars { resolved = resolved.replace(key, value); }
            if !resolved.contains("${") { args.insert(0, resolved); }
        }
    }
    if let Some(jvm) = metadata.get("arguments").and_then(|v| v.get("jvm")) {
        let mut extra = Vec::new();
        for mut a in collect_launch_args(jvm) {
            for (k, v) in &vars { a = a.replace(k, v); }
            if !a.contains("${") && a != "-cp" && a != "-classpath" && !a.starts_with("-Xmx") && !a.starts_with("-Xms") { extra.push(a); }
        }
        args.splice(0..0, extra);
    }
    if let Some(game) = metadata.get("arguments").and_then(|v| v.get("game")) {
        for mut a in collect_launch_args(game) {
            for (k, v) in &vars { a = a.replace(k, v); }
            if !a.contains("${") { args.push(a); }
        }
    } else if let Some(legacy) = metadata.get("minecraftArguments").and_then(Value::as_str) {
        for mut a in tokenize_legacy_arguments(legacy)? {
            for (k, v) in &vars { a = a.replace(k, v); }
            if !a.contains("${") { args.push(a); }
        }
    } else { return Err("Minecraft launch arguments missing".into()); }
    let java = if java_path.trim().is_empty() { "java".to_string() } else { java_path };
    let java_probe = Command::new(&java).arg("-version").output()
        .map_err(|e| format!("Could not inspect Java at {java}: {e}"))?;
    let java_text = format!("{}\n{}", String::from_utf8_lossy(&java_probe.stderr), String::from_utf8_lossy(&java_probe.stdout));
    let detected_major = java_major_from_output(&java_text)
        .ok_or_else(|| format!("Could not determine the Java version reported by {java}"))?;
    let required_major = metadata.get("javaVersion")
        .and_then(|v| v.get("majorVersion"))
        .and_then(Value::as_u64)
        .map(|v| v as u32);
    if let Some(required) = required_major {
        if detected_major < required {
            return Err(format!("Minecraft {version_id} requires Java {required} or newer, but {java} reports Java {detected_major}. Select a compatible Java runtime in Settings."));
        }
    }
    if !java_probe.status.success() {
        return Err(format!("Java runtime check failed for {java}: {}", java_text.trim()));
    }
    let child = Command::new(&java).args(&args).current_dir(&root).spawn().map_err(|e| format!("Could not start Java at {java}: {e}"))?;
    let pid = child.id();
    let started_at = SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs();
    running_games().lock().map_err(|_| "Running game registry is unavailable".to_string())?.insert(pid, RunningGame { child, version_id: version_id.clone(), username: username.to_string(), started_at });
    Ok(pid)
}


#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ModEntry { file_name: String, enabled: bool, size_bytes: u64 }

fn configured_game_root(game_directory: &str) -> Result<PathBuf, String> {
    if game_directory.trim().is_empty() {
        Ok(dirs::data_dir().ok_or("Cannot locate application data directory")?.join("mflegend").join("game"))
    } else { Ok(PathBuf::from(game_directory.trim())) }
}
fn valid_mod_filename(name: &str) -> bool {
    !name.is_empty() && name.len() <= 240 &&
        name.chars().all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-' | ' ' | '(' | ')'))
}
#[tauri::command]
fn list_mods(game_directory: String) -> Result<Vec<ModEntry>, String> {
    let dir = configured_game_root(&game_directory)?.join("mods");
    if !dir.exists() { return Ok(Vec::new()); }
    let mut mods = Vec::new();
    for entry in std::fs::read_dir(&dir).map_err(|e| format!("Cannot read mods folder: {e}"))? {
        let entry = entry.map_err(|e| e.to_string())?;
        if !entry.path().is_file() { continue; }
        let name = entry.file_name().to_string_lossy().to_string();
        let lower = name.to_ascii_lowercase();
        let enabled = lower.ends_with(".jar") || lower.ends_with(".zip");
        let disabled = lower.ends_with(".jar.disabled") || lower.ends_with(".zip.disabled");
        if !enabled && !disabled { continue; }
        let size_bytes = entry.metadata().map_err(|e| e.to_string())?.len();
        mods.push(ModEntry { file_name: name, enabled, size_bytes });
    }
    mods.sort_by(|a,b| a.file_name.to_lowercase().cmp(&b.file_name.to_lowercase()));
    Ok(mods)
}
#[tauri::command]
fn import_mod(source_path: String, game_directory: String) -> Result<ModEntry, String> {
    let source = PathBuf::from(source_path.trim());
    if !source.is_file() { return Err("Select an existing mod .jar or .zip file.".into()); }
    let name = source.file_name().and_then(|n| n.to_str()).ok_or("Invalid mod filename")?;
    let lower = name.to_ascii_lowercase();
    if !valid_mod_filename(name) || !(lower.ends_with(".jar") || lower.ends_with(".zip")) {
        return Err("Only .jar and .zip mod files with safe filenames are supported.".into());
    }
    let dir = configured_game_root(&game_directory)?.join("mods");
    std::fs::create_dir_all(&dir).map_err(|e| format!("Cannot create mods folder: {e}"))?;
    let destination = dir.join(name);
    if destination.exists() { return Err("A mod with that filename already exists.".into()); }
    std::fs::copy(&source, &destination).map_err(|e| format!("Could not import mod: {e}"))?;
    let size_bytes = std::fs::metadata(&destination).map_err(|e| e.to_string())?.len();
    Ok(ModEntry { file_name: name.to_string(), enabled: true, size_bytes })
}
#[tauri::command]
fn set_mod_enabled(file_name: String, enabled: bool, game_directory: String) -> Result<(), String> {
    if !valid_mod_filename(&file_name) { return Err("Invalid mod filename.".into()); }
    let dir = configured_game_root(&game_directory)?.join("mods");
    let source = dir.join(&file_name);
    let lower = file_name.to_ascii_lowercase();
    let (from, to) = if enabled {
        if !lower.ends_with(".jar.disabled") && !lower.ends_with(".zip.disabled") { return Err("This mod is already enabled.".into()); }
        (source, dir.join(&file_name[..file_name.len() - ".disabled".len()]))
    } else {
        if !(lower.ends_with(".jar") || lower.ends_with(".zip")) { return Err("This mod is already disabled.".into()); }
        (source, dir.join(format!("{file_name}.disabled")))
    };
    if !from.is_file() { return Err("Mod file was not found.".into()); }
    if to.exists() { return Err("Destination filename already exists.".into()); }
    std::fs::rename(from, to).map_err(|e| format!("Could not change mod state: {e}"))
}
#[tauri::command]
fn remove_mod(file_name: String, game_directory: String) -> Result<(), String> {
    if !valid_mod_filename(&file_name) { return Err("Invalid mod filename.".into()); }
    let path = configured_game_root(&game_directory)?.join("mods").join(&file_name);
    if !path.is_file() { return Err("Mod file was not found.".into()); }
    std::fs::remove_file(path).map_err(|e| format!("Could not remove mod: {e}"))
}


#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct ResourcePackEntry { file_name: String, enabled: bool, size_bytes: u64 }

fn valid_pack_filename(name: &str) -> bool {
    !name.is_empty() && name.len() <= 240 &&
        name.chars().all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_' | '-' | ' ' | '(' | ')'))
}
#[tauri::command]
fn list_resource_packs(game_directory: String) -> Result<Vec<ResourcePackEntry>, String> {
    let dir = configured_game_root(&game_directory)?.join("resourcepacks");
    if !dir.exists() { return Ok(Vec::new()); }
    let mut packs = Vec::new();
    for entry in std::fs::read_dir(&dir).map_err(|e| format!("Cannot read resource packs folder: {e}"))? {
        let entry = entry.map_err(|e| e.to_string())?;
        if !entry.path().is_file() { continue; }
        let name = entry.file_name().to_string_lossy().to_string();
        let lower = name.to_ascii_lowercase();
        let enabled = lower.ends_with(".zip");
        let disabled = lower.ends_with(".zip.disabled");
        if !enabled && !disabled { continue; }
        let size_bytes = entry.metadata().map_err(|e| e.to_string())?.len();
        packs.push(ResourcePackEntry { file_name: name, enabled, size_bytes });
    }
    packs.sort_by(|a,b| a.file_name.to_lowercase().cmp(&b.file_name.to_lowercase()));
    Ok(packs)
}
#[tauri::command]
fn import_resource_pack(source_path: String, game_directory: String) -> Result<ResourcePackEntry, String> {
    let source = PathBuf::from(source_path.trim());
    if !source.is_file() { return Err("Select an existing resource-pack .zip file.".into()); }
    let name = source.file_name().and_then(|n| n.to_str()).ok_or("Invalid resource-pack filename")?;
    if !valid_pack_filename(name) || !name.to_ascii_lowercase().ends_with(".zip") {
        return Err("Only .zip resource packs with safe filenames are supported.".into());
    }
    let dir = configured_game_root(&game_directory)?.join("resourcepacks");
    std::fs::create_dir_all(&dir).map_err(|e| format!("Cannot create resource packs folder: {e}"))?;
    let destination = dir.join(name);
    if destination.exists() { return Err("A resource pack with that filename already exists.".into()); }
    std::fs::copy(&source, &destination).map_err(|e| format!("Could not import resource pack: {e}"))?;
    let size_bytes = std::fs::metadata(&destination).map_err(|e| e.to_string())?.len();
    Ok(ResourcePackEntry { file_name: name.to_string(), enabled: true, size_bytes })
}
#[tauri::command]
fn set_resource_pack_enabled(file_name: String, enabled: bool, game_directory: String) -> Result<(), String> {
    if !valid_pack_filename(&file_name) { return Err("Invalid resource-pack filename.".into()); }
    let dir = configured_game_root(&game_directory)?.join("resourcepacks");
    let source = dir.join(&file_name);
    let lower = file_name.to_ascii_lowercase();
    let (from, to) = if enabled {
        if !lower.ends_with(".zip.disabled") { return Err("This resource pack is already enabled.".into()); }
        (source, dir.join(&file_name[..file_name.len() - ".disabled".len()]))
    } else {
        if !lower.ends_with(".zip") { return Err("This resource pack is already disabled.".into()); }
        (source, dir.join(format!("{file_name}.disabled")))
    };
    if !from.is_file() { return Err("Resource pack file was not found.".into()); }
    if to.exists() { return Err("Destination filename already exists.".into()); }
    std::fs::rename(from, to).map_err(|e| format!("Could not change resource pack state: {e}"))
}
#[tauri::command]
fn remove_resource_pack(file_name: String, game_directory: String) -> Result<(), String> {
    if !valid_pack_filename(&file_name) { return Err("Invalid resource-pack filename.".into()); }
    let path = configured_game_root(&game_directory)?.join("resourcepacks").join(&file_name);
    if !path.is_file() { return Err("Resource pack file was not found.".into()); }
    std::fs::remove_file(path).map_err(|e| format!("Could not remove resource pack: {e}"))
}



#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct SkinEntry { file_name: String, file_path: String, width: u32, height: u32, size_bytes: u64 }

fn valid_skin_filename(name: &str) -> bool {
    !name.is_empty() && name.len() <= 120 && name.to_ascii_lowercase().ends_with(".png")
        && name.chars().all(|c| c.is_ascii_alphanumeric() || matches!(c, ' ' | '_' | '-' | '.' | '(' | ')'))
        && name != "." && name != ".."
}

fn skin_dimensions(path: &Path) -> Result<(u32,u32,u64),String> {
    let meta = std::fs::symlink_metadata(path).map_err(|e| format!("Cannot inspect skin: {e}"))?;
    if !meta.is_file() || meta.file_type().is_symlink() { return Err("Skin must be a regular PNG file.".into()); }
    let bytes = std::fs::read(path).map_err(|e| format!("Cannot read skin PNG: {e}"))?;
    if bytes.len() < 24 || &bytes[0..8] != b"\x89PNG\r\n\x1a\n" { return Err("File is not a valid PNG image.".into()); }
    if &bytes[12..16] != b"IHDR" { return Err("PNG image is missing its IHDR header.".into()); }
    let width = u32::from_be_bytes(bytes[16..20].try_into().map_err(|_| "Invalid PNG width")?);
    let height = u32::from_be_bytes(bytes[20..24].try_into().map_err(|_| "Invalid PNG height")?);
    let valid_size = width >= 64 && width <= 1024 && height >= 32 && height <= 1024
        && width % 64 == 0 && (height == width || height * 2 == width);
    if !valid_size { return Err("Minecraft skin PNG must be 64×64 (modern) or 64×32 (classic), or an integer-scaled equivalent.".into()); }
    Ok((width,height,meta.len()))
}

#[tauri::command]
fn list_skins(game_directory: String) -> Result<Vec<SkinEntry>,String> {
    let folder = configured_game_root(&game_directory)?.join("skins");
    if !folder.exists() { return Ok(Vec::new()); }
    let mut result = Vec::new();
    for entry in std::fs::read_dir(&folder).map_err(|e| format!("Cannot read skins folder: {e}"))? {
        let entry = entry.map_err(|e| e.to_string())?;
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().to_string();
        if !valid_skin_filename(&name) { continue; }
        if let Ok((width,height,size_bytes)) = skin_dimensions(&path) {
            result.push(SkinEntry { file_name: name, file_path: path.to_string_lossy().to_string(), width,height,size_bytes });
        }
    }
    result.sort_by(|a,b| a.file_name.to_lowercase().cmp(&b.file_name.to_lowercase()));
    Ok(result)
}

#[tauri::command]
fn import_skin(source_path: String, game_directory: String) -> Result<SkinEntry,String> {
    let source = PathBuf::from(source_path.trim());
    let source_name = source.file_name().and_then(|n| n.to_str()).ok_or("Invalid skin filename")?;
    if !valid_skin_filename(source_name) { return Err("Choose a PNG file with a safe filename.".into()); }
    let (width,height,size_bytes) = skin_dimensions(&source)?;
    let folder = configured_game_root(&game_directory)?.join("skins");
    std::fs::create_dir_all(&folder).map_err(|e| e.to_string())?;
    let destination = folder.join(source_name);
    if destination.exists() { return Err("A skin with that filename already exists.".into()); }
    std::fs::copy(&source,&destination).map_err(|e| format!("Could not import skin: {e}"))?;
    Ok(SkinEntry { file_name: source_name.to_string(), file_path: destination.to_string_lossy().to_string(), width,height,size_bytes })
}

#[tauri::command]
fn remove_skin(file_name: String, game_directory: String) -> Result<(),String> {
    if !valid_skin_filename(&file_name) { return Err("Invalid skin filename.".into()); }
    let folder = configured_game_root(&game_directory)?.join("skins");
    let path = folder.join(&file_name);
    skin_dimensions(&path)?;
    std::fs::remove_file(path).map_err(|e| format!("Could not remove skin: {e}"))
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct WorldEntry { folder_name: String, display_name: String, size_bytes: u64, modified_unix: u64 }

fn valid_world_folder(name: &str) -> bool {
    !name.is_empty() && name.len() <= 180 &&
        name.chars().all(|c| c.is_ascii_alphanumeric() || matches!(c, ' ' | '_' | '-' | '.' | '(' | ')'))
        && name != "." && name != ".."
}

fn copy_world_tree(source: &Path, destination: &Path) -> Result<u64, String> {
    let metadata = std::fs::symlink_metadata(source).map_err(|e| format!("Cannot inspect {}: {e}", source.display()))?;
    if metadata.file_type().is_symlink() { return Err("World folders containing symbolic links cannot be copied.".into()); }
    if metadata.is_dir() {
        std::fs::create_dir_all(destination).map_err(|e| format!("Cannot create {}: {e}", destination.display()))?;
        let mut total = 0u64;
        for entry in std::fs::read_dir(source).map_err(|e| format!("Cannot read {}: {e}", source.display()))? {
            let entry = entry.map_err(|e| e.to_string())?;
            total = total.saturating_add(copy_world_tree(&entry.path(), &destination.join(entry.file_name()))?);
        }
        Ok(total)
    } else if metadata.is_file() {
        if let Some(parent) = destination.parent() { std::fs::create_dir_all(parent).map_err(|e| e.to_string())?; }
        std::fs::copy(source, destination).map_err(|e| format!("Could not copy {}: {e}", source.display()))?;
        Ok(metadata.len())
    } else { Err("World contains an unsupported filesystem entry.".into()) }
}

fn inspect_world(folder: &Path) -> Result<(u64, u64), String> {
    let root_meta = std::fs::symlink_metadata(folder).map_err(|e| format!("Cannot inspect world folder: {e}"))?;
    if !root_meta.is_dir() || root_meta.file_type().is_symlink() { return Err("World path must be a real directory, not a symbolic link.".into()); }
    if !folder.join("level.dat").is_file() { return Err("This folder does not contain a Minecraft level.dat file.".into()); }
    let mut bytes = 0u64;
    let mut newest = 0u64;
    fn walk(path: &Path, bytes: &mut u64, newest: &mut u64) -> Result<(), String> {
        for entry in std::fs::read_dir(path).map_err(|e| e.to_string())? {
            let entry = entry.map_err(|e| e.to_string())?;
            let meta = std::fs::symlink_metadata(entry.path()).map_err(|e| e.to_string())?;
            if meta.file_type().is_symlink() { continue; }
            if meta.is_dir() { walk(&entry.path(), bytes, newest)?; }
            else if meta.is_file() {
                *bytes = bytes.saturating_add(meta.len());
                if let Ok(t) = meta.modified() {
                    let secs = t.duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_secs();
                    *newest = (*newest).max(secs);
                }
            }
        }
        Ok(())
    }
    walk(folder, &mut bytes, &mut newest)?;
    Ok((bytes, newest))
}

#[tauri::command]
fn list_worlds(game_directory: String) -> Result<Vec<WorldEntry>, String> {
    let saves = configured_game_root(&game_directory)?.join("saves");
    if !saves.exists() { return Ok(Vec::new()); }
    let mut worlds = Vec::new();
    for entry in std::fs::read_dir(&saves).map_err(|e| format!("Cannot read worlds folder: {e}"))? {
        let entry = entry.map_err(|e| e.to_string())?;
        let path = entry.path();
        let meta = std::fs::symlink_metadata(&path).map_err(|e| e.to_string())?;
        if !meta.is_dir() || meta.file_type().is_symlink() { continue; }
        let name = entry.file_name().to_string_lossy().to_string();
        if let Ok((size_bytes, modified_unix)) = inspect_world(&path) {
            worlds.push(WorldEntry { folder_name: name.clone(), display_name: name, size_bytes, modified_unix });
        }
    }
    worlds.sort_by(|a,b| b.modified_unix.cmp(&a.modified_unix));
    Ok(worlds)
}

#[tauri::command]
fn import_world(source_path: String, game_directory: String) -> Result<WorldEntry, String> {
    let source = PathBuf::from(source_path.trim());
    if !source.is_dir() { return Err("Select an extracted Minecraft world folder containing level.dat.".into()); }
    let name = source.file_name().and_then(|n| n.to_str()).ok_or("Invalid world folder name")?;
    if !valid_world_folder(name) { return Err("World folder name contains unsupported characters.".into()); }
    let (size_bytes, modified_unix) = inspect_world(&source)?;
    let saves = configured_game_root(&game_directory)?.join("saves");
    std::fs::create_dir_all(&saves).map_err(|e| e.to_string())?;
    let destination = saves.join(name);
    if destination.exists() { return Err("A world with that folder name already exists.".into()); }
    if let Err(error) = copy_world_tree(&source, &destination) {
        let _ = std::fs::remove_dir_all(&destination);
        return Err(error);
    }
    Ok(WorldEntry { folder_name: name.to_string(), display_name: name.to_string(), size_bytes, modified_unix })
}

#[tauri::command]
fn export_world(folder_name: String, destination_path: String, game_directory: String) -> Result<String, String> {
    if !valid_world_folder(&folder_name) { return Err("Invalid world folder name.".into()); }
    let source = configured_game_root(&game_directory)?.join("saves").join(&folder_name);
    inspect_world(&source)?;
    let destination = PathBuf::from(destination_path.trim());
    if !destination.is_absolute() || destination.exists() { return Err("Choose an absolute path to a new, non-existing destination folder.".into()); }
    let source_real = std::fs::canonicalize(&source).map_err(|e| format!("Cannot resolve world folder: {e}"))?;
    if let Some(parent) = destination.parent() { std::fs::create_dir_all(parent).map_err(|e| e.to_string())?; }
    let parent_real = destination.parent().ok_or("Export destination must have a parent folder")?;
    let parent_real = std::fs::canonicalize(parent_real).map_err(|e| e.to_string())?;
    if parent_real.starts_with(&source_real) { return Err("Export destination cannot be inside the world being copied.".into()); }
    if let Err(error) = copy_world_tree(&source, &destination) {
        let _ = std::fs::remove_dir_all(&destination);
        return Err(error);
    }
    Ok(destination.display().to_string())
}

#[tauri::command]
fn backup_world(folder_name: String, game_directory: String) -> Result<String, String> {
    if !valid_world_folder(&folder_name) { return Err("Invalid world folder name.".into()); }
    let root = configured_game_root(&game_directory)?;
    let source = root.join("saves").join(&folder_name);
    inspect_world(&source)?;
    let stamp = std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_nanos();
    let backup_root = root.join("backups");
    std::fs::create_dir_all(&backup_root).map_err(|e| e.to_string())?;
    let destination = backup_root.join(format!("{folder_name}-{stamp}"));
    if let Err(error) = copy_world_tree(&source, &destination) {
        let _ = std::fs::remove_dir_all(&destination);
        return Err(error);
    }
    Ok(destination.display().to_string())
}

#[tauri::command]
fn remove_world(folder_name: String, game_directory: String) -> Result<(), String> {
    if !valid_world_folder(&folder_name) { return Err("Invalid world folder name.".into()); }
    let saves = configured_game_root(&game_directory)?.join("saves");
    let path = saves.join(&folder_name);
    inspect_world(&path)?;
    std::fs::remove_dir_all(path).map_err(|e| format!("Could not remove world: {e}"))
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct InstalledVersion {
    id: String,
    version_type: String,
    inherits_from: Option<String>,
    main_class: Option<String>,
}

#[tauri::command]
fn list_installed_versions(game_directory: String) -> Result<Vec<InstalledVersion>, String> {
    let root = configured_game_root(&game_directory)?.join("versions");
    if !root.exists() { return Ok(Vec::new()); }
    let mut entries = Vec::new();
    let dirs = std::fs::read_dir(&root).map_err(|e| format!("Cannot read versions directory: {e}"))?;
    for item in dirs {
        let item = item.map_err(|e| e.to_string())?;
        let path = item.path();
        if !path.is_dir() { continue; }
        let id = match path.file_name().and_then(|n| n.to_str()) { Some(v) if valid_version_id(v) => v.to_string(), _ => continue };
        let metadata_path = path.join(format!("{id}.json"));
        if !metadata_path.is_file() { continue; }
        let content = match std::fs::read_to_string(metadata_path) { Ok(v) => v, Err(_) => continue };
        let metadata: Value = match serde_json::from_str(&content) { Ok(v) => v, Err(_) => continue };
        entries.push(InstalledVersion {
            id,
            version_type: metadata.get("type").and_then(Value::as_str).unwrap_or("custom").to_string(),
            inherits_from: metadata.get("inheritsFrom").and_then(Value::as_str).map(str::to_string),
            main_class: metadata.get("mainClass").and_then(Value::as_str).map(str::to_string),
        });
    }
    entries.sort_by(|a,b| a.id.cmp(&b.id));
    Ok(entries)
}

#[tauri::command]
fn detect_java() -> Vec<JavaRuntime> {
    let mut candidates: Vec<PathBuf> = Vec::new();
    if let Ok(path) = std::env::var("PATH") { for dir in std::env::split_paths(&path) { candidates.push(dir.join("java")); } }
    for path in ["/usr/bin/java", "/usr/lib/jvm/default-java/bin/java", "/usr/lib/jvm/java-21-openjdk/bin/java", "/usr/lib/jvm/java-17-openjdk/bin/java", "/opt/java/bin/java"] { candidates.push(PathBuf::from(path)); }
    let mut seen = HashSet::new();
    let mut found = Vec::new();
    for path in candidates {
        if !path.is_file() { continue; }
        let canonical = path.canonicalize().unwrap_or(path.clone());
        if !seen.insert(canonical.clone()) { continue; }
        let output = match Command::new(&canonical).arg("-XshowSettings:properties").arg("-version").output() { Ok(v) => v, Err(_) => continue };
        if !output.status.success() { continue; }
        let text = format!("{}\n{}", String::from_utf8_lossy(&output.stderr), String::from_utf8_lossy(&output.stdout));
        let version = text.lines().find(|l| l.contains("java.version =")).and_then(|l| l.split('=').nth(1)).map(str::trim).unwrap_or("Unknown").to_string();
        if version == "Unknown" || parse_java_major(&version).is_none() { continue; }
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
    if preferences.microsoft_client_id.len() > 128 || preferences.microsoft_client_id.chars().any(char::is_whitespace) { return Err("Microsoft application client ID must be a compact identifier of at most 128 characters".into()); }
    if !preferences.accent.starts_with('#') || preferences.accent.len() != 7 { return Err("Invalid accent color".into()); }
    let mut profile_ids = HashSet::new();
    let mut profile_directories = HashSet::new();
    for profile in &preferences.installations {
        if profile.id.is_empty() || !profile.id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_') {
            return Err("Installation profile has an invalid ID".into());
        }
        if profile.name.trim().is_empty() || profile.name.len() > 64 {
            return Err("Installation profile names must contain 1–64 characters".into());
        }
        if !profile_ids.insert(profile.id.clone()) {
            return Err("Installation profile IDs must be unique".into());
        }
        if !profile.game_directory.trim().is_empty() {
            let directory = Path::new(&profile.game_directory);
            if !directory.is_absolute() { return Err("Installation game directories must be absolute paths".into()); }
            if !profile_directories.insert(directory.to_string_lossy().to_lowercase()) {
                return Err("Installation profiles must use separate game directories".into());
            }
        }
    }
    if !preferences.selected_installation_id.is_empty() && !profile_ids.contains(&preferences.selected_installation_id) {
        return Err("The selected installation profile does not exist".into());
    }
    let path = preferences_path()?;
    let temp = path.with_extension("json.tmp");
    let data = serde_json::to_vec_pretty(&preferences).map_err(|e| e.to_string())?;
    std::fs::write(&temp, data).map_err(|e| e.to_string())?;
    std::fs::rename(&temp, &path).map_err(|e| e.to_string())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![get_versions, detect_java, load_preferences, save_preferences, install_version, install_fabric, install_forge, list_mods, import_mod, set_mod_enabled, remove_mod, list_resource_packs, import_resource_pack, set_resource_pack_enabled, remove_resource_pack, list_worlds, import_world, export_world, backup_world, remove_world, list_installed_versions, list_skins, import_skin, remove_skin, launch_version, list_running_games, stop_running_game])
        .run(tauri::generate_context!())
        .expect("error while running MFLegend");
}


#[cfg(test)]
mod launch_argument_tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn java_version_parser_supports_legacy_modern_and_early_access_versions() {
        assert_eq!(parse_java_major("1.8.0_402"), Some(8));
        assert_eq!(parse_java_major("17.0.10"), Some(17));
        assert_eq!(parse_java_major("21-ea"), Some(21));
        assert_eq!(parse_java_major("22.0.1+8"), Some(22));
        assert_eq!(parse_java_major("unknown"), None);
    }

    #[test]
    fn java_output_parser_reads_quoted_runtime_version() {
        assert_eq!(java_major_from_output("openjdk version \"21.0.2\" 2024-01-16"), Some(21));
        assert_eq!(java_major_from_output("java version \"1.8.0_402\""), Some(8));
        assert_eq!(java_major_from_output("no version data"), None);
    }

    #[test]
    fn sha1_checksums_require_exactly_40_hexadecimal_characters() {
        assert!(valid_sha1("0123456789abcdef0123456789abcdef01234567"));
        assert!(valid_sha1("ABCDEF0123456789ABCDEF0123456789ABCDEF01"));
        assert!(!valid_sha1("0123456789abcdef"));
        assert!(!valid_sha1("g123456789abcdef0123456789abcdef01234567"));
        assert!(!valid_sha1(""));
    }

    #[test]
    fn inherited_metadata_combines_libraries_and_child_overrides() {
        let mut parent = json!({
            "mainClass":"parent.Main",
            "libraries":[{"name":"parent:lib:1"}],
            "arguments":{"jvm":["-Dparent=true"],"game":["--parent"]}
        });
        merge_inherited_metadata(&mut parent, json!({
            "inheritsFrom":"base",
            "mainClass":"child.Main",
            "libraries":[{"name":"child:lib:1"}],
            "arguments":{"jvm":["-Dchild=true"],"game":["--child"]}
        }));
        assert_eq!(parent["mainClass"], "child.Main");
        assert_eq!(parent["libraries"].as_array().unwrap().len(), 2);
        assert_eq!(parent["arguments"]["jvm"].as_array().unwrap().len(), 2);
        assert_eq!(parent["arguments"]["game"].as_array().unwrap().len(), 2);
        assert!(parent.get("inheritsFrom").is_none());
    }

    #[test]
    fn fabric_maven_coordinates_resolve_to_safe_paths() {
        assert_eq!(
            maven_artifact_path("net.fabricmc:fabric-loader:0.16.10").unwrap(),
            "net/fabricmc/fabric-loader/0.16.10/fabric-loader-0.16.10.jar"
        );
        assert_eq!(
            maven_artifact_path("org.example:runtime:1.2:natives-linux").unwrap(),
            "org/example/runtime/1.2/runtime-1.2-natives-linux.jar"
        );
        assert!(maven_artifact_path("../escape:lib:1").is_err());
    }

    #[test]
    fn mod_filenames_are_confined_and_supported_extensions_are_safe() {
        assert!(valid_mod_filename("sodium-fabric.jar"));
        assert!(valid_mod_filename("example mod.zip.disabled"));
        assert!(!valid_mod_filename("../outside.jar"));
        assert!(!valid_mod_filename("nested/mod.jar"));
        assert!(!valid_mod_filename("bad\\\\name.jar"));
    }

    #[test]
    fn resource_pack_filenames_are_confined_to_zip_archives() {
        assert!(valid_pack_filename("faithful-32x.zip"));
        assert!(valid_pack_filename("pack (updated).zip.disabled"));
        assert!(!valid_pack_filename("../outside.zip"));
        assert!(!valid_pack_filename("nested/pack.zip"));
        assert!(!valid_pack_filename("pack.jar"));
    }

    #[test]
    fn skin_filenames_are_confined_to_png_files() {
        assert!(valid_skin_filename("Alex Skin.png"));
        assert!(valid_skin_filename("my_skin-2.png"));
        assert!(!valid_skin_filename("../outside.png"));
        assert!(!valid_skin_filename("skin.jpg"));
        assert!(!valid_skin_filename("folder/skin.png"));
    }

    #[test]
    fn world_folder_names_are_safe() {
        assert!(valid_world_folder("Survival World 2026"));
        assert!(valid_world_folder("Build_(creative).backup"));
        assert!(!valid_world_folder("../outside"));
        assert!(!valid_world_folder("nested/world"));
        assert!(!valid_world_folder(".."));
    }

    #[test]
    fn version_ids_reject_path_traversal() {
        assert!(valid_version_id("1.21.1"));
        assert!(!valid_version_id("../outside"));
        assert!(!valid_version_id(""));
    }

    #[test]
    fn legacy_arguments_preserve_quoted_spaces() {
        let parsed = tokenize_legacy_arguments(r#"--username "Player One" --gameDir 'my worlds/save'"#).unwrap();
        assert_eq!(parsed, vec!["--username", "Player One", "--gameDir", "my worlds/save"]);
    }

    #[test]
    fn legacy_arguments_reject_unclosed_quotes() {
        assert!(tokenize_legacy_arguments(r#"--username "unfinished"#).is_err());
    }

    #[test]
    fn rules_apply_feature_flags_and_last_matching_rule() {
        let features = serde_json::Map::new();
        let rules = vec![
            json!({"action":"allow"}),
            json!({"action":"disallow","features":{"has_custom_resolution":true}}),
            json!({"action":"disallow","os":{"name":"not-this-platform"}})
        ];
        assert!(rules_allow(Some(&rules), &features));
        let denied = vec![
            json!({"action":"allow"}),
            json!({"action":"disallow","os":{"name":launch_os_name()}})
        ];
        assert!(!rules_allow(Some(&denied), &features));
    }

    #[test]
    fn feature_rules_require_matching_feature_values() {
        let mut features = serde_json::Map::new();
        features.insert("is_demo_user".into(), Value::Bool(true));
        let rules = vec![json!({"action":"allow","features":{"is_demo_user":true}})];
        assert!(rules_allow(Some(&rules), &features));
        assert!(!rules_allow(Some(&rules), &serde_json::Map::new()));
    }
}
