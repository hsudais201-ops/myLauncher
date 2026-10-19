package com.voidcraft.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;


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

    private static final int COLOR_BACKGROUND = Color.rgb(13, 11, 24);
    private static final int COLOR_SURFACE = Color.rgb(25, 21, 43);
    private static final int COLOR_SURFACE_ALT = Color.rgb(35, 29, 57);
    private static final int COLOR_ACCENT = Color.rgb(139, 92, 246);
    private static final int COLOR_TEXT = Color.rgb(246, 243, 255);
    private static final int COLOR_MUTED = Color.rgb(184, 177, 208);
    private static final int COLOR_WARNING = Color.rgb(251, 191, 36);

    private SharedPreferences preferences;
    private String currentRoute = "Dashboard";
    private LinearLayout pageContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        render();
    }

    @Override
    public void onBackPressed() {
        if (!"Dashboard".equals(currentRoute)) {
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
            addCard("Choose your features",
                    "The intended store is category-free: browse feature cards, install only the features you choose, and open each installed feature on its own page.");
            addStatus("Remote catalog not connected", COLOR_WARNING);
            addAction("Installed features", "No feature packages are installed in this clean-room app.");
        } else if ("Servers".equals(currentRoute)) {
            buildServers();
        } else if ("Settings".equals(currentRoute)) {
            buildSettings();
        }
    }

    private void buildServers() {
        SavedServerStore store = new SavedServerStore(this);
        java.util.List<SavedServerStore.Entry> servers = store.load();
        addCard("Saved servers",
                "Manage server name and address entries on this device. Live online status, MOTD, player count, version, and icon require a query service and are not guessed here.");
        Button add = button("＋  Add server", this::showServerEditorForNew, true);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-1, dp(46));
        addParams.bottomMargin = dp(12);
        pageContent.addView(add, addParams);

        if (servers.isEmpty()) {
            addStatus("No saved servers yet", COLOR_MUTED);
        }
        for (SavedServerStore.Entry server : servers) {
            LinearLayout card = cardContainer();
            card.addView(text(server.name, 17, COLOR_TEXT, true));
            TextView addressText = text(server.address, 14, COLOR_MUTED, false);
            addressText.setTextIsSelectable(true);
            LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(-1, -2);
            addressParams.topMargin = dp(5);
            card.addView(addressText, addressParams);
            card.addView(text("Status not checked", 13, COLOR_WARNING, true));

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

    private void showServerEditorForNew() {
        showServerEditor(null);
    }

    private void showServerEditor(String serverId) {
        SavedServerStore store = new SavedServerStore(this);
        SavedServerStore.Entry existing = null;
        if (serverId != null) {
            for (SavedServerStore.Entry entry : store.load()) {
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
                store.add(name, address);
            } else if (!store.update(serverToEdit.id, name, address)) {
                Toast.makeText(this, "This saved server no longer exists. Refresh and try again.", Toast.LENGTH_SHORT).show();
                return;
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
                    new SavedServerStore(this).remove(serverId);
                    render();
                })
                .show();
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
                "This foundation makes no network requests and does not ask for Microsoft, Google, or other account credentials.");
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
