package net.kdt.pojavlaunch;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Optional, data-only extension marketplace. Feature packages are JSON declarations;
 * they cannot load DEX, Java, shell commands, or replace launcher/runtime files.
 */
public final class FeatureStoreActivity extends BaseActivity {
    private static final String CATALOG_URL =
            "https://raw.githubusercontent.com/hsudais201-ops/myLauncher/main/feature-store/catalog.json";
    private static final String PUBLISH_URL =
            "https://github.com/hsudais201-ops/myLauncher/tree/main/feature-store";
    private static final int REQUEST_IMPORT = 7801;
    private static final int REQUEST_EXPORT = 7802;
    private static final int MAX_PACKAGE_BYTES = 1024 * 1024;
    private static final int MAX_CATALOG_BYTES = 1024 * 1024;
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{1,79}");
    private static final Pattern SAFE_HOST = Pattern.compile("[A-Za-z0-9._:-]{1,253}");
    private static final Pattern SAFE_ASSET = Pattern.compile("packages/[A-Za-z0-9._-]{1,100}\\.vcfeature");
    private static final Pattern SHA256 = Pattern.compile("[a-fA-F0-9]{64}");

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> catalog = new ArrayList<>();
    private LinearLayout pageBody;
    private LinearLayout pageRoot;
    private String currentPage = "browse";
    private JSONObject currentFeature;
    private JSONObject pendingExport;
    private boolean remoteCatalogLoaded;
    private int socialTab = 0;
    private String activeChatFriend;

    @Override
    public boolean setFullscreen() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadBundledCatalog();
        showBrowse();
        refreshRemoteCatalog();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private File installedDirectory() {
        return new File(new File(getFilesDir(), "feature-store"), "installed");
    }

    private File installedFile(String id) {
        if (!isSafeId(id)) throw new IllegalArgumentException("Invalid feature id");
        return new File(installedDirectory(), id + ".vcfeature");
    }

    private static boolean isSafeId(String id) {
        return id != null && SAFE_ID.matcher(id).matches() && !id.contains("..");
    }

    private void loadBundledCatalog() {
        try {
            JSONObject document = new JSONObject(readAsset("feature-store/catalog.json", MAX_CATALOG_BYTES));
            addCatalogEntries(document.optJSONArray("features"), false);
        } catch (Exception ignored) {
            // A bad optional catalog must not affect game launching or other launcher systems.
        }
    }

    private void refreshRemoteCatalog() {
        io.execute(() -> {
            try {
                String raw = fetchHttps(CATALOG_URL, MAX_CATALOG_BYTES);
                JSONObject document = new JSONObject(raw);
                if (document.optInt("schemaVersion", -1) != 1) {
                    throw new IllegalArgumentException("Unsupported catalog schema");
                }
                JSONArray entries = document.optJSONArray("features");
                if (entries == null || entries.length() > 500) {
                    throw new IllegalArgumentException("Invalid catalog feature list");
                }
                runOnUiThread(() -> {
                    addCatalogEntries(entries, true);
                    remoteCatalogLoaded = true;
                    if ("browse".equals(currentPage)) showBrowse();
                });
            } catch (Exception ignored) {
                // Keep bundled catalog available offline; remote browsing is best-effort.
            }
        });
    }

    private void addCatalogEntries(JSONArray entries, boolean replace) {
        if (entries == null) return;
        Map<String, JSONObject> merged = new LinkedHashMap<>();
        for (JSONObject current : catalog) merged.put(current.optString("id"), current);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (!validCatalogEntry(entry)) continue;
            String id = entry.optString("id");
            if (replace || !merged.containsKey(id)) merged.put(id, entry);
        }
        catalog.clear();
        catalog.addAll(merged.values());
        Collections.sort(catalog, Comparator.comparing(
                item -> item.optString("name"), String.CASE_INSENSITIVE_ORDER));
    }

    private boolean validCatalogEntry(JSONObject entry) {
        if (entry == null || !isSafeId(entry.optString("id"))) return false;
        if (empty(entry.optString("name")) || empty(entry.optString("author"))
                || empty(entry.optString("owner")) || empty(entry.optString("version"))) return false;
        if (!SHA256.matcher(entry.optString("sha256")).matches()) return false;
        String asset = entry.optString("asset", "");
        String url = entry.optString("packageUrl", "");
        return (SAFE_ASSET.matcher(asset).matches() || !empty(url))
                && (empty(url) || isHttpsUrl(url));
    }

    private void showBrowse() {
        currentPage = "browse";
        currentFeature = null;
        beginPage("Browse features", "Optional add-ons with their own screens. Core launcher systems stay installed.");
        addButtonRow("Refresh catalog", v -> {
            addText("Refreshing the public catalog…", 13, false);
            refreshRemoteCatalog();
            Toast.makeText(this, "Catalog refresh started", Toast.LENGTH_SHORT).show();
        }, "Import package", v -> importPackage());
        if (!remoteCatalogLoaded) {
            addText("Showing the bundled catalog. New community entries appear when the public catalog is reachable.", 12, false);
        }
        if (catalog.isEmpty()) {
            addText("No catalog entries are available yet. Connect to the internet or import a .vcfeature package.", 15, false);
            return;
        }
        for (JSONObject item : catalog) addCatalogCard(item);
    }

    private void addCatalogCard(JSONObject item) {
        boolean installed = isInstalled(item.optString("id"));
        LinearLayout card = newCard();
        addTextTo(card, item.optString("name", "Unnamed feature"), 18, true);
        addTextTo(card, "Version " + item.optString("version", "1.0.0"), 12, false);
        addTextTo(card, item.optString("description", ""), 14, false);
        addTextTo(card, "Owner: " + item.optString("owner", item.optString("author", "Unknown")), 12, false);
        LinearLayout actions = horizontalRow();
        if (installed) {
            addButtonTo(actions, "Open", v -> openInstalled(item.optString("id")));
            addButtonTo(actions, "Update", v -> installCatalogFeature(item));
            addButtonTo(actions, "Uninstall", v -> confirmUninstall(item.optString("id"), item.optString("name")));
        } else {
            addButtonTo(actions, "Install", v -> installCatalogFeature(item));
        }
        card.addView(actions);
        pageBody.addView(card);
    }

    private void showInstalled() {
        currentPage = "installed";
        currentFeature = null;
        beginPage("Installed features", "Every installed feature is listed here and opens its own screen.");
        addButtonRow("Import package", v -> importPackage(),
                "Create feature", v -> showCreate());
        List<JSONObject> installed = readInstalledFeatures();
        if (installed.isEmpty()) {
            addText("You have no installed features yet. Browse the catalog or import a .vcfeature file.", 15, false);
            return;
        }
        for (JSONObject item : installed) {
            LinearLayout card = newCard();
            addTextTo(card, item.optString("name", "Unnamed feature"), 18, true);
            addTextTo(card, "Version " + item.optString("version", "1.0.0"), 12, false);
            addTextTo(card, item.optString("description", ""), 14, false);
            addTextTo(card, "Owner: " + item.optString("owner", item.optString("author", "Unknown")), 12, false);
            LinearLayout actions = horizontalRow();
            addButtonTo(actions, "Open", v -> showFeature(item));
            addButtonTo(actions, "Export / share", v -> exportPackage(item));
            addButtonTo(actions, "Uninstall", v -> confirmUninstall(item.optString("id"), item.optString("name")));
            card.addView(actions);
            pageBody.addView(card);
        }
    }

    private void showCreate() {
        currentPage = "create";
        currentFeature = null;
        beginPage("Create a feature", "Make a small declarative add-on. Custom features do not execute uploaded code.");
        addText("Feature name", 14, true);
        EditText name = editField("Example: My server panel");
        pageBody.addView(name);
        addText("Owner / author", 14, true);
        EditText owner = editField("Your name or project name");
        pageBody.addView(owner);
        addText("Description", 14, true);
        EditText description = editField("What does your feature do?");
        pageBody.addView(description);
        addText("Screen type", 14, true);
        Spinner type = new Spinner(this);
        String[] screenTypes = new String[] {"Custom page", "Theme studio", "Server status", "Social hub (local preview)"};
        type.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, screenTypes));
        pageBody.addView(type, matchWrap());
        addButtonRow("Create and install", v -> {
            try {
                JSONObject feature = makeFeature(name.getText().toString(),
                        owner.getText().toString(), description.getText().toString(),
                        type.getSelectedItemPosition());
                saveInstalledFeature(feature);
                Toast.makeText(this, "Feature created and installed", Toast.LENGTH_LONG).show();
                showFeature(feature);
            } catch (Exception error) {
                showError("Could not create feature", error);
            }
        }, "Import existing", v -> importPackage());
        addText("To publish for other people, export/share the .vcfeature package and submit it to the public catalog through the repository review process.", 13, false);
        addButtonRow("Export catalog instructions", v -> openHttps(PUBLISH_URL),
                "Open public catalog", v -> openHttps(PUBLISH_URL));
        addText("Community packages are reviewed and listed in feature-store/catalog.json. The app does not embed a GitHub credential or silently upload to someone else's repository.", 12, false);
    }

    private JSONObject makeFeature(String rawName, String rawOwner, String rawDescription, int typeIndex)
            throws JSONException {
        String name = rawName == null ? "" : rawName.trim();
        String owner = rawOwner == null ? "" : rawOwner.trim();
        String description = rawDescription == null ? "" : rawDescription.trim();
        if (name.length() < 2 || name.length() > 80) throw new IllegalArgumentException("Feature name must be 2–80 characters");
        if (owner.length() < 2 || owner.length() > 80) throw new IllegalArgumentException("Enter an owner or author name");
        if (description.length() > 400) throw new IllegalArgumentException("Description cannot exceed 400 characters");

        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        slug = slug.replaceAll("^-+", "").replaceAll("-+$", "");
        if (slug.length() < 2) slug = "feature";
        if (slug.length() > 45) slug = slug.substring(0, 45).replaceAll("-+$", "");
        String id = "custom." + slug;
        int suffix = 2;
        while (installedFile(id).exists()) id = "custom." + slug + "-" + suffix++;

        JSONObject feature = new JSONObject();
        feature.put("schemaVersion", 1);
        feature.put("id", id);
        feature.put("name", name);
        feature.put("version", "1.0.0");
        feature.put("author", owner);
        feature.put("owner", owner);
        feature.put("description", description);
        JSONObject screen = new JSONObject();
        if (typeIndex == 1) {
            screen.put("type", "theme");
            screen.put("title", name);
            screen.put("description", empty(description) ? "Choose a launcher appearance preset." : description);
            screen.put("presets", defaultPresets());
        } else if (typeIndex == 2) {
            screen.put("type", "server_status");
            screen.put("title", name);
            screen.put("description", empty(description) ? "Check a Minecraft Java Edition server." : description);
            screen.put("defaultHost", "mc.hypixel.net");
        } else if (typeIndex == 3) {
            screen.put("type", "social");
            screen.put("mode", "local_preview");
            screen.put("title", name);
            screen.put("description", empty(description) ? "A mobile-first social interface preview. Profiles, requests and chats are stored locally; online delivery is not connected." : description);
        } else {
            screen.put("type", "page");
            screen.put("title", name);
            screen.put("description", empty(description) ? "Your custom feature page." : description);
            JSONArray widgets = new JSONArray();
            JSONObject intro = new JSONObject();
            intro.put("type", "text");
            intro.put("label", empty(description) ? "Welcome to this custom feature." : description);
            widgets.put(intro);
            screen.put("widgets", widgets);
        }
        feature.put("screen", screen);
        return validateFeature(feature);
    }

    private JSONArray defaultPresets() throws JSONException {
        JSONArray presets = new JSONArray();
        presets.put(themePreset("Night Violet", "#171526", "#8158E9", "#F4F0FF"));
        presets.put(themePreset("Ocean Blue", "#101D2E", "#2486E8", "#EAF4FF"));
        presets.put(themePreset("Forest Green", "#12251E", "#35A96B", "#ECFFF4"));
        return presets;
    }

    private JSONObject themePreset(String name, String background, String accent, String text) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("name", name);
        item.put("background", background);
        item.put("accent", accent);
        item.put("text", text);
        return item;
    }

    private void openInstalled(String id) {
        for (JSONObject feature : readInstalledFeatures()) {
            if (id.equals(feature.optString("id"))) {
                showFeature(feature);
                return;
            }
        }
        showBrowse();
    }

    private void showFeature(JSONObject feature) {
        try {
            currentPage = "detail";
            currentFeature = validateFeature(feature);
            JSONObject screen = currentFeature.getJSONObject("screen");
            beginPage(currentFeature.optString("name", "Feature"), currentFeature.optString("description", ""));
            addButtonRow("← Feature list", v -> showInstalled(),
                    "Export / share", v -> exportPackage(currentFeature));
            addText("Owner: " + currentFeature.optString("owner", currentFeature.optString("author")),
                    12, false);
            addText("Version " + currentFeature.optString("version", "1.0.0"), 12, false);
            String type = screen.optString("type", "page");
            if ("theme".equals(type)) {
                renderThemeScreen(screen);
            } else if ("server_status".equals(type)) {
                renderServerStatus(screen);
            } else if ("social".equals(type)) {
                renderSocialScreen(screen);
            } else {
                renderPageScreen(screen);
            }
        } catch (Exception error) {
            showError("This feature could not be opened", error);
        }
    }

    private void renderThemeScreen(JSONObject screen) {
        addText(screen.optString("description", "Choose a launcher palette."), 14, false);
        JSONArray presets = screen.optJSONArray("presets");
        if (presets == null || presets.length() == 0) {
            addText("This theme feature has no valid presets.", 13, false);
            return;
        }
        for (int i = 0; i < presets.length(); i++) {
            JSONObject preset = presets.optJSONObject(i);
            if (preset == null || !validHex(preset.optString("background"))
                    || !validHex(preset.optString("accent")) || !validHex(preset.optString("text"))) continue;
            final JSONObject choice = preset;
            LinearLayout card = newCard();
            addTextTo(card, choice.optString("name", "Theme"), 17, true);
            addTextTo(card, "Background " + choice.optString("background")
                    + " · Accent " + choice.optString("accent"), 12, false);
            Button apply = makeButton("Apply theme");
            apply.setOnClickListener(v -> {
                try {
                    FeatureTheme.applyPreset(this, choice.getString("background"),
                            choice.getString("accent"), choice.getString("text"));
                    FeatureTheme.apply(this);
                    Toast.makeText(this, "Theme applied to supported launcher UI", Toast.LENGTH_LONG).show();
                } catch (Exception error) {
                    showError("Could not apply theme", error);
                }
            });
            card.addView(apply);
            pageBody.addView(card);
        }
        addText("Theme changes affect supported launcher screens and buttons; the Minecraft renderer and in-game controls are left alone.", 12, false);
    }

    private void renderServerStatus(JSONObject screen) {
        addText(screen.optString("description", "Check a Minecraft Java Edition server."), 14, false);
        EditText host = editField(screen.optString("defaultHost", "example.org"));
        host.setSingleLine(true);
        pageBody.addView(host);
        TextView result = addText("Enter a server address and press Check status.", 14, false);
        Button check = makeButton("Check status");
        check.setOnClickListener(v -> {
            String address = host.getText().toString().trim();
            if (!SAFE_HOST.matcher(address).matches() || address.contains("..")) {
                result.setText("Enter a valid host name or host:port without spaces or a URL.");
                return;
            }
            check.setEnabled(false);
            result.setText("Checking " + address + "…");
            io.execute(() -> {
                String status;
                try {
                    JSONObject response = new JSONObject(fetchHttps(
                            "https://api.mcsrvstat.us/3/" + URLEncoder.encode(address, "UTF-8"), 256 * 1024));
                    if (!response.optBoolean("online", false)) {
                        status = "Offline or not reachable: " + address;
                    } else {
                        JSONObject players = response.optJSONObject("players");
                        JSONObject version = response.optJSONObject("version");
                        String count = players == null ? "Player count unavailable"
                                : players.optInt("online", 0) + " / " + players.optInt("max", 0) + " players";
                        String versionText = version == null ? "" : version.optString("name", "");
                        status = "ONLINE\n" + address + "\n" + count
                                + (empty(versionText) ? "" : "\nVersion: " + versionText);
                        JSONObject motd = response.optJSONObject("motd");
                        JSONArray clean = motd == null ? null : motd.optJSONArray("clean");
                        if (clean != null && clean.length() > 0) {
                            StringBuilder lines = new StringBuilder();
                            for (int i = 0; i < Math.min(clean.length(), 3); i++) {
                                if (lines.length() > 0) lines.append('\n');
                                lines.append(clean.optString(i));
                            }
                            status += "\n" + lines;
                        }
                    }
                } catch (Exception error) {
                    status = "Status lookup failed. Check the address and internet connection.";
                }
                final String finalStatus = status;
                runOnUiThread(() -> {
                    check.setEnabled(true);
                    result.setText(finalStatus);
                });
            });
        });
        pageBody.addView(check);
        addText("Status is fetched from the third-party mcsrvstat.us service. Do not enter passwords or account tokens.", 12, false);
    }

    private android.content.SharedPreferences socialPreferences() {
        return getSharedPreferences("voidcraft_feature_store", MODE_PRIVATE);
    }

    private String socialKey(String suffix) {
        String id = currentFeature == null ? "social" : currentFeature.optString("id", "social");
        return "social." + id + "." + suffix;
    }

    private JSONArray socialArray(String name) {
        try {
            return new JSONArray(socialPreferences().getString(socialKey(name), "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private void saveSocialArray(String name, JSONArray values) {
        socialPreferences().edit().putString(socialKey(name), values.toString()).apply();
    }

    private boolean socialContainsUser(JSONArray entries, String username) {
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry != null && username.equalsIgnoreCase(entry.optString("username", ""))) return true;
        }
        return false;
    }

    private boolean validSocialUsername(String username) {
        return username != null && Pattern.matches("[A-Za-z0-9_]{3,16}", username);
    }

    private void showSocialProviderNotice(String provider) {
        new AlertDialog.Builder(this)
                .setTitle(provider + " sign-in")
                .setMessage("This provider button is a UI placeholder in the Social Hub preview. No password or token is requested or saved. Provider OAuth, account linking, server-side verification, and online friend/chat delivery need a configured authentication backend. Your existing Minecraft/Microsoft launcher sign-in remains separate and unchanged.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void renderSocialScreen(JSONObject screen) {
        addText("SOCIAL HUB  ·  PREVIEW", 12, true);
        addText("Profiles, friend requests, contacts and message screens. This install currently stores preview data on this device only; nothing is sent to another player.", 13, false);
        addButtonRow("Profile", v -> { socialTab = 0; showFeature(currentFeature); },
                "Friends", v -> { socialTab = 1; showFeature(currentFeature); });
        addButtonRow("Requests", v -> { socialTab = 2; showFeature(currentFeature); },
                "Chats", v -> { socialTab = 3; showFeature(currentFeature); });
        addButtonRow("← Previous", v -> { socialTab = (socialTab + 3) % 4; showFeature(currentFeature); },
                "Swipe → Next", v -> { socialTab = (socialTab + 1) % 4; showFeature(currentFeature); });

        if (socialTab == 0) renderSocialProfile();
        else if (socialTab == 1) renderSocialFriends();
        else if (socialTab == 2) renderSocialRequests();
        else renderSocialChats();

        addText("Online-service status: not connected. Microsoft/Xbox, Google and other-provider sign-in buttons are not live authentication yet.", 12, false);
    }

    private void renderSocialProfile() {
        addText("Social profile", 19, true);
        EditText username = editField("Username: 3–16 letters, numbers or underscores");
        username.setSingleLine(true);
        username.setText(socialPreferences().getString(socialKey("username"), ""));
        pageBody.addView(username);
        addButtonRow("Save username", v -> {
            String value = username.getText().toString().trim();
            if (!validSocialUsername(value)) {
                Toast.makeText(this, "Use 3–16 letters, numbers, or underscores.", Toast.LENGTH_LONG).show();
                return;
            }
            socialPreferences().edit().putString(socialKey("username"), value).apply();
            Toast.makeText(this, "Saved local Social Hub profile", Toast.LENGTH_LONG).show();
            showFeature(currentFeature);
        });
        addText("This username belongs to the add-on preview. It does not alter the selected Minecraft profile, Microsoft gamertag, or official account.", 12, false);
        addText("Account providers", 16, true);
        LinearLayout providers = horizontalRow();
        addButtonTo(providers, "Microsoft / Xbox", v -> showSocialProviderNotice("Microsoft / Xbox"));
        addButtonTo(providers, "Google", v -> showSocialProviderNotice("Google"));
        pageBody.addView(providers);
        addButtonRow("Other account provider", v -> showSocialProviderNotice("Other account provider"));
        addText("Real account sign-in requires provider registration, secure token exchange, and server-side account linking. Never paste passwords or tokens into this preview.", 12, false);
    }

    private void renderSocialFriends() {
        addText("Friends", 19, true);
        JSONArray friends = socialArray("friends");
        if (friends.length() == 0) addText("No contacts yet. Add a demo contact to try the friend and chat screens.", 13, false);
        for (int i = 0; i < Math.min(friends.length(), 100); i++) {
            JSONObject friend = friends.optJSONObject(i);
            if (friend == null) continue;
            String name = friend.optString("username", "");
            LinearLayout card = newCard();
            addTextTo(card, "@" + name, 17, true);
            addTextTo(card, "Local preview contact · not an online presence indicator", 12, false);
            LinearLayout actions = horizontalRow();
            addButtonTo(actions, "Open chat", v -> {
                activeChatFriend = name;
                socialTab = 3;
                showFeature(currentFeature);
            });
            addButtonTo(actions, "Remove friend", v -> confirmRemoveSocialFriend(name));
            card.addView(actions);
        }

        addText("Find player / send friend request", 16, true);
        EditText target = editField("Player username");
        target.setSingleLine(true);
        pageBody.addView(target);
        addButtonRow("Create local request preview", v -> {
            String name = target.getText().toString().trim();
            if (!validSocialUsername(name)) {
                Toast.makeText(this, "Enter a username with 3–16 letters, numbers, or underscores.", Toast.LENGTH_LONG).show();
                return;
            }
            String own = socialPreferences().getString(socialKey("username"), "");
            if (!empty(own) && own.equalsIgnoreCase(name)) {
                Toast.makeText(this, "You cannot send a friend request to your own username.", Toast.LENGTH_LONG).show();
                return;
            }
            JSONArray currentFriends = socialArray("friends");
            JSONArray requests = socialArray("requests");
            if (socialContainsUser(currentFriends, name) || socialContainsUser(requests, name)) {
                Toast.makeText(this, "That username is already a contact or pending request.", Toast.LENGTH_LONG).show();
                return;
            }
            if (requests.length() >= 100) {
                Toast.makeText(this, "The preview is limited to 100 pending requests.", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                JSONObject request = new JSONObject();
                request.put("username", name);
                request.put("direction", "outgoing");
                request.put("state", "pending");
                requests.put(request);
                saveSocialArray("requests", requests);
                socialTab = 2;
                showFeature(currentFeature);
            } catch (JSONException error) {
                showError("Could not create preview request", error);
            }
        });
        addButtonRow("Add demo contact", v -> addDemoSocialContact());
        addText("Requests created here are saved locally for UI testing only. They are not delivered to the named player.", 12, false);
    }

    private void renderSocialRequests() {
        addText("Friend requests", 19, true);
        JSONArray requests = socialArray("requests");
        if (requests.length() == 0) addText("No pending preview requests.", 13, false);
        for (int i = 0; i < Math.min(requests.length(), 100); i++) {
            JSONObject request = requests.optJSONObject(i);
            if (request == null) continue;
            final String name = request.optString("username", "");
            final String direction = request.optString("direction", "outgoing");
            LinearLayout card = newCard();
            addTextTo(card, "@" + name, 17, true);
            addTextTo(card, ("incoming".equals(direction) ? "Incoming demo request" : "Outgoing demo request")
                    + " · Pending locally", 12, false);
            LinearLayout actions = horizontalRow();
            if ("incoming".equals(direction)) {
                addButtonTo(actions, "Accept", v -> resolveSocialRequest(name, true));
                addButtonTo(actions, "Decline", v -> resolveSocialRequest(name, false));
            } else {
                addButtonTo(actions, "Cancel request", v -> resolveSocialRequest(name, false));
            }
            card.addView(actions);
        }
        addText("Preview an incoming request", 16, true);
        EditText incoming = editField("Incoming player username");
        incoming.setSingleLine(true);
        pageBody.addView(incoming);
        addButtonRow("Add incoming demo request", v -> {
            String name = incoming.getText().toString().trim();
            if (!validSocialUsername(name)) {
                Toast.makeText(this, "Enter a username with 3–16 letters, numbers, or underscores.", Toast.LENGTH_LONG).show();
                return;
            }
            if (socialContainsUser(socialArray("friends"), name) || socialContainsUser(socialArray("requests"), name)) {
                Toast.makeText(this, "That username is already a contact or request.", Toast.LENGTH_LONG).show();
                return;
            }
            JSONArray requestsNow = socialArray("requests");
            if (requestsNow.length() >= 100) {
                Toast.makeText(this, "The preview is limited to 100 pending requests.", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                JSONObject request = new JSONObject();
                request.put("username", name);
                request.put("direction", "incoming");
                request.put("state", "pending");
                requestsNow.put(request);
                saveSocialArray("requests", requestsNow);
                showFeature(currentFeature);
            } catch (JSONException error) {
                showError("Could not create demo request", error);
            }
        });
        addText("Incoming requests are simulated on this device so you can test Accept and Decline. They do not originate from another account.", 12, false);
    }

    private void resolveSocialRequest(String username, boolean accept) {
        JSONArray requests = socialArray("requests");
        JSONArray remaining = new JSONArray();
        String direction = "";
        try {
            for (int i = 0; i < requests.length(); i++) {
                JSONObject item = requests.optJSONObject(i);
                if (item == null) continue;
                if (username.equalsIgnoreCase(item.optString("username", ""))) {
                    direction = item.optString("direction", "");
                    continue;
                }
                remaining.put(item);
            }
            if (accept && "incoming".equals(direction)) {
                JSONArray friends = socialArray("friends");
                if (!socialContainsUser(friends, username)) {
                    JSONObject friend = new JSONObject();
                    friend.put("username", username);
                    friends.put(friend);
                    saveSocialArray("friends", friends);
                }
            }
            saveSocialArray("requests", remaining);
            showFeature(currentFeature);
        } catch (JSONException error) {
            showError("Could not update friend request", error);
        }
    }

    private void addDemoSocialContact() {
        JSONArray friends = socialArray("friends");
        String username = "DemoPlayer";
        int suffix = 2;
        while (socialContainsUser(friends, username)) username = "DemoPlayer" + suffix++;
        try {
            JSONObject friend = new JSONObject();
            friend.put("username", username);
            friends.put(friend);
            saveSocialArray("friends", friends);
            activeChatFriend = username;
            socialTab = 1;
            showFeature(currentFeature);
        } catch (JSONException error) {
            showError("Could not create demo contact", error);
        }
    }

    private void confirmRemoveSocialFriend(String username) {
        new AlertDialog.Builder(this)
                .setTitle("Remove friend")
                .setMessage("Remove @" + username + " from this device's Social Hub preview?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    JSONArray friends = socialArray("friends");
                    JSONArray remaining = new JSONArray();
                    for (int i = 0; i < friends.length(); i++) {
                        JSONObject friend = friends.optJSONObject(i);
                        if (friend != null && !username.equalsIgnoreCase(friend.optString("username", ""))) remaining.put(friend);
                    }
                    saveSocialArray("friends", remaining);
                    if (username.equalsIgnoreCase(activeChatFriend)) activeChatFriend = null;
                    showFeature(currentFeature);
                }).show();
    }

    private void renderSocialChats() {
        addText("Chats", 19, true);
        JSONArray friends = socialArray("friends");
        if (empty(activeChatFriend) || !socialContainsUser(friends, activeChatFriend)) {
            addText("Choose a contact to open its conversation preview.", 13, false);
            for (int i = 0; i < Math.min(friends.length(), 100); i++) {
                JSONObject friend = friends.optJSONObject(i);
                if (friend == null) continue;
                String name = friend.optString("username", "");
                addButtonRow("Chat with @" + name, v -> {
                    activeChatFriend = name;
                    showFeature(currentFeature);
                });
            }
            addButtonRow("Add demo contact", v -> addDemoSocialContact());
            return;
        }

        final String conversationFriend = activeChatFriend;
        addTextTo(pageBody, "Conversation with @" + conversationFriend, 17, true);
        addButtonRow("Choose another friend", v -> { activeChatFriend = null; showFeature(currentFeature); });
        String messageKey = "messages." + conversationFriend.toLowerCase(Locale.ROOT);
        JSONArray messages = socialArray(messageKey);
        if (messages.length() == 0) addText("No preview messages yet. Your messages stay on this device.", 13, false);
        for (int i = Math.max(0, messages.length() - 100); i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            String from = message.optString("from", "");
            String body = message.optString("text", "");
            addText((from.equalsIgnoreCase(socialPreferences().getString(socialKey("username"), "")) ? "You" : "@" + from)
                    + ": " + body, 14, false);
        }
        EditText composer = editField("Write a local preview message…");
        pageBody.addView(composer);
        addButtonRow("Send local preview message", v -> {
            String body = composer.getText().toString().trim();
            if (empty(body)) {
                Toast.makeText(this, "Type a message first.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (body.length() > 1000) {
                Toast.makeText(this, "Preview messages are limited to 1,000 characters.", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                JSONArray currentMessages = socialArray(messageKey);
                JSONObject message = new JSONObject();
                String own = socialPreferences().getString(socialKey("username"), "Me");
                message.put("from", empty(own) ? "Me" : own);
                message.put("text", body);
                message.put("createdAt", System.currentTimeMillis());
                currentMessages.put(message);
                JSONArray bounded = new JSONArray();
                for (int i = Math.max(0, currentMessages.length() - 200); i < currentMessages.length(); i++) {
                    bounded.put(currentMessages.get(i));
                }
                saveSocialArray(messageKey, bounded);
                showFeature(currentFeature);
            } catch (JSONException error) {
                showError("Could not save preview message", error);
            }
        });
        addText("Messages are saved as local UI previews, not delivered to the other contact.", 12, false);
    }

    private void renderPageScreen(JSONObject screen) {
        addText(screen.optString("description", "Feature page"), 14, false);
        JSONArray widgets = screen.optJSONArray("widgets");
        if (widgets == null) {
            addText("This page has no widgets.", 13, false);
            return;
        }
        for (int i = 0; i < Math.min(widgets.length(), 100); i++) {
            JSONObject widget = widgets.optJSONObject(i);
            if (widget == null) continue;
            String type = widget.optString("type", "text");
            if ("text".equals(type)) {
                addText(widget.optString("label", ""), 14, false);
            } else if ("link".equals(type)) {
                String label = widget.optString("label", "Open link");
                String url = widget.optString("url", "");
                addButtonRow(label, v -> openHttps(url));
            } else if ("button".equals(type)) {
                String label = widget.optString("label", "Action");
                JSONObject action = widget.optJSONObject("action");
                addButtonRow(label, v -> runWidgetAction(action));
            } else if ("toggle".equals(type)) {
                String label = widget.optString("label", "Setting");
                String settingId = widget.optString("id", "toggle-" + i);
                if (!isSafeId(settingId)) continue;
                Switch toggle = new Switch(this);
                toggle.setText(label);
                toggle.setTextColor(Color.parseColor("#F4F0FF"));
                toggle.setChecked(getSharedPreferences("voidcraft_feature_store", MODE_PRIVATE)
                        .getBoolean("widget." + currentFeature.optString("id") + "." + settingId, false));
                toggle.setOnCheckedChangeListener((button, checked) ->
                        getSharedPreferences("voidcraft_feature_store", MODE_PRIVATE).edit()
                                .putBoolean("widget." + currentFeature.optString("id") + "." + settingId, checked)
                                .apply());
                pageBody.addView(toggle);
            }
        }
    }

    private void runWidgetAction(JSONObject action) {
        if (action == null) {
            Toast.makeText(this, "This feature has no supported action", Toast.LENGTH_SHORT).show();
            return;
        }
        String type = action.optString("type", "");
        try {
            if ("open_url".equals(type)) {
                openHttps(action.optString("url", ""));
            } else if ("set_theme".equals(type)) {
                FeatureTheme.applyPreset(this, action.optString("background"),
                        action.optString("accent"), action.optString("text"));
                FeatureTheme.apply(this);
                Toast.makeText(this, "Launcher theme applied", Toast.LENGTH_SHORT).show();
            } else if ("copy_text".equals(type)) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(
                        ClipData.newPlainText("Feature text", action.optString("text", "")));
                Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Unsupported feature action", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception error) {
            showError("Feature action failed", error);
        }
    }

    private void installCatalogFeature(JSONObject entry) {
        if (!validCatalogEntry(entry)) {
            showError("Invalid catalog entry", new IllegalArgumentException("Catalog metadata did not pass validation"));
            return;
        }
        Toast.makeText(this, "Downloading and validating feature…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                String expectedHash = entry.getString("sha256").toLowerCase(Locale.ROOT);
                String packageText = null;
                String asset = entry.optString("asset", "");
                if (SAFE_ASSET.matcher(asset).matches()) {
                    try {
                        String local = readAsset("feature-store/" + asset, MAX_PACKAGE_BYTES);
                        if (sha256(local).equals(expectedHash)) packageText = local;
                    } catch (Exception ignored) { }
                }
                if (packageText == null) {
                    String url = entry.optString("packageUrl", "");
                    if (!isHttpsUrl(url)) throw new IllegalArgumentException("Feature package URL must use HTTPS");
                    packageText = fetchHttps(url, MAX_PACKAGE_BYTES);
                }
                if (!sha256(packageText).equals(expectedHash)) {
                    throw new IllegalArgumentException("Feature package SHA-256 did not match the catalog");
                }
                JSONObject feature = validateFeature(new JSONObject(packageText));
                if (!entry.optString("id").equals(feature.optString("id"))) {
                    throw new IllegalArgumentException("Catalog id does not match package id");
                }
                saveInstalledFeature(feature);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Installed " + feature.optString("name"), Toast.LENGTH_LONG).show();
                    if ("browse".equals(currentPage)) showBrowse();
                    else showInstalled();
                });
            } catch (Exception error) {
                runOnUiThread(() -> showError("Could not install feature", error));
            }
        });
    }

    private JSONObject validateFeature(JSONObject feature) {
        if (feature == null || feature.optInt("schemaVersion", -1) != 1) {
            throw new IllegalArgumentException("Unsupported feature package schema");
        }
        String id = feature.optString("id", "");
        String name = feature.optString("name", "").trim();
        String version = feature.optString("version", "").trim();
        String author = feature.optString("author", "").trim();
        String owner = feature.optString("owner", "").trim();
        String description = feature.optString("description", "");
        if (!isSafeId(id)) throw new IllegalArgumentException("Feature id is invalid");
        if (name.length() < 2 || name.length() > 80) throw new IllegalArgumentException("Feature name is invalid");
        if (version.length() < 1 || version.length() > 40) throw new IllegalArgumentException("Feature version is invalid");
        if (author.length() < 2 || author.length() > 80 || owner.length() < 2 || owner.length() > 80) {
            throw new IllegalArgumentException("Feature owner/author is required");
        }
        if (description.length() > 400) throw new IllegalArgumentException("Feature description is too long");
        JSONObject screen = feature.optJSONObject("screen");
        if (screen == null) throw new IllegalArgumentException("Feature screen is missing");
        String type = screen.optString("type", "");
        if (!("page".equals(type) || "theme".equals(type) || "server_status".equals(type)
                || "social".equals(type))) {
            throw new IllegalArgumentException("Unsupported feature screen type");
        }
        if ("social".equals(type) && !"local_preview".equals(screen.optString("mode", "local_preview"))) {
            throw new IllegalArgumentException("Only the explicitly local-preview social mode is supported until an online service is configured");
        }
        if ("theme".equals(type)) {
            JSONArray presets = screen.optJSONArray("presets");
            if (presets == null || presets.length() < 1 || presets.length() > 30) {
                throw new IllegalArgumentException("Theme feature must have 1–30 presets");
            }
            for (int i = 0; i < presets.length(); i++) {
                JSONObject preset = presets.optJSONObject(i);
                if (preset == null || !validHex(preset.optString("background"))
                        || !validHex(preset.optString("accent")) || !validHex(preset.optString("text"))) {
                    throw new IllegalArgumentException("Theme preset colors must be #RRGGBB values");
                }
            }
        } else if ("page".equals(type)) {
            JSONArray widgets = screen.optJSONArray("widgets");
            if (widgets != null && widgets.length() > 100) throw new IllegalArgumentException("Too many page widgets");
            if (widgets != null) {
                for (int i = 0; i < widgets.length(); i++) {
                    JSONObject widget = widgets.optJSONObject(i);
                    if (widget == null) throw new IllegalArgumentException("Invalid page widget");
                    String widgetType = widget.optString("type", "text");
                    if (!("text".equals(widgetType) || "link".equals(widgetType)
                            || "button".equals(widgetType) || "toggle".equals(widgetType))) {
                        throw new IllegalArgumentException("Unsupported page widget");
                    }
                    if ("link".equals(widgetType) && !isHttpsUrl(widget.optString("url", ""))) {
                        throw new IllegalArgumentException("Feature links must use HTTPS");
                    }
                    if ("button".equals(widgetType)) validateWidgetAction(widget.optJSONObject("action"));
                    if ("toggle".equals(widgetType) && !isSafeId(widget.optString("id", ""))) {
                        throw new IllegalArgumentException("Toggle id is invalid");
                    }
                }
            }
        }
        return feature;
    }

    private void validateWidgetAction(JSONObject action) {
        if (action == null) throw new IllegalArgumentException("Feature button action is missing");
        String type = action.optString("type", "");
        if ("open_url".equals(type)) {
            if (!isHttpsUrl(action.optString("url", ""))) {
                throw new IllegalArgumentException("Feature action URLs must use HTTPS");
            }
        } else if ("set_theme".equals(type)) {
            if (!validHex(action.optString("background")) || !validHex(action.optString("accent"))
                    || !validHex(action.optString("text"))) {
                throw new IllegalArgumentException("Theme action has invalid colors");
            }
        } else if ("copy_text".equals(type)) {
            if (action.optString("text", "").length() > 2000) {
                throw new IllegalArgumentException("Copied text is too long");
            }
        } else {
            throw new IllegalArgumentException("Unsupported feature action type");
        }
    }

    private void saveInstalledFeature(JSONObject feature) throws Exception {
        JSONObject validated = validateFeature(feature);
        File directory = installedDirectory();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Could not create feature storage directory");
        }
        File destination = installedFile(validated.getString("id"));
        File temp = new File(directory, destination.getName() + ".tmp");
        byte[] bytes = validated.toString(2).getBytes("UTF-8");
        if (bytes.length > MAX_PACKAGE_BYTES) throw new IllegalArgumentException("Feature package exceeds 1 MiB");
        try (FileOutputStream stream = new FileOutputStream(temp)) {
            stream.write(bytes);
            stream.getFD().sync();
        }
        if (destination.exists() && !destination.delete()) {
            temp.delete();
            throw new IllegalStateException("Could not replace the installed feature");
        }
        if (!temp.renameTo(destination)) {
            temp.delete();
            throw new IllegalStateException("Could not finish installing the feature");
        }
    }

    private List<JSONObject> readInstalledFeatures() {
        ArrayList<JSONObject> result = new ArrayList<>();
        File directory = installedDirectory();
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".vcfeature") && !name.endsWith(".tmp.vcfeature"));
        if (files == null) return result;
        for (File file : files) {
            try {
                if (!file.isFile() || file.length() > MAX_PACKAGE_BYTES) continue;
                String content;
                try (InputStream input = new FileInputStream(file)) {
                    content = readLimited(input, MAX_PACKAGE_BYTES);
                }
                JSONObject feature = validateFeature(new JSONObject(content));
                if ((feature.optString("id") + ".vcfeature").equals(file.getName())) result.add(feature);
            } catch (Exception ignored) {
                // Corrupt third-party data is ignored rather than blocking other installed features.
            }
        }
        Collections.sort(result, Comparator.comparing(
                item -> item.optString("name"), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private boolean isInstalled(String id) {
        if (!isSafeId(id)) return false;
        File file = installedFile(id);
        return file.isFile() && file.length() <= MAX_PACKAGE_BYTES;
    }

    private void confirmUninstall(String id, String name) {
        if (!isSafeId(id)) return;
        new AlertDialog.Builder(this)
                .setTitle("Uninstall feature")
                .setMessage("Remove " + name + " from installed features? The launcher engine, game files, Java runtimes, renderers, loaders, and modpack integrations will not be changed.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Uninstall", (dialog, which) -> {
                    File file = installedFile(id);
                    if (file.isFile() && file.delete()) {
                        Toast.makeText(this, "Feature uninstalled", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Could not remove the feature", Toast.LENGTH_LONG).show();
                    }
                    if ("browse".equals(currentPage)) showBrowse(); else showInstalled();
                }).show();
    }

    private void importPackage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"application/json", "text/json", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQUEST_IMPORT);
        } catch (ActivityNotFoundException error) {
            showError("No file picker is available", error);
        }
    }

    private void exportPackage(JSONObject feature) {
        try {
            pendingExport = validateFeature(feature);
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, pendingExport.getString("id") + ".vcfeature");
            startActivityForResult(intent, REQUEST_EXPORT);
        } catch (Exception error) {
            showError("Could not export feature", error);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT) {
            io.execute(() -> {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IllegalArgumentException("Could not read selected package");
                    JSONObject feature = validateFeature(new JSONObject(readLimited(input, MAX_PACKAGE_BYTES)));
                    saveInstalledFeature(feature);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Imported " + feature.optString("name"), Toast.LENGTH_LONG).show();
                        showInstalled();
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> showError("Import rejected", error));
                }
            });
        } else if (requestCode == REQUEST_EXPORT && pendingExport != null) {
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IllegalArgumentException("Could not write selected file");
                byte[] bytes = pendingExport.toString(2).getBytes("UTF-8");
                output.write(bytes);
                output.flush();
                Toast.makeText(this, "Feature exported. Share the .vcfeature file or submit it to the catalog.", Toast.LENGTH_LONG).show();
            } catch (Exception error) {
                showError("Export failed", error);
            } finally {
                pendingExport = null;
            }
        }
    }

    private String readAsset(String path, int limit) throws Exception {
        try (InputStream input = getAssets().open(path)) {
            return readLimited(input, limit);
        }
    }

    private static String readLimited(InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IllegalArgumentException("File exceeds size limit");
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), "UTF-8");
    }

    private static String fetchHttps(String address, int limit) throws Exception {
        if (!isHttpsUrl(address)) throw new IllegalArgumentException("Only HTTPS URLs are allowed");
        HttpURLConnection connection = null;
        try {
            URL url = new URL(address);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(9000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "VoidCraft-FeatureStore/1");
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("Server returned HTTP " + status);
            }
            long length = connection.getContentLengthLong();
            if (length > limit) throw new IllegalArgumentException("Remote file exceeds size limit");
            try (InputStream input = connection.getInputStream()) {
                return readLimited(input, limit);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isHttpsUrl(String value) {
        if (value == null || value.length() > 2048 || !value.regionMatches(true, 0, "https://", 0, 8)) return false;
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String sha256(String content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(content.getBytes("UTF-8"));
        StringBuilder result = new StringBuilder(64);
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static boolean validHex(String value) {
        if (value == null || value.length() != 7 || value.charAt(0) != '#') return false;
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void openHttps(String address) {
        try {
            if (!isHttpsUrl(address)) throw new IllegalArgumentException("Only HTTPS links are allowed");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address)));
        } catch (Exception error) {
            showError("Could not open link", error);
        }
    }

    private void showError(String title, Exception error) {
        String message = error == null || empty(error.getMessage())
                ? "The operation could not be completed." : error.getMessage();
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.ok, null).show();
    }

    private void beginPage(String title, String subtitle) {
        pageRoot = new LinearLayout(this);
        pageRoot.setOrientation(LinearLayout.VERTICAL);
        pageRoot.setPadding(dp(16), dp(12), dp(16), dp(12));
        pageRoot.setBackgroundColor(Color.parseColor("#171526"));

        TextView brand = addTextTo(pageRoot, "VOIDCRAFT  /  FEATURE STORE", 12, true);
        brand.setTextColor(Color.parseColor("#B8A7F7"));
        addTextTo(pageRoot, title, 25, true);
        addTextTo(pageRoot, subtitle, 13, false);

        LinearLayout navigation = horizontalRow();
        addButtonTo(navigation, "Browse", v -> showBrowse());
        addButtonTo(navigation, "Installed", v -> showInstalled());
        addButtonTo(navigation, "Create", v -> showCreate());
        addButtonTo(navigation, "Import", v -> importPackage());
        pageRoot.addView(navigation);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        pageBody = new LinearLayout(this);
        pageBody.setOrientation(LinearLayout.VERTICAL);
        pageBody.setPadding(0, dp(8), 0, dp(18));
        scroll.addView(pageBody, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        pageRoot.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(pageRoot);
    }

    private void addButtonRow(String firstLabel, View.OnClickListener first,
                              String secondLabel, View.OnClickListener second) {
        LinearLayout row = horizontalRow();
        addButtonTo(row, firstLabel, first);
        if (secondLabel != null && second != null) addButtonTo(row, secondLabel, second);
        pageBody.addView(row);
    }

    private void addButtonRow(String label, View.OnClickListener listener) {
        addButtonRow(label, listener, null, null);
    }

    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#242138"));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.parseColor("#40385C"));
        card.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        params.bottomMargin = dp(4);
        pageBody.addView(card, params);
        return card;
    }

    private TextView addText(String text, int size, boolean bold) {
        return addTextTo(pageBody, text, size, bold);
    }

    private TextView addTextTo(LinearLayout parent, String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text == null ? "" : text);
        view.setTextSize(size);
        view.setTextColor(Color.parseColor("#F4F0FF"));
        view.setPadding(0, dp(4), 0, dp(4));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(view, matchWrap());
        return view;
    }

    private EditText editField(String hint) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setTextColor(Color.parseColor("#F4F0FF"));
        edit.setHintTextColor(Color.parseColor("#A9A3C3"));
        edit.setSingleLine(false);
        edit.setPadding(dp(10), dp(8), dp(10), dp(8));
        return edit;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private Button makeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setMinHeight(dp(40));
        return button;
    }

    private void addButtonTo(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = makeButton(label);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), dp(4), dp(3), dp(4));
        parent.addView(button, params);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
