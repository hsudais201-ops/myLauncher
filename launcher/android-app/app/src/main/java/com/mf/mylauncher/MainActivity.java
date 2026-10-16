package com.mf.mylauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.mf.mylauncher.core.MemoryDefaults;
import com.mf.mylauncher.core.MinecraftVersion;
import com.mf.mylauncher.core.OfflineProfileValidator;
import com.mf.mylauncher.core.VersionManifest;
import com.mf.mylauncher.core.VersionManifestDownloader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lightweight Android shell for the independently tested launcher core. */
public final class MainActivity extends Activity {
    private static final String PREFS = "mylauncher-settings";
    private static final String PREF_PROFILE = "profile_name";
    private static final String PREF_VERSION = "selected_version";
    private static final String PREF_HEAP = "heap_mib";
    private static final int BACKGROUND = Color.rgb(11, 16, 32);
    private static final int SURFACE = Color.rgb(22, 29, 51);
    private static final int ACCENT = Color.rgb(139, 118, 255);
    private static final int TEXT = Color.rgb(245, 247, 255);
    private static final int MUTED = Color.rgb(174, 184, 209);

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private Spinner versionSpinner;
    private EditText profileInput;
    private TextView profileStatus;
    private TextView versionStatus;
    private TextView heapLabel;
    private ProgressBar progressBar;
    private Button refreshButton;
    private SeekBar heapSlider;
    private List<MinecraftVersion> versions = new ArrayList<>();
    private boolean loadingVersions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Window window = getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        buildUi();
        loadVersions();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(BACKGROUND);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("myLauncher", 32, TEXT, true);
        title.setLetterSpacing(0.015f);
        content.addView(title, matchWrap());
        content.addView(text("Minecraft: Java Edition · Android", 15, MUTED, false),
                margins(matchWrap(), 0, dp(4), 0, dp(20)));

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        statusCard.setBackground(panel(SURFACE, dp(18)));
        versionStatus = text("Preparing version catalogue…", 14, TEXT, false);
        content.addView(statusCard, margins(matchWrap(), 0, 0, 0, dp(20)));
        statusCard.addView(versionStatus, matchWrap());
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        statusCard.addView(progressBar, margins(matchWrap(), 0, dp(10), 0, 0));

        section(content, "GAME VERSION");
        versionSpinner = new Spinner(this);
        versionSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {"Loading official versions…"}));
        versionSpinner.setPadding(dp(8), dp(4), dp(8), dp(4));
        versionSpinner.setBackground(panel(SURFACE, dp(12)));
        content.addView(versionSpinner, margins(matchWrap(), 0, dp(8), 0, dp(10)));

        refreshButton = button("Refresh version list", false);
        content.addView(refreshButton, margins(matchWrap(), 0, 0, 0, dp(22)));
        refreshButton.setOnClickListener(view -> loadVersions());

        section(content, "LOCAL PROFILE");
        profileInput = new EditText(this);
        profileInput.setSingleLine(true);
        profileInput.setTextColor(TEXT);
        profileInput.setHintTextColor(MUTED);
        profileInput.setHint("Enter a player name");
        profileInput.setTextSize(16);
        profileInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        profileInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        profileInput.setBackground(panel(SURFACE, dp(12)));
        profileInput.setText(preferences.getString(PREF_PROFILE, ""));
        content.addView(profileInput, margins(matchWrap(), 0, dp(8), 0, 0));

        profileStatus = text("Local profile only — not a Microsoft sign-in.", 13, MUTED, false);
        content.addView(profileStatus, margins(matchWrap(), 0, dp(8), 0, dp(22)));
        updateProfileStatus();
        profileInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                preferences.edit().putString(PREF_PROFILE, text.toString()).apply();
                updateProfileStatus();
            }
            @Override public void afterTextChanged(Editable text) { }
        });

        section(content, "MEMORY HINT");
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memoryInfo);
        long totalRamMiB = Math.max(256L, memoryInfo.totalMem / (1024L * 1024L));
        int recommendedHeapMiB = MemoryDefaults.suggestedHeapMiB(totalRamMiB);
        int maximumHeap = Math.max(256, recommendedHeapMiB);

        heapLabel = text("", 14, TEXT, true);
        content.addView(heapLabel, margins(matchWrap(), 0, dp(2), 0, dp(3)));
        content.addView(text("Conservative suggestion; the game runtime is not connected yet.",
                12, MUTED, false), margins(matchWrap(), 0, 0, 0, dp(4)));
        heapSlider = new SeekBar(this);
        heapSlider.setMax(Math.max(0, maximumHeap - 256));
        int savedHeap = preferences.getInt(PREF_HEAP, Math.min(512, maximumHeap));
        heapSlider.setProgress(Math.max(0, Math.min(heapSlider.getMax(), savedHeap - 256)));
        content.addView(heapSlider, margins(matchWrap(), 0, 0, 0, dp(22)));
        updateHeapLabel();
        heapSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                preferences.edit().putInt(PREF_HEAP, 256 + progress).apply();
                updateHeapLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        Button launchButton = button("Launch game · runtime not connected", true);
        launchButton.setEnabled(false);
        launchButton.setAlpha(0.58f);
        content.addView(launchButton, margins(matchWrap(), 0, 0, 0, dp(12)));
        content.addView(text("The app can load version metadata and save settings. Game file installation, Microsoft authentication, Java runtime setup and actual launch are still being built.",
                12, MUTED, false), matchWrap());

        setContentView(scrollView);
    }

    private void loadVersions() {
        if (loadingVersions || isFinishing() || isDestroyed()) return;
        loadingVersions = true;
        refreshButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        versionStatus.setText("Contacting the official Minecraft version manifest…");
        ioExecutor.execute(() -> {
            try {
                VersionManifest manifest = new VersionManifestDownloader().downloadManifest();
                List<MinecraftVersion> downloaded = manifest.getCatalog().getVersionsNewestFirst();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    versions = downloaded;
                    List<String> ids = new ArrayList<>();
                    for (MinecraftVersion version : versions) ids.add(version.getId());
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, ids);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    versionSpinner.setAdapter(adapter);
                    String saved = preferences.getString(PREF_VERSION, manifest.getLatestReleaseId());
                    int selected = ids.indexOf(saved);
                    if (selected < 0) selected = ids.indexOf(manifest.getLatestReleaseId());
                    if (selected >= 0) versionSpinner.setSelection(selected);
                    versionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            if (position >= 0 && position < versions.size()) {
                                preferences.edit().putString(PREF_VERSION, versions.get(position).getId()).apply();
                            }
                        }
                        @Override public void onNothingSelected(AdapterView<?> parent) { }
                    });
                    versionStatus.setText("Loaded " + ids.size() + " versions · Latest release "
                            + manifest.getLatestReleaseId() + " · Latest snapshot " + manifest.getLatestSnapshotId());
                    finishVersionLoad();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    String reason = exception.getMessage();
                    versionStatus.setText("Couldn't load versions. Check your connection and try again."
                            + (reason == null ? "" : "\n" + reason));
                    finishVersionLoad();
                });
            }
        });
    }

    private void finishVersionLoad() {
        loadingVersions = false;
        refreshButton.setEnabled(true);
        progressBar.setVisibility(View.GONE);
    }

    private void updateProfileStatus() {
        if (profileInput == null || profileStatus == null) return;
        String name = profileInput.getText().toString();
        if (name.isEmpty()) {
            profileStatus.setText("Local profile only — not a Microsoft sign-in.");
            profileStatus.setTextColor(MUTED);
        } else if (OfflineProfileValidator.isValid(name)) {
            profileStatus.setText("Name looks valid. This profile is local/offline only.");
            profileStatus.setTextColor(Color.rgb(129, 224, 173));
        } else {
            profileStatus.setText(OfflineProfileValidator.validationMessage(name));
            profileStatus.setTextColor(Color.rgb(255, 151, 151));
        }
    }

    private void updateHeapLabel() {
        if (heapLabel != null && heapSlider != null) {
            heapLabel.setText("Heap hint: " + (256 + heapSlider.getProgress()) + " MiB");
        }
    }

    private void section(LinearLayout parent, String title) {
        TextView label = text(title, 12, ACCENT, true);
        label.setLetterSpacing(0.12f);
        parent.addView(label, margins(matchWrap(), 0, 0, 0, dp(4)));
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button button(String label, boolean primary) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(TEXT);
        view.setMinimumHeight(dp(48));
        view.setPadding(dp(16), dp(10), dp(16), dp(10));
        view.setBackground(panel(primary ? ACCENT : SURFACE, dp(13)));
        return view;
    }

    private GradientDrawable panel(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams margins(LinearLayout.LayoutParams params,
                                               int left, int top, int right, int bottom) {
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}
