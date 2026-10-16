package com.mf.mylauncher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
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

import com.mf.mylauncher.core.InstallationProgress;
import com.mf.mylauncher.core.InstallationResult;
import com.mf.mylauncher.core.InstallationService;
import com.mf.mylauncher.core.JavaRuntimeLocator;
import com.mf.mylauncher.core.LibraryPlatform;
import com.mf.mylauncher.core.MemoryDefaults;
import com.mf.mylauncher.core.MinecraftVersion;
import com.mf.mylauncher.core.OfflineProfileValidator;
import com.mf.mylauncher.core.VersionManifest;
import com.mf.mylauncher.core.VersionManifestDownloader;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Locale;
import java.nio.file.Path;
import java.io.IOException;
import java.io.File;
import java.io.InterruptedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private TextView runtimeStatus;
    private TextView heapLabel;
    private ProgressBar progressBar;
    private Button refreshButton;
    private Button installButton;
    private SeekBar heapSlider;
    private final AtomicBoolean installationCancelled = new AtomicBoolean(false);
    private boolean installing;
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
        content.addView(refreshButton, margins(matchWrap(), 0, 0, 0, dp(8)));
        refreshButton.setOnClickListener(view -> loadVersions());

        installButton = button("Prepare selected version", true);
        installButton.setEnabled(false);
        content.addView(installButton, margins(matchWrap(), 0, 0, 0, dp(22)));
        installButton.setOnClickListener(view -> handleInstallationClick());

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

        section(content, "JAVA RUNTIME");
        runtimeStatus = text(runtimeStatusMessage(), 12, MUTED, false);
        content.addView(runtimeStatus, margins(matchWrap(), 0, 0, 0, dp(8)));
        Button checkRuntimeButton = button("Check installed runtime", false);
        content.addView(checkRuntimeButton, margins(matchWrap(), 0, 0, 0, dp(14)));
        checkRuntimeButton.setOnClickListener(view -> runtimeStatus.setText(runtimeStatusMessage()));

        Button launchButton = button("Launch game · runtime not connected", true);
        launchButton.setEnabled(false);
        launchButton.setAlpha(0.58f);
        content.addView(launchButton, margins(matchWrap(), 0, 0, 0, dp(12)));
        content.addView(text("This downloads and verifies the client JAR, ordinary libraries and assets. The core can plan and extract native archives when given a compatible target, but an Android runtime provider, Android-native selection, Microsoft sign-in and the actual game launch are not connected yet.",
                12, MUTED, false), matchWrap());

        setContentView(scrollView);
    }

    private String runtimeStatusMessage() {
        Path privateRoot = getFilesDir().toPath();
        Path expected = privateRoot.resolve("runtimes/default/bin/java");
        try {
            java.util.Optional<Path> executable = new JavaRuntimeLocator(privateRoot).locate("default");
            if (!executable.isPresent()) {
                return "No installed Java runtime found. Expected location: " + expected
                        + ". A compatible Android runtime provider is still needed.";
            }
            return "Java executable found at " + executable.get()
                    + ". Version and Android compatibility are not yet verified.";
        } catch (IOException | IllegalArgumentException exception) {
            return "Runtime check failed safely: " + exception.getMessage();
        }
    }

    private void loadVersions() {
        if (loadingVersions || isFinishing() || isDestroyed()) return;
        loadingVersions = true;
        refreshButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setProgress(0);
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
        refreshButton.setEnabled(!installing);
        if (installButton != null) installButton.setEnabled(!installing && !versions.isEmpty());
        progressBar.setVisibility(View.GONE);
    }

    private void handleInstallationClick() {
        if (installing) {
            installationCancelled.set(true);
            installButton.setEnabled(false);
            versionStatus.setText("Cancellation requested. Cleaning up the current partial download…");
            return;
        }
        int selectedPosition = versionSpinner.getSelectedItemPosition();
        if (selectedPosition < 0 || selectedPosition >= versions.size()) {
            versionStatus.setText("Load the official version list before preparing a version.");
            return;
        }

        MinecraftVersion selectedVersion = versions.get(selectedPosition);
        installing = true;
        installationCancelled.set(false);
        versionSpinner.setEnabled(false);
        refreshButton.setEnabled(false);
        installButton.setText("Cancel preparation");
        installButton.setEnabled(true);
        progressBar.setIndeterminate(true);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.VISIBLE);
        versionStatus.setText("Preparing " + selectedVersion.getId() + "…");

        ioExecutor.execute(() -> {
            try {
                InstallationResult result = new InstallationService().install(
                        selectedVersion,
                        new File(getFilesDir(), "minecraft").toPath(),
                        androidLibraryPlatform(),
                        installationCancelled::get,
                        snapshot -> runOnUiThread(() -> updateInstallationProgress(snapshot)));
                runOnUiThread(() -> finishInstallation(
                        "Base files prepared for " + result.getVersionId()
                                + ". Runtime, Android native libraries, sign-in and game launch are not connected yet."));
            } catch (InterruptedIOException exception) {
                runOnUiThread(() -> finishInstallation(
                        "Preparation cancelled. Completed verified files are kept; partial downloads were removed."));
            } catch (Exception exception) {
                String reason = exception.getMessage();
                runOnUiThread(() -> finishInstallation(
                        "Couldn't prepare this version. Verified files are kept; try again after checking the error."
                                + (reason == null ? "" : "\n" + reason)));
            }
        });
    }

    private LibraryPlatform androidLibraryPlatform() {
        String arch = Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0];
        if ("arm64-v8a".equals(arch)) arch = "aarch64";
        else if ("armeabi-v7a".equals(arch)) arch = "arm";
        else if ("x86_64".equals(arch)) arch = "x86_64";
        else if ("x86".equals(arch)) arch = "x86";
        return new LibraryPlatform("linux", arch, Build.VERSION.RELEASE, Collections.emptyMap());
    }

    private void updateInstallationProgress(InstallationProgress snapshot) {
        if (isFinishing() || isDestroyed() || !installing) return;
        InstallationProgress.Stage stage = snapshot.getStage();
        if (stage == InstallationProgress.Stage.DOWNLOADING) {
            progressBar.setIndeterminate(false);
            progressBar.setMax(100);
            progressBar.setProgress(snapshot.getPercent());
            String task = snapshot.getCurrentTask();
            String current = task == null ? "Preparing downloads" : task;
            double completedMiB = snapshot.getCompletedBytes() / (1024.0 * 1024.0);
            double totalMiB = snapshot.getTotalBytes() / (1024.0 * 1024.0);
            versionStatus.setText(String.format(Locale.ROOT,
                    "%s\n%d/%d files · %.1f/%.1f MiB · %d%%",
                    current, snapshot.getCompletedTasks(), snapshot.getTotalTasks(),
                    completedMiB, totalMiB, snapshot.getPercent()));
        } else {
            progressBar.setIndeterminate(stage != InstallationProgress.Stage.COMPLETED);
            if (stage == InstallationProgress.Stage.FETCHING_VERSION_METADATA) {
                versionStatus.setText("Reading metadata for " + snapshot.getCurrentTask() + "…");
            } else if (stage == InstallationProgress.Stage.FETCHING_ASSET_INDEX) {
                versionStatus.setText("Downloading and verifying asset index " + snapshot.getCurrentTask() + "…");
            } else if (stage == InstallationProgress.Stage.PLANNING) {
                versionStatus.setText("Calculating client, library and asset downloads…");
            } else if (stage == InstallationProgress.Stage.EXTRACTING_NATIVES) {
                versionStatus.setText("Extracting native libraries for " + snapshot.getCurrentTask() + "…");
            } else if (stage == InstallationProgress.Stage.MATERIALIZING_ASSETS) {
                versionStatus.setText("Preparing legacy asset folders…");
            }
        }
    }

    private void finishInstallation(String message) {
        if (isFinishing() || isDestroyed()) return;
        installing = false;
        installationCancelled.set(false);
        versionSpinner.setEnabled(true);
        refreshButton.setEnabled(true);
        installButton.setText("Prepare selected version");
        installButton.setEnabled(!versions.isEmpty());
        progressBar.setVisibility(View.GONE);
        versionStatus.setText(message);
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
        installationCancelled.set(true);
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}
