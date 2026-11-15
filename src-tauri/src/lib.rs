use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};
use serde_json::Value;
use std::{collections::HashSet, path::{Component, Path, PathBuf}, process::Command};

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
    #[serde(default)]
    selected_java_path: String,
    ram_mb: u32,
    game_directory: String,
    accent: String,
    theme: String,
}
impl Default for Preferences {
    fn default() -> Self {
        Self { selected_version: String::new(), selected_java_path: String::new(), ram_mb: 2048, game_directory: String::new(), accent: "#8b5cf6".into(), theme: "dark".into() }
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

async fn download_verified(client: &reqwest::Client, url: &str, destination: &Path, expected_sha1: Option<&str>) -> Result<bool, String> {
    if !url.starts_with("https://") { return Err("Refusing a non-HTTPS download URL".into()); }
    if let Some(parent) = destination.parent() { tokio::fs::create_dir_all(parent).await.map_err(|e| e.to_string())?; }
    if destination.is_file() {
        if let Some(expected) = expected_sha1 {
            let existing = tokio::fs::read(destination).await.map_err(|e| e.to_string())?;
            let digest = format!("{:x}", Sha1::digest(&existing));
            if digest.eq_ignore_ascii_case(expected) { return Ok(false); }
        }
    }
    let response = client.get(url).send().await.map_err(|e| format!("Download failed for {url}: {e}"))?;
    if !response.status().is_success() { return Err(format!("Download returned HTTP {} for {url}", response.status())); }
    let bytes = response.bytes().await.map_err(|e| format!("Could not read download: {e}"))?;
    if let Some(expected) = expected_sha1 {
        let digest = format!("{:x}", Sha1::digest(&bytes));
        if !digest.eq_ignore_ascii_case(expected) { return Err(format!("SHA-1 verification failed for {url}")); }
    }
    let temporary = destination.with_extension("mflegend-download");
    tokio::fs::write(&temporary, &bytes).await.map_err(|e| e.to_string())?;
    tokio::fs::rename(&temporary, destination).await.map_err(|e| {
        let _ = std::fs::remove_file(&temporary);
        format!("Could not finalize downloaded file: {e}")
    })?;
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
    tokio::fs::write(&metadata_path, metadata_bytes).await.map_err(|e| e.to_string())?;

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
        }
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

    let base_jar_id = chain.first().and_then(|(_, metadata)| metadata.get("jar"))
        .and_then(Value::as_str).map(str::to_string)
        .unwrap_or_else(|| chain.get(1).map(|(id, _)| id.clone()).unwrap_or_else(|| version_id.to_string()));
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
    let detected_major = java_text.lines()
        .find_map(|line| {
            let start = line.find('"')? + 1;
            let version = line.get(start..)?.split('"').next()?;
            let first = version.split('.').next()?;
            if first == "1" {
                version.split('.').nth(1)?.parse::<u32>().ok()
            } else {
                first.parse::<u32>().ok()
            }
        })
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
    Ok(child.id())
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
        .invoke_handler(tauri::generate_handler![get_versions, detect_java, load_preferences, save_preferences, install_version, launch_version])
        .run(tauri::generate_context!())
        .expect("error while running MFLegend");
}


#[cfg(test)]
mod launch_argument_tests {
    use super::*;
    use serde_json::json;

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
