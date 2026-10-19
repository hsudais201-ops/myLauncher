package com.voidcraft.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * First-party VoidCraft navigation and dashboard foundation.
 *
 * This screen deliberately does not pretend that a Minecraft runtime,
 * sign-in provider, remote catalog, or game downloader has been integrated.
 */
public final class MainActivity extends Activity {
    private static final String PREFS = "voidcraft_clean_room_settings";
    private static final String KEY_LOW_MEMORY = "low_memory_mode";
    private static final String KEY_REDUCED_MOTION = "reduced_ui_motion";
    private static final String KEY_FEATURE_SERVER_STATUS_INSTALLED = "feature_server_status_installed";

    private static final int COLOR_BACKGROUND = Color.rgb(13, 11, 24);
    private static final int COLOR_SURFACE = Color.rgb(25, 21, 43);
    private static final int COLOR_SURFACE_ALT = Color.rgb(35, 29, 57);
    private static final int COLOR_ACCENT = Color.rgb(139, 92, 246);
    private static final int COLOR_TEXT = Color.rgb(246, 243, 255);
    private static final int COLOR_MUTED = Color.rgb(184, 177, 208);
    private static final int COLOR_WARNING = Color.rgb(251, 191, 36);
    private static final int COLOR_SUCCESS = Color.rgb(74, 222, 128);
    private static final int COLOR_OFFLINE = Color.rgb(248, 113, 113);

    private SharedPreferences preferences;
    private SavedServerStore serverStore;
    private final Map<String, MinecraftServerStatusClient.Result> serverStatuses = new HashMap<>();
    private final Set<String> serverQueriesInFlight = new HashSet<>();
    private ExecutorService serverStatusExecutor;
    private String currentRoute = "Dashboard";
    private LinearLayout pageContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        serverStore = new SavedServerStore(this);
        serverStatusExecutor = Executors.newFixedThreadPool(3);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        render();
    }

    @Override
    protected void onDestroy() {
        if (serverStatusExecutor != null) {
            serverStatusExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if ("Server Status".equals(currentRoute)) {
            currentRoute = "Feature Store";
            render();
        } else if (!"Dashboard".equals(currentRoute)) {
            currentRoute = "Dashboard";
            render();
        } else {
            super.onBackPressed();
        }
    }

    private void render() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);
        root.setPadding(dp(18), dp(14), dp(18), dp(8));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.VERTICAL);

        TextView brand = text("VOIDCRAFT", 12, COLOR_ACCENT, true);
        brand.setLetterSpacing(0.16f);
        header.addView(brand);

        TextView subtitle = text("A launcher built from first-party code", 14, COLOR_MUTED, false);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = dp(4);
        header.addView(subtitle, subtitleParams);
        root.addView(header);

        TextView pageTitle = text(currentRoute, 27, COLOR_TEXT, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(24);
        titleParams.bottomMargin = dp(12);
        root.addView(pageTitle, titleParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        pageContent = new LinearLayout(this);
        pageContent.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(pageContent);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        buildCurrentPage();

        HorizontalScrollView navScroll = new HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        navScroll.setFillViewport(false);
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.addView(navButton("Home", "Dashboard"));
        nav.addView(navButton("Installs", "Installations"));
        nav.addView(navButton("Downloads", "Downloads"));
        nav.addView(navButton("Store", "Feature Store"));
        nav.addView(navButton("Servers", "Servers"));
        nav.addView(navButton("Settings", "Settings"));
        navScroll.addView(nav);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(54));
        navParams.topMargin = dp(8);
        root.addView(navScroll, navParams);

        setContentView(root);
    }

    private void buildCurrentPage() {
        if ("Dashboard".equals(currentRoute)) {
            buildDashboard();
        } else if ("Installations".equals(currentRoute)) {
            addCard("Game installations",
                    "This section will manage isolated game profiles, versions, loader choices, and per-profile settings. No installation engine is connected in this clean-room milestone.");
            addAction("Create installation", "Installation creation is not wired up yet.");
        } else if ("Downloads".equals(currentRoute)) {
            addCard("Download manager",
                    "The independent download queue, resume logic, file verification, and storage accounting have not been implemented yet.");
            addAction("Download service status", "No game download service is connected.");
        } else if ("Feature Store".equals(currentRoute)) {
            buildFeatureStore();
        } else if ("Servers".equals(currentRoute)) {
            buildServers();
        } else if ("Server Status".equals(currentRoute)) {
            buildServerStatusFeature();
        } else if ("Settings".equals(currentRoute)) {
            buildSettings();
        }
    }

    private void buildFeatureStore() {
        addCard("Optional launcher features",
                "Browse individual features without fixed categories. Install only the tools you want; installed features get their own screen.");

        boolean installed = preferences.getBoolean(KEY_FEATURE_SERVER_STATUS_INSTALLED, false);
        LinearLayout statusFeature = cardContainer();
        statusFeature.addView(text("Server Status", 18, COLOR_TEXT, true));
        TextView detail = text(
                "Check saved Minecraft: Java Edition servers for reachability, MOTD, player count, version, and server icon.",
                14, COLOR_MUTED, false);
        detail.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(8);
        statusFeature.addView(detail, detailParams);
        statusFeature.addView(text(
                installed ? "Installed on this device" : "Not installed",
                13, installed ? COLOR_SUCCESS : COLOR_MUTED, true));

        if (installed) {
            Button open = button("Open Server Status", () -> navigate("Server Status"), true);
            LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(-1, dp(44));
            openParams.topMargin = dp(12);
            statusFeature.addView(open, openParams);

            Button uninstall = button("Uninstall feature", this::confirmUninstallServerStatusFeature, false);
            LinearLayout.LayoutParams uninstallParams = new LinearLayout.LayoutParams(-1, dp(44));
            uninstallParams.topMargin = dp(8);
            statusFeature.addView(uninstall, uninstallParams);
        } else {
            Button install = button("Install Server Status", () -> {
                preferences.edit().putBoolean(KEY_FEATURE_SERVER_STATUS_INSTALLED, true).apply();
                Toast.makeText(this, "Server Status installed.", Toast.LENGTH_SHORT).show();
                navigate("Feature Store");
            }, true);
            LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(-1, dp(44));
            installParams.topMargin = dp(12);
            statusFeature.addView(install, installParams);
        }
        addCardView(statusFeature);

        addCard("More features",
                "Custom themes and community-uploaded features can be added later. The remote catalog, upload service, publisher verification, and package-signature checks are not connected yet.");
    }

    private void confirmUninstallServerStatusFeature() {
        new AlertDialog.Builder(this)
                .setTitle("Uninstall Server Status?")
                .setMessage("This removes the optional status screen. Your saved server names and addresses will remain on this device.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Uninstall", (dialog, which) -> {
                    preferences.edit().putBoolean(KEY_FEATURE_SERVER_STATUS_INSTALLED, false).apply();
                    serverStatuses.clear();
                    if ("Server Status".equals(currentRoute)) {
                        navigate("Feature Store");
                    } else {
                        render();
                    }
                    Toast.makeText(this, "Server Status uninstalled; saved servers were kept.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void buildServers() {
        java.util.List<SavedServerStore.Entry> servers = serverStore.load();
        addCard("Saved servers",
                "This list stores server names and host addresses on this device. Add, edit, remove, or refresh the saved list here. Live status is an optional feature available in the Feature Store.");

        Button add = button("＋  Add server", this::showServerEditorForNew, true);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, dp(46));
        addParams.bottomMargin = dp(8);
        pageContent.addView(add, addParams);

        Button refresh = button("Refresh saved list", () -> {
            render();
            Toast.makeText(this, "Saved server list reloaded.", Toast.LENGTH_SHORT).show();
        }, false);
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(-1, dp(44));
        refreshParams.bottomMargin = dp(12);
        pageContent.addView(refresh, refreshParams);

        if (preferences.getBoolean(KEY_FEATURE_SERVER_STATUS_INSTALLED, false)) {
            Button openStatus = button("Open Server Status feature", () -> navigate("Server Status"), true);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, dp(44));
            statusParams.bottomMargin = dp(12);
            pageContent.addView(openStatus, statusParams);
        }

        if (servers.isEmpty()) {
            addStatus("No saved servers yet. Select Add server to create one.", COLOR_MUTED);
        }
        for (SavedServerStore.Entry server : servers) {
            LinearLayout card = cardContainer();
            card.addView(text(server.name, 17, COLOR_TEXT, true));
            TextView addressText = text(server.address, 14, COLOR_MUTED, false);
            addressText.setTextIsSelectable(true);
            LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1, -2);
            addressParams.topMargin = dp(5);
            card.addView(addressText, addressParams);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button edit = button("Edit", () -> showServerEditor(server.id), true);
            Button remove = button("Remove", () -> confirmRemoveServer(server.id, server.name), false);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
            actionParams.setMargins(0, dp(8), dp(5), 0);
            actions.addView(edit, actionParams);
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
            removeParams.setMargins(dp(5), dp(8), 0, 0);
            actions.addView(remove, removeParams);
            card.addView(actions);
            addCardView(card);
        }
    }

    private void buildServerStatusFeature() {
        if (!preferences.getBoolean(KEY_FEATURE_SERVER_STATUS_INSTALLED, false)) {
            addCard("Server Status is not installed",
                    "Open the Feature Store to install this optional feature. Your saved servers have not been deleted.");
            Button store = button("Open Feature Store", () -> navigate("Feature Store"), true);
            pageContent.addView(store, new LinearLayout.LayoutParams(-1, dp(44)));
            return;
        }

        java.util.List<SavedServerStore.Entry> servers = serverStore.load();
        addCard("Server Status",
                "Read-only Minecraft: Java Edition status. A successful response can include MOTD, online/max players, reported version, and the server-provided icon. The app does not log in to or join the server.");

        Button manage = button("Manage saved servers", () -> navigate("Servers"), false);
        LinearLayout.LayoutParams manageParams = new LinearLayout.LayoutParams(-1, dp(44));
        manageParams.bottomMargin = dp(8);
        pageContent.addView(manage, manageParams);

        Button checkAll = button("Check all server statuses", this::refreshAllServerStatuses, true);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(-1, dp(44));
        checkParams.bottomMargin = dp(12);
        pageContent.addView(checkAll, checkParams);

        if (servers.isEmpty()) {
            addStatus("No saved servers yet. Use Manage saved servers to add one.", COLOR_MUTED);
        }
        for (SavedServerStore.Entry server : servers) {
            startServerStatusCheckIfNeeded(server);

            LinearLayout card = cardContainer();
            addServerFavicon(card, serverStatuses.get(server.id));
            card.addView(text(server.name, 17, COLOR_TEXT, true));

            TextView addressText = text(server.address, 14, COLOR_MUTED, false);
            addressText.setTextIsSelectable(true);
            LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1, -2);
            addressParams.topMargin = dp(5);
            card.addView(addressText, addressParams);

            addServerStatusText(card, serverStatuses.get(server.id), server.id);

            addCardView(card);
        }
    }

    private void refreshAllServerStatuses() {
        java.util.List<SavedServerStore.Entry> servers = serverStore.load();
        for (SavedServerStore.Entry server : servers) {
            serverStatuses.remove(server.id);
        }
        for (SavedServerStore.Entry server : servers) {
            startServerStatusCheckIfNeeded(server);
        }
        render();
        Toast.makeText(this, "Checking saved Java Edition servers…", Toast.LENGTH_SHORT).show();
    }

    private void startServerStatusCheckIfNeeded(SavedServerStore.Entry server) {
        if (serverStatuses.containsKey(server.id)
                || serverQueriesInFlight.contains(server.id)
                || serverStatusExecutor == null
                || serverStatusExecutor.isShutdown()) {
            return;
        }

        serverQueriesInFlight.add(server.id);
        serverStatusExecutor.execute(() -> {
            MinecraftServerStatusClient.Result result = MinecraftServerStatusClient.query(server.address);
            runOnUiThread(() -> {
                serverQueriesInFlight.remove(server.id);
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!preferences.getBoolean(KEY_FEATURE_SERVER_STATUS_INSTALLED, false)) {
                    serverStatuses.remove(server.id);
                    return;
                }

                boolean unchangedSavedEntry = false;
                for (SavedServerStore.Entry current : serverStore.load()) {
                    if (current.id.equals(server.id) && current.address.equals(server.address)) {
                        unchangedSavedEntry = true;
                        break;
                    }
                }
                if (unchangedSavedEntry) {
                    serverStatuses.put(server.id, result);
                } else {
                    serverStatuses.remove(server.id);
                }
                if ("Server Status".equals(currentRoute)) {
                    render();
                }
            });
        });
    }

    private void addServerStatusText(LinearLayout card,
                                    MinecraftServerStatusClient.Result result,
                                    String serverId) {
        if (result == null) {
            String message = serverQueriesInFlight.contains(serverId)
                    ? "Checking server status…"
                    : "Status not checked";
            card.addView(text(message, 13, COLOR_MUTED, true));
            return;
        }

        if (!result.online) {
            card.addView(text("● OFFLINE / UNREACHABLE", 13, COLOR_OFFLINE, true));
            TextView detail = text(result.message, 13, COLOR_MUTED, false);
            detail.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
            detailParams.topMargin = dp(4);
            card.addView(detail, detailParams);
            return;
        }

        card.addView(text("● ONLINE", 13, COLOR_SUCCESS, true));
        card.addView(text("MOTD: " + result.motd, 14, COLOR_TEXT, false));

        String players = result.playersOnline >= 0 && result.playersMax >= 0
                ? "Players: " + result.playersOnline + " / " + result.playersMax
                : "Players: not reported";
        card.addView(text(players, 13, COLOR_MUTED, false));

        String version = result.version.isEmpty() ? "Version: not reported" : "Version: " + result.version;
        card.addView(text(version, 13, COLOR_MUTED, false));
    }

    private void addServerFavicon(LinearLayout card, MinecraftServerStatusClient.Result result) {
        if (result == null || result.faviconDataUri == null) {
            return;
        }

        final String prefix = "data:image/png;base64,";
        if (!result.faviconDataUri.startsWith(prefix)) {
            return;
        }
        try {
            byte[] imageBytes = Base64.decode(
                    result.faviconDataUri.substring(prefix.length()), Base64.DEFAULT);
            if (imageBytes.length == 0 || imageBytes.length > 150_000) {
                return;
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap == null) {
                return;
            }
            ImageView favicon = new ImageView(this);
            favicon.setImageBitmap(bitmap);
            favicon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            favicon.setContentDescription("Server icon");
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
            iconParams.bottomMargin = dp(6);
            card.addView(favicon, 0, iconParams);
        } catch (IllegalArgumentException ignored) {
            // A malformed server-provided favicon should not break the server list.
        }
    }

    private void showServerEditorForNew() {
        showServerEditor(null);
    }

    private void showServerEditor(String serverId) {
        SavedServerStore.Entry existing = null;
        if (serverId != null) {
            for (SavedServerStore.Entry entry : serverStore.load()) {
                if (serverId.equals(entry.id)) {
                    existing = entry;
                    break;
                }
            }
        }

        final SavedServerStore.Entry serverToEdit = existing;
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("Server name");
        EditText addressInput = new EditText(this);
        addressInput.setSingleLine(true);
        addressInput.setHint("Address or hostname (example: play.example.net)");
        if (serverToEdit != null) {
            nameInput.setText(serverToEdit.name);
            addressInput.setText(serverToEdit.address);
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        form.addView(nameInput, new LinearLayout.LayoutParams(-1, -2));
        form.addView(addressInput, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(serverToEdit == null ? "Add server" : "Edit server")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = nameInput.getText().toString().trim();
            String address = addressInput.getText().toString().trim();
            if (name.isEmpty() || address.isEmpty()) {
                Toast.makeText(this, "Enter both a server name and address.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (address.contains("://") || address.contains("/") || address.contains(" ")) {
                Toast.makeText(this, "Enter a hostname or host:port, not a URL.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (serverToEdit == null) {
                serverStore.add(name, address);
            } else if (!serverStore.update(serverToEdit.id, name, address)) {
                Toast.makeText(this, "This saved server no longer exists. Refresh and try again.", Toast.LENGTH_SHORT).show();
                return;
            } else {
                // A changed address must not inherit the old host's cached status.
                serverStatuses.remove(serverToEdit.id);
            }
            dialog.dismiss();
            render();
        }));
        dialog.show();
    }

    private void confirmRemoveServer(String serverId, String serverName) {
        new AlertDialog.Builder(this)
                .setTitle("Remove server?")
                .setMessage("Remove " + serverName + " from your saved list? This does not affect the server itself.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", (dialog, which) -> {
                    serverStore.remove(serverId);
                    serverStatuses.remove(serverId);
                    render();
                })
                .show();
    }

    private void buildDashboard() {
        addCard("Your Minecraft workspace",
                "An independent VoidCraft Android foundation with its own package, UI, preferences, and navigation.");
        addStatus("Clean-room foundation • 0.1.0", COLOR_ACCENT);

        addCard("Runtime integration",
                "Minecraft has not been connected to this app yet. Play remains disabled until a tested runtime and launch pipeline are available.");
        Button play = button("PLAY — NOT AVAILABLE YET", null, false);
        play.setEnabled(false);
        play.setAlpha(0.7f);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(-1, dp(50));
        playParams.topMargin = dp(4);
        pageContent.addView(play, playParams);

        addShortcut("Installations", "Manage game profiles and versions");
        addShortcut("Feature Store", "Choose and manage optional launcher features");
        addShortcut("Servers", "Maintain a saved server list");
    }

    private void buildSettings() {
        addCard("Device-friendly options",
                "These preferences are local to this clean-room app. They do not change Java heap size or game rendering until a runtime integration exists.");

        Switch lowMemory = new Switch(this);
        lowMemory.setText("Low-memory mode preference");
        lowMemory.setTextColor(COLOR_TEXT);
        lowMemory.setTextSize(15);
        lowMemory.setPadding(0, dp(8), 0, dp(8));
        lowMemory.setChecked(preferences.getBoolean(KEY_LOW_MEMORY, true));
        lowMemory.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(KEY_LOW_MEMORY, checked).apply());
        pageContent.addView(lowMemory);

        Switch reducedMotion = new Switch(this);
        reducedMotion.setText("Reduced UI motion preference");
        reducedMotion.setTextColor(COLOR_TEXT);
        reducedMotion.setTextSize(15);
        reducedMotion.setPadding(0, dp(8), 0, dp(8));
        reducedMotion.setChecked(preferences.getBoolean(KEY_REDUCED_MOTION, false));
        reducedMotion.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(KEY_REDUCED_MOTION, checked).apply());
        pageContent.addView(reducedMotion);

        addCard("Privacy",
                "Opening Servers or selecting Check all server statuses sends short read-only status requests to the saved addresses. The app does not log in to or join servers.");
    }

    private void addShortcut(String route, String description) {
        LinearLayout card = cardContainer();
        card.addView(text(route, 18, COLOR_TEXT, true));
        TextView body = text(description, 14, COLOR_MUTED, false);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(5);
        card.addView(body, bodyParams);
        Button open = button("Open " + route, () -> navigate(route), true);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(44));
        buttonParams.topMargin = dp(12);
        card.addView(open, buttonParams);
        addCardView(card);
    }

    private void addCard(String title, String description) {
        LinearLayout card = cardContainer();
        card.addView(text(title, 18, COLOR_TEXT, true));
        TextView detail = text(description, 14, COLOR_MUTED, false);
        detail.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(8);
        card.addView(detail, detailParams);
        addCardView(card);
    }

    private void addAction(String label, String message) {
        Button action = button(label, () -> addStatus(message, COLOR_WARNING), true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(46));
        params.topMargin = dp(2);
        params.bottomMargin = dp(10);
        pageContent.addView(action, params);
    }

    private void addStatus(String message, int accent) {
        TextView status = text("•  " + message, 13, accent, true);
        status.setPadding(dp(3), dp(6), dp(3), dp(12));
        pageContent.addView(status);
    }

    private LinearLayout cardContainer() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(shape(COLOR_SURFACE, dp(18)));
        return card;
    }

    private void addCardView(View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        pageContent.addView(card, params);
    }

    private Button navButton(String label, String route) {
        boolean selected = route.equals(currentRoute);
        Button result = button(label, () -> navigate(route), selected);
        result.setTextSize(12);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(44));
        params.setMargins(dp(3), 0, dp(3), 0);
        result.setLayoutParams(params);
        return result;
    }

    private Button button(String label, Runnable action, boolean primary) {
        Button result = new Button(this);
        result.setText(label);
        result.setAllCaps(false);
        result.setTextSize(14);
        result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        result.setTextColor(primary ? COLOR_TEXT : COLOR_MUTED);
        result.setMinHeight(dp(42));
        result.setPadding(dp(12), dp(6), dp(12), dp(6));
        result.setBackground(shape(primary ? COLOR_ACCENT : COLOR_SURFACE_ALT, dp(14)));
        if (action != null) {
            result.setOnClickListener(view -> action.run());
        }
        return result;
    }

    private void navigate(String route) {
        currentRoute = route;
        render();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        result.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) {
            result.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return result;
    }

    private GradientDrawable shape(int color, int radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
