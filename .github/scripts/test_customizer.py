from __future__ import annotations

import importlib.util
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


SCRIPT = Path(__file__).with_name("customize_amethyst.py")
SPEC = importlib.util.spec_from_file_location("customize_amethyst", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
customizer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(customizer)

APPLY_LOGO_SCRIPT = Path(__file__).with_name("apply_logo.py")
APPLY_LOGO_SPEC = importlib.util.spec_from_file_location("apply_logo", APPLY_LOGO_SCRIPT)
assert APPLY_LOGO_SPEC is not None and APPLY_LOGO_SPEC.loader is not None
logo_applier = importlib.util.module_from_spec(APPLY_LOGO_SPEC)
APPLY_LOGO_SPEC.loader.exec_module(logo_applier)


class CustomizeAmethystTests(unittest.TestCase):
    def fixture(self, root: Path) -> None:
        app = root / "app_pojavlauncher"
        (app / "src/main/res/values").mkdir(parents=True)
        (app / "build.gradle").write_text(
            '''android {
  defaultConfig { applicationId "org.angelauramc.amethyst"; minSdkVersion 21 }
  buildTypes {
    debug {
      applicationIdSuffix '.debug'
      resValue "string", "app_name", "Amethyst (Debug)"
      resValue "string", "app_short_name", "Amethyst (Debug)"
      resValue 'string', 'application_package', 'org.angelauramc.amethyst.debug'
      resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder.debug'
      resValue 'string', 'shareProviderAuthority', 'org.angelauramc.amethyst.scoped.controlfolder.debug'
    }
    release {
      resValue "string", "app_name", "Amethyst"
      resValue "string", "app_short_name", "Amethyst"
      resValue 'string', 'storageProviderAuthorities', 'org.angelauramc.amethyst.scoped.gamefolder'
      resValue 'string', 'application_package', 'org.angelauramc.amethyst'
    }
  }
}
''',
            encoding="utf-8",
        )
        (app / "src/main/res/values/strings.xml").write_text(
            '''<resources>
  <string name="app_name">Amethyst</string>
  <string name="app_short_name">Amethyst</string>
  <string name="error_fatal">Amethyst crashed</string>
  <string name="storageProviderAuthorities">org.angelauramc.amethyst.scoped.gamefolder</string>
  <string name="main_add_account">Add account</string>
</resources>
''',
            encoding="utf-8",
        )
        (app / "src/main/res/values/colors.xml").write_text(
            '<resources><color name="primary_text">#FFFFFF</color>'
            '<color name="secondary_text">#B2B2B2</color></resources>\n',
            encoding="utf-8",
        )
        (app / "src/main/res/values-fr").mkdir(parents=True, exist_ok=True)
        (app / "src/main/res/values-fr/strings.xml").write_text(
            '<resources><string name="app_name">Amethyst</string>'
            '<string name="error_fatal">Amethyst a planté</string></resources>\n',
            encoding="utf-8",
        )
        about_asset = app / "src/main/assets/about_en.txt"
        about_asset.parent.mkdir(parents=True, exist_ok=True)
        about_asset.write_text(
            '%s (Minecraft Java Launcher for Android), version %s by Tran Khanh Duy (based on "Boardwalk" app)<br>\n'
            '* License:<br>\n • GNU GPLv3<br>\n • Third party components and their licenses<br>\n',
            encoding="utf-8",
        )
        (root / "settings.gradle").write_text(
            """pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}

rootProject.name='Amethyst'
""",
            encoding="utf-8",
        )

        tools_java = app / "src/main/java/net/kdt/pojavlaunch/Tools.java"
        tools_java.parent.mkdir(parents=True, exist_ok=True)
        tools_java.write_text(
            'public final class Tools { public static String APP_NAME = "Amethyst"; }\n',
            encoding="utf-8",
        )

        logger_view = app / "src/main/java/com/kdt/LoggerView.java"
        logger_view.parent.mkdir(parents=True, exist_ok=True)
        logger_view.write_text(
            r"""package com.kdt;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import net.kdt.pojavlaunch.Logger;
import net.kdt.pojavlaunch.R;

public class LoggerView extends ConstraintLayout {
    private Logger.eventLogListener mLogListener;
    private ToggleButton mLogToggle;
    private DefocusableScrollView mScrollView;
    private TextView mLogTextView;

    public LoggerView(@NonNull Context context) { this(context, null); }
    public LoggerView(@NonNull Context context, @Nullable AttributeSet attrs) { super(context, attrs); init(); }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        mLogToggle.setChecked(visibility == VISIBLE);
    }

    private void init() {
        inflate(getContext(), R.layout.view_logger, this);
        mLogTextView = findViewById(R.id.content_log_view);
        mLogTextView.setTypeface(Typeface.MONOSPACE);
        mLogTextView.setMaxLines(Integer.MAX_VALUE);
        mLogTextView.setEllipsize(null);
        mLogTextView.setVisibility(GONE);

        mLogToggle = findViewById(R.id.content_log_toggle_log);
        mLogToggle.setOnCheckedChangeListener(
                (compoundButton, isChecked) -> {
                    mLogTextView.setVisibility(isChecked ? VISIBLE : GONE);
                    if(isChecked) {
                        Logger.addLogListener(mLogListener);
                    }else{
                        mLogTextView.setText("");
                        Logger.removeLogListener(mLogListener);
                    }
                });
        mLogToggle.setChecked(false);
        ImageButton cancelButton = findViewById(R.id.log_view_cancel);
        cancelButton.setOnClickListener(view -> LoggerView.this.setVisibility(GONE));
        mScrollView = findViewById(R.id.content_log_scroll);
        mScrollView.setKeepFocusing(true);
        ToggleButton autoscrollToggle = findViewById(R.id.content_log_toggle_autoscroll);
        autoscrollToggle.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (isChecked) mScrollView.fullScroll(View.FOCUS_DOWN);
            mScrollView.setKeepFocusing(isChecked);
        });
        autoscrollToggle.setChecked(true);
        mLogListener = text -> {
            if(mLogTextView.getVisibility() != VISIBLE) return;
            post(() -> {
                mLogTextView.append(text + '\n');
                if(mScrollView.isKeepFocusing()) mScrollView.fullScroll(View.FOCUS_DOWN);
            });

        };
    }
}
""",
            encoding="utf-8",
        )
        logger_layout = app / "src/main/res/layout/view_logger.xml"
        logger_layout.parent.mkdir(parents=True, exist_ok=True)
        logger_layout.write_text(
            """<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    <View android:id="@+id/top_log_view" android:layout_width="match_parent"
        android:layout_height="70dp" app:layout_constraintTop_toTopOf="parent" />
    <TextView android:id="@+id/log_title" android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        app:layout_constraintTop_toTopOf="parent" />
    <ToggleButton android:id="@+id/content_log_toggle_autoscroll"
        android:layout_width="wrap_content" android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent" />
    <ToggleButton android:id="@+id/content_log_toggle_log"
        android:layout_width="wrap_content" android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent" />
    <ImageButton android:id="@+id/log_view_cancel"
        android:layout_width="70dp" android:layout_height="70dp"
        app:layout_constraintTop_toTopOf="parent" />
    <com.kdt.DefocusableScrollView
        android:id="@+id/content_log_scroll"
        android:layout_width="match_parent" android:layout_height="0dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/top_log_view">
        <TextView android:id="@+id/content_log_view" android:layout_width="match_parent"
            android:layout_height="wrap_content" android:textIsSelectable="true" />
    </com.kdt.DefocusableScrollView>
</androidx.constraintlayout.widget.ConstraintLayout>
""",
            encoding="utf-8",
        )

        downloader = app / "src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java"
        downloader.parent.mkdir(parents=True, exist_ok=True)
        downloader.write_text(
            """class MinecraftDownloader {
    private boolean isLocalProfile;
    private boolean isOnline;
    void start() {
        if(isLocalProfile || !isOnline) {
            File providedJsonFile = new File(Tools.DIR_HOME_VERSION + "/" + realVersion + "/" + realVersion + ".json");
            JMinecraftVersionList.Version providedJson = Tools.GLOBAL_GSON.fromJson(Tools.read(providedJsonFile.getAbsolutePath()), JMinecraftVersionList.Version.class);
            File vanillaJsonFile = new File(Tools.DIR_HOME_VERSION + "/" + providedJson.inheritsFrom + "/" + providedJson.inheritsFrom + ".json");
            versionMessage = providedJson.inheritsFrom != null ? providedJson.inheritsFrom : versionMessage;
            String tryagain = !isOnline ? "Please ensure you have an internet connection" : "Please try again on your Microsoft Account";
            Tools.showErrorRemote(versionMessage + " is not currently installed. "+ tryagain, e);
        } else {
            downloadGame(activity, version, realVersion);
        }
    }
}
""",
            encoding="utf-8",
        )

        local_login = app / "src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java"
        local_login.parent.mkdir(parents=True)
        local_login.write_text(
            """import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LocalLoginFragment {
    private Pattern mUsernameValidationPattern;

    LocalLoginFragment() {
        mUsernameValidationPattern = Pattern.compile("^[a-zA-Z0-9_]*$");
    }

    void onViewCreated() {
        // This is overkill but meh
        if (!hasOnlineProfile()){
            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        }
        if(!checkEditText()) return;
        ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{ mUsernameEditText.getText().toString(), "" });
    }

    private boolean checkEditText() {
        String text = mUsernameEditText.getText().toString();
        Matcher matcher = mUsernameValidationPattern.matcher(text);
        return !(text.isEmpty()
                || text.length() < 3
                || text.length() > 16
                || !matcher.find()
                || new File(Tools.DIR_ACCOUNT_NEW + "/" + text + ".json").exists()
        );
    }
}
""",
            encoding="utf-8",
        )

        login_layout = app / "src/main/res/layout/fragment_local_login.xml"
        login_layout.parent.mkdir(parents=True, exist_ok=True)
        login_layout.write_text(
            '<androidx.constraintlayout.widget.ConstraintLayout '
            'xmlns:android="http://schemas.android.com/apk/res/android">'
            '<EditText android:inputType="textEmailAddress" />'
            '<com.kdt.mcgui.MineButton android:text="@string/login_online_login_label" />'
            '</androidx.constraintlayout.widget.ConstraintLayout>\n',
            encoding="utf-8",
        )

        for relative in (
            "src/main/res/layout/fragment_launcher.xml",
            "src/main/res/layout-land/fragment_launcher.xml",
        ):
            launcher_layout = app / relative
            launcher_layout.parent.mkdir(parents=True, exist_ok=True)
            launcher_layout.write_text(
                """<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">
    <androidx.constraintlayout.widget.Guideline android:id="@+id/center_guideline" />
    <com.kdt.mcgui.LauncherMenuButton
        android:id="@+id/news_button"
        android:layout_width="0dp"
        app:layout_constraintTop_toTopOf="parent" />
    <View
        android:id="@+id/social_divider"
        app:layout_constraintTop_toTopOf="parent" />
    <com.kdt.mcgui.LauncherMenuButton
        android:id="@+id/discord_button"
        android:layout_width="0dp"
        app:layout_constraintTop_toTopOf="parent" />
    <com.kdt.mcgui.LauncherMenuButton
        android:id="@+id/open_files_button"
        android:layout_width="match_parent"
        app:layout_constraintTop_toBottomOf="@id/share_logs_button" />
</androidx.constraintlayout.widget.ConstraintLayout>
""",
                encoding="utf-8",
            )

        launcher_activity = app / "src/main/java/net/kdt/pojavlaunch/LauncherActivity.java"
        launcher_activity.parent.mkdir(parents=True, exist_ok=True)
        launcher_activity.write_text(
            """class LauncherActivity {
    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        Fragment fragment = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        if(!(fragment instanceof MainMenuFragment)) return false;
        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };
    void connectAccountSelector() {
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
    }
}
""",
            encoding="utf-8",
        )

        main_menu = app / "src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java"
        main_menu.parent.mkdir(parents=True, exist_ok=True)
        main_menu.write_text(
            """import android.widget.Button;
import android.widget.Toast;
import static net.kdt.pojavlaunch.Tools.openPath;
import java.io.File;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

class MainMenuFragment {
    void onViewCreated(View view) {
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        mOpenDirectoryButton.setOnClickListener((v)-> {
            if (Tools.isDemoProfile(v.getContext())) { // Say a different message when on demo profile
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
            } else if (!hasOnlineProfile()) { // Otherwise display the generic pop-up to log in
                hasNoOnlineProfileDialog(requireActivity());
            } else openPath(v.getContext(), getCurrentProfileDirectory(), false);

        });
        mNewsButton.setOnLongClickListener(v -> true);
    }
}
""",
            encoding="utf-8",
        )

        auth_selector = app / "src/main/java/net/kdt/pojavlaunch/fragments/SelectAuthFragment.java"
        auth_selector.write_text(
            """import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;

class SelectAuthFragment {
    void onViewCreated() {
        mMicrosoftButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));
        mLocalButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity(), () -> Tools.swapFragment(requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null)));
    }
}
""",
            encoding="utf-8",
        )

        profile_type = app / "src/main/java/net/kdt/pojavlaunch/fragments/ProfileTypeSelectFragment.java"
        profile_type.write_text(
            """import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;

import androidx.fragment.app.Fragment;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

class ProfileTypeSelectFragment {
    void onViewCreated() {
        view.findViewById(R.id.vanilla_profile).setOnClickListener(v -> Tools.swapFragment(requireActivity(), ProfileEditorFragment.class, ProfileEditorFragment.TAG, new Bundle(1)));
        view.findViewById(R.id.optifine_profile).setOnClickListener(v -> tryInstall(OptiFineInstallFragment.class, OptiFineInstallFragment.TAG));
    }

    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag){
        if(!hasOnlineProfile()){
            hasNoOnlineProfileDialog(requireActivity());
        } else {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
        }
    }
}
""",
            encoding="utf-8",
        )

        manifest = app / "src/main/AndroidManifest.xml"
        manifest.write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            '<application android:icon="@mipmap/ic_launcher" '
            'android:roundIcon="@mipmap/ic_launcher_round" '
            'android:label="@string/app_name" /></manifest>\n',
            encoding="utf-8",
        )

        prefs = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java"
        prefs.parent.mkdir(parents=True)
        prefs.write_text(
            """class LauncherPreferences {
    private static int findBestRAMAllocation(Context ctx) {
        int deviceRam = Tools.getTotalDeviceMemory(ctx);
        if (deviceRam < 1024) return 296;
        if (deviceRam < 1536) return 448;
        if (deviceRam < 2048) return 656;
        if (is32BitsDevice()) return 696;
        if (deviceRam < 3064) return 936;
        return 2048;
    }
    private static int findBestResolution(Context context, boolean isDevicePowerful) {
        int targetSide = isDevicePowerful ? 1080 : 720;
        return targetSide;
    }
}
""",
            encoding="utf-8",
        )

    def test_plugin_management_includes_maven_central(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            settings = (root / "settings.gradle").read_text(encoding="utf-8")
            plugin_management = settings.split("dependencyResolutionManagement", 1)[0]
            self.assertIn("pluginManagement", plugin_management)
            self.assertIn("gradlePluginPortal()", plugin_management)
            self.assertIn("google()", plugin_management)
            self.assertIn("mavenCentral()", plugin_management)
            customizer.patch_upstream(root)
            settings_again = (root / "settings.gradle").read_text(encoding="utf-8")
            self.assertEqual(settings, settings_again)
            self.assertEqual(settings.count("mavenCentral()"), 1)

    def test_local_profiles_can_download_missing_versions_when_connected(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            downloader = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java").read_text(encoding="utf-8")
            self.assertIn("if(!isOnline) {", downloader)
            self.assertNotIn("if(isLocalProfile || !isOnline)", downloader)
            self.assertIn("downloadGame(activity, version, realVersion);", downloader)

    def test_missing_version_json_guard_and_clear_offline_error(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            downloader = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java").read_text(encoding="utf-8")
            self.assertIn("!providedJsonFile.isFile()", downloader)
            self.assertIn("!providedJsonFile.canRead()", downloader)
            self.assertIn("providedJsonFile.length() == 0", downloader)
            self.assertIn("providedJson.inheritsFrom != null", downloader)
            self.assertIn("!vanillaJsonFile.isFile()", downloader)
            self.assertIn("!vanillaJsonFile.canRead()", downloader)
            self.assertIn("vanillaJsonFile.length() == 0", downloader)
            self.assertIn("Base Minecraft version ", downloader)
            self.assertIn("Connect to the internet and select Play again to download it.", downloader)
            self.assertIn("version metadata is missing/corrupt.", downloader)
            self.assertIn('String tryagain = "Connect to the internet and try again.";', downloader)
            self.assertNotIn("Please try again on your Microsoft Account", downloader)

    def test_rejects_missing_inherited_json_patch_anchor(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            downloader_path = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java"
            downloader_text = downloader_path.read_text(encoding="utf-8")
            anchor = "versionMessage = providedJson.inheritsFrom != null ? providedJson.inheritsFrom : versionMessage;"
            self.assertIn(anchor, downloader_text)
            downloader_path.write_text(downloader_text.replace(anchor, "versionMessage = versionMessage;", 1), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Could not add missing/unreadable/empty inherited Minecraft version JSON guard"):
                customizer.patch_upstream(root)

    def test_rejects_hardcoded_offline_username_submission(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            local_login_path = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java"
            login_text = local_login_path.read_text(encoding="utf-8")
            expected = 'ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{ mUsernameEditText.getText().toString(), "" });'
            self.assertIn(expected, login_text)
            hardcoded = 'ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{ "name", "" });'
            local_login_path.write_text(login_text.replace(expected, hardcoded, 1), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "does not submit the validated username"):
                customizer.patch_upstream(root)

    def test_verify_repair_version_action_checks_official_version_and_does_not_launch(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="main_repair_version">Verify / repair version</string>', strings)
            self.assertIn("Button mMyLauncherRepairVersionButton = view.findViewById(R.id.mylauncher_repair_version_button);", menu)
            self.assertIn("mMyLauncherRepairVersionButton.setOnClickListener", menu)
            self.assertIn("Tools.isOnline(v.getContext())", menu)
            self.assertIn("ProgressKeeper.getTaskCount() > 0", menu)
            self.assertIn("AsyncMinecraftDownloader.normalizeVersionId(selectedProfile.lastVersionId)", menu)
            self.assertIn("AsyncMinecraftDownloader.getListedVersion(normalizedVersionId)", menu)
            self.assertIn("new MinecraftDownloader().start(requireActivity(), selectedVersion, normalizedVersionId", menu)
            self.assertIn("onDownloadDone()", menu)
            self.assertIn("onDownloadFailed(Throwable throwable)", menu)
            self.assertIn("main_repair_version_failure", strings)
            repair_start = menu.index("mMyLauncherRepairVersionButton.setOnClickListener")
            repair_block = menu[repair_start:repair_start + 6500]
            self.assertNotIn("ExtraConstants.LAUNCH_GAME", repair_block)
            self.assertNotIn(r"\n\n        mMyLauncherRepairVersionButton.setOnClickListener", menu)
            for relative in ("src/main/res/layout/fragment_launcher.xml", "src/main/res/layout-land/fragment_launcher.xml"):
                layout = (root / "app_pojavlauncher" / relative).read_text(encoding="utf-8")
                self.assertEqual(layout.count('android:id="@+id/mylauncher_repair_version_button"'), 1)
                self.assertIn('android:text="@string/main_repair_version"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_shaderpacks_button"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_repair_version_button"', layout)

    def test_patches_branding_and_ids(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            build = (root / "app_pojavlauncher/build.gradle").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            settings = (root / "settings.gradle").read_text(encoding="utf-8")
            prefs = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java").read_text(encoding="utf-8")
            self.assertIn('applicationId "com.mf.mylauncher"', build)
            self.assertIn("com.mf.mylauncher.scoped.gamefolder", build)
            self.assertIn('"VoidCraft Launcher (Debug)"', build)
            self.assertIn('"app_name", "VoidCraft Launcher"', build)
            self.assertIn("'application_package', 'com.mf.mylauncher.debug'", build)
            self.assertIn("'application_package', 'com.mf.mylauncher'", build)
            self.assertIn("'shareProviderAuthority', 'com.mf.mylauncher.scoped.controlfolder.debug'", build)
            self.assertNotIn("org.angelauramc.amethyst", build)
            self.assertNotIn("Amethyst", strings)
            self.assertIn("VoidCraft Launcher crashed", strings)
            localized_strings = (root / "app_pojavlauncher/src/main/res/values-fr/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="app_name">VoidCraft Launcher</string>', localized_strings)
            self.assertNotIn("Amethyst", localized_strings)
            about_text = (root / "app_pojavlauncher/src/main/assets/about_en.txt").read_text(encoding="utf-8")
            self.assertIn("VoidCraft Launcher is customized and maintained by MF.", about_text)
            self.assertIn("Original upstream credits: Tran Khanh Duy and contributors", about_text)
            self.assertIn("* License:", about_text)
            self.assertIn("Third party components and their licenses", about_text)
            tools_java = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Tools.java").read_text(encoding="utf-8")
            self.assertIn('public static String APP_NAME = "VoidCraft Launcher";', tools_java)
            self.assertNotIn('public static String APP_NAME = "Amethyst";', tools_java)
            self.assertIn("rootProject.name='VoidCraft Launcher'", settings)
            self.assertIn("pluginManagement", settings)
            self.assertIn("mavenCentral()", settings.split("rootProject.name")[0])
            self.assertIn('<string name="main_mods_folder">Mods folder</string>', strings)
            self.assertIn('<string name="main_resourcepacks_folder">Resource packs</string>', strings)
            self.assertIn('<string name="main_world_saves_folder">World saves</string>', strings)
            self.assertIn('<string name="main_world_saves_backup_hint">World saves. Long-press to create a ZIP backup.</string>', strings)
            self.assertIn('<string name="main_logs_folder">Game logs</string>', strings)
            self.assertIn('<string name="main_screenshots_folder">Screenshots</string>', strings)
            self.assertIn('<string name="main_crash_reports_folder">Crash reports</string>', strings)
            self.assertIn("if (deviceRam <= 2048) return 512;", prefs)
            self.assertIn("int targetSide = Tools.getTotalDeviceMemory(context) <= 2048 ? 480 : (isDevicePowerful ? 1080 : 720);", prefs)
            self.assertNotIn("int targetSide = isDevicePowerful ? 1080 :", prefs)
            local_login = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java").read_text(encoding="utf-8")
            login_layout = (root / "app_pojavlauncher/src/main/res/layout/fragment_local_login.xml").read_text(encoding="utf-8")
            self.assertNotIn("if (!hasOnlineProfile())", local_login)
            self.assertNotIn("import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;", local_login)
            self.assertIn('ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{ mUsernameEditText.getText().toString(), "" });', " ".join(local_login.split()))
            self.assertIn('Pattern.compile("^[a-zA-Z0-9_]*$")', local_login)
            self.assertIn("text.length() < 3", local_login)
            self.assertIn("text.length() > 16", local_login)
            self.assertIn("!matcher.matches()", local_login)
            self.assertNotIn("!matcher.find()", local_login)
            self.assertIn('new File(Tools.DIR_ACCOUNT_NEW + "/" + text + ".json").exists()', local_login)
            self.assertIn('android:inputType="textPersonName"', login_layout)
            self.assertIn('android:text="@string/main_add_account"', login_layout)
            self.assertNotIn('android:text="@string/login_online_login_label"', login_layout)
            main_menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            self.assertIn("Button mMyLauncherAccountButton = view.findViewById(R.id.mylauncher_account_button);", main_menu)
            self.assertIn("mMyLauncherAccountButton.setOnClickListener", main_menu)
            self.assertIn("ExtraConstants.SELECT_AUTH_METHOD", main_menu)
            self.assertIn("Tools.isDemoProfile(v.getContext())", main_menu)
            self.assertIn("else openPath(v.getContext(), getCurrentProfileDirectory(), false);", main_menu)
            self.assertNotIn("else if (!hasOnlineProfile())", main_menu)
            self.assertIn("Button mMyLauncherModsButton = view.findViewById(R.id.mylauncher_mods_button);", main_menu)
            self.assertIn("mMyLauncherModsButton.setOnClickListener", main_menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "mods")', main_menu)
            self.assertIn("!modsDirectory.isDirectory() && !modsDirectory.mkdirs()", main_menu)
            self.assertIn("openPath(v.getContext(), modsDirectory, false);", main_menu)
            self.assertIn("import java.io.File;", main_menu)
            self.assertIn("import android.widget.Toast;", main_menu)
            self.assertIn("import static net.kdt.pojavlaunch.Tools.openPath;", main_menu)
            self.assertIn("Button mMyLauncherResourcePacksButton = view.findViewById(R.id.mylauncher_resourcepacks_button);", main_menu)
            self.assertIn("mMyLauncherResourcePacksButton.setOnClickListener", main_menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "resourcepacks")', main_menu)
            self.assertIn("!resourcePackDirectory.isDirectory() && !resourcePackDirectory.mkdirs()", main_menu)
            self.assertIn("openPath(v.getContext(), resourcePackDirectory, false);", main_menu)
            self.assertIn("Button mMyLauncherWorldSavesButton = view.findViewById(R.id.mylauncher_world_saves_button);", main_menu)
            self.assertIn("mMyLauncherWorldSavesButton.setOnClickListener", main_menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "saves")', main_menu)
            self.assertIn("!worldSavesDirectory.isDirectory() && !worldSavesDirectory.mkdirs()", main_menu)
            self.assertIn("openPath(v.getContext(), worldSavesDirectory, false);", main_menu)
            self.assertIn("R.string.main_world_saves_open_folder", main_menu)
            self.assertIn("R.string.main_world_backup_restore", main_menu)
            self.assertIn("R.string.main_world_backup_delete", main_menu)
            self.assertIn("showWorldBackupPicker(requireActivity(), profileDirectory)", main_menu)
            self.assertIn("showWorldBackupDeletePicker(requireActivity(), profileDirectory)", main_menu)
            self.assertIn("setPositiveButton(R.string.main_world_backup_restore", main_menu)
            self.assertIn("private void showWorldBackupPicker(", main_menu)
            self.assertIn("private void showWorldBackupDeletePicker(", main_menu)
            self.assertIn("private static void restoreWorldBackup(", main_menu)
            self.assertIn("private static void extractWorldBackup(", main_menu)
            self.assertIn("private static File uniqueRestoredWorldDestination(", main_menu)
            self.assertIn("The ZIP contains an unsafe path segment", main_menu)
            self.assertIn("The ZIP contains duplicate entries", main_menu)
            self.assertIn("Restoring world backup…", main_menu)
            self.assertIn("mMyLauncherWorldSavesButton.setContentDescription(getString(R.string.main_world_saves_backup_hint));", main_menu)
            self.assertIn("mMyLauncherWorldSavesButton.setOnLongClickListener", main_menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "backups")', main_menu)
            self.assertIn('new File(backupsDirectory, backupName + ".tmp")', main_menu)
            self.assertIn('zipWorldSaves(savesDirectory, savesDirectory.getCanonicalPath(), zipOut, new HashSet<String>())', main_menu)
            self.assertIn("A world-save path escapes the saves directory", main_menu)
            self.assertIn("!tempBackupFile.renameTo(backupFile)", main_menu)
            self.assertIn("new Thread(() ->", main_menu)
            self.assertIn("Button mMyLauncherLogsButton = view.findViewById(R.id.mylauncher_logs_button);", main_menu)
            self.assertIn("mMyLauncherLogsButton.setOnClickListener", main_menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "logs")', main_menu)
            self.assertIn("!logsDirectory.isDirectory() && !logsDirectory.mkdirs()", main_menu)
            self.assertIn("openPath(v.getContext(), logsDirectory, false);", main_menu)
            self.assertIn("Button mMyLauncherScreenshotsButton = view.findViewById(R.id.mylauncher_screenshots_button);", main_menu)
            self.assertIn("mMyLauncherScreenshotsButton.setOnClickListener", main_menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "screenshots")', main_menu)
            self.assertIn("!screenshotsDirectory.isDirectory() && !screenshotsDirectory.mkdirs()", main_menu)
            self.assertIn("openPath(v.getContext(), screenshotsDirectory, false);", main_menu)
            self.assertIn("Button mMyLauncherCrashReportsButton = view.findViewById(R.id.mylauncher_crash_reports_button);", main_menu)
            self.assertIn("mMyLauncherCrashReportsButton.setOnClickListener", main_menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "crash-reports")', main_menu)
            self.assertIn("!crashReportsDirectory.isDirectory() && !crashReportsDirectory.mkdirs()", main_menu)
            self.assertIn("openPath(v.getContext(), crashReportsDirectory, false);", main_menu)
            self.assertIn("Button mMyLauncherConfigButton = view.findViewById(R.id.mylauncher_config_button);", main_menu)
            self.assertIn("mMyLauncherConfigButton.setOnClickListener", main_menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "config")', main_menu)
            self.assertIn("!configDirectory.isDirectory() && !configDirectory.mkdirs()", main_menu)
            self.assertIn("openPath(v.getContext(), configDirectory, false);", main_menu)
            self.assertIn("Button mMyLauncherShaderPacksButton = view.findViewById(R.id.mylauncher_shaderpacks_button);", main_menu)
            self.assertIn("mMyLauncherShaderPacksButton.setOnClickListener", main_menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "shaderpacks")', main_menu)
            self.assertIn("!shaderPacksDirectory.isDirectory() && !shaderPacksDirectory.mkdirs()", main_menu)
            self.assertIn("openPath(v.getContext(), shaderPacksDirectory, false);", main_menu)
            activity = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java").read_text(encoding="utf-8")
            self.assertIn("ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);", activity)
            self.assertIn("Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);", activity)
            manifest = (root / "app_pojavlauncher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
            icon = (root / "app_pojavlauncher/src/main/res/drawable/mylauncher_icon.xml").read_text(encoding="utf-8")
            self.assertIn('android:icon="@drawable/mylauncher_icon"', manifest)
            self.assertIn('android:roundIcon="@drawable/mylauncher_icon"', manifest)
            self.assertIn('android:label="@string/app_name"', manifest)
            self.assertNotIn("org.angelauramc.amethyst", manifest)
            self.assertIn('android:strokeColor="#7B61FF"', icon)
            # All transformed resources must remain well-formed XML.
            for xml_path in (
                root / "app_pojavlauncher/src/main/res/layout/fragment_launcher.xml",
                root / "app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml",
                root / "app_pojavlauncher/src/main/res/layout/fragment_local_login.xml",
                root / "app_pojavlauncher/src/main/AndroidManifest.xml",
                root / "app_pojavlauncher/src/main/res/drawable/mylauncher_icon.xml",
                root / "app_pojavlauncher/src/main/res/values/strings.xml",
                root / "app_pojavlauncher/src/main/res/values/colors.xml",
            ):
                with self.subTest(xml=str(xml_path.relative_to(root))):
                    ET.parse(xml_path)
            auth_selector = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/SelectAuthFragment.java").read_text(encoding="utf-8")
            self.assertNotIn("hasNoOnlineProfileDialog", auth_selector)
            self.assertIn("mLocalButton.setOnClickListener(v -> Tools.swapFragment", auth_selector)
            self.assertIn("mMicrosoftButton.setOnClickListener(v -> Tools.swapFragment", auth_selector)
            for relative in ("src/main/res/layout/fragment_launcher.xml", "src/main/res/layout-land/fragment_launcher.xml"):
                launcher_layout = (root / "app_pojavlauncher" / relative).read_text(encoding="utf-8")
                self.assertTrue(all(line == line.rstrip() for line in launcher_layout.splitlines()), relative)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_hero_title"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_hero_subtitle"'), 1)
                self.assertIn('android:text="VoidCraft Launcher"', launcher_layout)
                self.assertIn('android:text="Minecraft Java Edition · by MF"', launcher_layout)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_account_button"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_mods_button"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_resourcepacks_button"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_world_saves_button"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_logs_button"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_screenshots_button"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_crash_reports_button"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_config_button"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_shaderpacks_button"'), 1)
                self.assertIn('android:text="VoidCraft Launcher"', launcher_layout)
                self.assertIn('android:text="Minecraft Java Edition · by MF"', launcher_layout)
                self.assertIn('android:text="@string/main_add_account"', launcher_layout)
                self.assertIn('android:drawableStart="@android:drawable/ic_menu_myplaces"', launcher_layout)
                self.assertIn('android:text="@string/main_mods_folder"', launcher_layout)
                self.assertIn('android:drawableStart="@android:drawable/ic_menu_manage"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/open_files_button"', launcher_layout)
                self.assertIn('android:text="@string/main_resourcepacks_folder"', launcher_layout)
                self.assertIn('android:drawableStart="@android:drawable/ic_menu_gallery"', launcher_layout)
                self.assertIn('android:text="@string/main_world_saves_folder"', launcher_layout)
                self.assertIn('android:drawableStart="@android:drawable/ic_menu_save"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_mods_button"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_resourcepacks_button"', launcher_layout)
                self.assertIn('android:text="@string/main_logs_folder"', launcher_layout)
                self.assertIn('android:drawableStart="@android:drawable/ic_menu_view"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_world_saves_button"', launcher_layout)
                self.assertIn('android:text="@string/main_screenshots_folder"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_logs_button"', launcher_layout)
                self.assertIn('android:text="@string/main_crash_reports_folder"', launcher_layout)
                self.assertIn('android:drawableStart="@android:drawable/ic_menu_info_details"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_screenshots_button"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_crash_reports_button"', launcher_layout)
                self.assertIn('android:text="@string/main_config_folder"', launcher_layout)
                self.assertIn('android:drawableStart="@android:drawable/ic_menu_preferences"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_config_button"', launcher_layout)
                self.assertIn('android:text="@string/main_shaderpacks_folder"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_shaderpacks_button"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toTopOf="@id/news_button"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_subtitle"', launcher_layout)


    def test_rejects_incomplete_offline_username_validation(self) -> None:
        invalidations = (
            (
                'mUsernameValidationPattern = Pattern.compile("^[a-zA-Z0-9_]*$");',
                'mUsernameValidationPattern = Pattern.compile("^[a-zA-Z]*$");',
                "Offline username validation pattern",
            ),
            ("text.length() < 3", "text.length() < 2", "Offline username validation is incomplete"),
            ("text.length() > 16", "text.length() > 64", "Offline username validation is incomplete"),
            ("!matcher.find()", "matcher.find()", "Offline username validation is incomplete"),
            (
                'new File(Tools.DIR_ACCOUNT_NEW + "/" + text + ".json").exists()',
                "false",
                "Offline username validation is incomplete",
            ),
            (
                "Matcher matcher = mUsernameValidationPattern.matcher(text);",
                "String matcher = mUsernameValidationPattern.matcher(text);",
                "Offline username validation is incomplete",
            ),
            (
                "return !(",
                "return (",
                "Offline username validation is incomplete",
            ),
            (
                "text.isEmpty()\n                || text.length() < 3",
                "text.isEmpty()\n                && text.length() < 3",
                "Offline username validation is incomplete",
            ),
        )
        for original, replacement, expected_error in invalidations:
            with self.subTest(missing_guard=original), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                self.fixture(root)
                local_login = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java"
                login_text = local_login.read_text(encoding="utf-8")
                self.assertIn(original, login_text)
                local_login.write_text(login_text.replace(original, replacement, 1), encoding="utf-8")
                with self.assertRaisesRegex(ValueError, expected_error):
                    customizer.patch_upstream(root)

    def test_rejects_broken_auth_selector_listener(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            activity = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java"
            text = activity.read_text(encoding="utf-8")
            good_guard = "if(!(fragment instanceof MainMenuFragment)) return false;"
            self.assertIn(good_guard, text)
            activity.write_text(
                text.replace(good_guard, "if(!(fragment instanceof MainMenuFragment)) Tools.backToMainMenu(this);", 1),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "SELECT_AUTH_METHOD listener"):
                customizer.patch_upstream(root)

    def test_mod_loader_gate_offers_direct_microsoft_sign_in(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            profile_type = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/ProfileTypeSelectFragment.java").read_text(encoding="utf-8")

            # Do not bypass Minecraft ownership validation; give eligible users an immediate sign-in path.
            self.assertIn("if (!hasOnlineProfile())", profile_type)
            self.assertIn(".setPositiveButton(R.string.login_online_login_label", profile_type)
            self.assertIn("MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG", profile_type)
            self.assertIn(".setNegativeButton(android.R.string.cancel, null)", profile_type)
            self.assertIn("Tools.swapFragment(requireActivity(), fragmentClass, tag, null);", profile_type)
            self.assertIn("R.id.vanilla_profile", profile_type)
            self.assertNotIn("hasNoOnlineProfileDialog", profile_type)

    def test_mods_folder_shortcut_opens_the_selected_profile_mods_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            for relative in ("src/main/res/layout/fragment_launcher.xml", "src/main/res/layout-land/fragment_launcher.xml"):
                layout = (root / "app_pojavlauncher" / relative).read_text(encoding="utf-8")
                self.assertIn('android:id="@+id/mylauncher_mods_button"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/open_files_button"', layout)
                self.assertIn('android:id="@+id/mylauncher_resourcepacks_button"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_mods_button"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_resourcepacks_button"', layout)
            self.assertIn('<string name="main_mods_folder">Mods folder</string>', strings)
            self.assertIn('<string name="main_resourcepacks_folder">Resource packs</string>', strings)
            self.assertIn('new File(getCurrentProfileDirectory(), "mods")', menu)
            self.assertIn('if (!modsDirectory.isDirectory() && !modsDirectory.mkdirs())', menu)
            self.assertIn('openPath(v.getContext(), modsDirectory, false);', menu)
            self.assertIn('Tools.isDemoProfile(v.getContext())', menu)
            self.assertNotIn('else if (!hasOnlineProfile())', menu)

    def test_mods_long_press_can_enable_disable_jars_safely(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="main_mods_folder_hint">Mods folder. Long-press to enable or disable JAR mods.</string>', strings)
            self.assertIn("mMyLauncherModsButton.setContentDescription(getString(R.string.main_mods_folder_hint));", menu)
            self.assertIn("mMyLauncherModsButton.setOnClickListener", menu)
            self.assertIn('openPath(v.getContext(), modsDirectory, false);', menu)
            self.assertIn("mMyLauncherModsButton.setOnLongClickListener", menu)
            self.assertIn("File[] modFiles = modsDirectory.listFiles", menu)
            self.assertIn('normalizedName.endsWith(".jar")', menu)
            self.assertIn('normalizedName.endsWith(".jar.disabled")', menu)
            self.assertIn('selectedName + ".disabled"', menu)
            self.assertIn('selectedName.substring(0, selectedName.length() - ".disabled".length())', menu)
            self.assertIn('setPositiveButton(wasDisabled ? "Enable" : "Disable"', menu)
            self.assertIn("destination.exists()", menu)
            self.assertIn("!selectedMod.renameTo(destination)", menu)
            self.assertIn("The selected mod is no longer available", menu)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)
            self.assertIn("A file already exists with that name", menu)
            self.assertLess(menu.index("mMyLauncherModsButton.setOnClickListener"), menu.index("mMyLauncherModsButton.setOnLongClickListener"))

    def test_resourcepacks_folder_shortcut_opens_the_selected_profile_resourcepacks_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            for relative in ("src/main/res/layout/fragment_launcher.xml", "src/main/res/layout-land/fragment_launcher.xml"):
                layout = (root / "app_pojavlauncher" / relative).read_text(encoding="utf-8")
                self.assertIn('android:id="@+id/mylauncher_resourcepacks_button"', layout)
                self.assertIn('android:text="@string/main_resourcepacks_folder"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_mods_button"', layout)
            self.assertIn('<string name="main_resourcepacks_folder">Resource packs</string>', strings)
            self.assertIn('new File(getCurrentProfileDirectory(), "resourcepacks")', menu)
            self.assertIn('if (!resourcePackDirectory.isDirectory() && !resourcePackDirectory.mkdirs())', menu)
            self.assertIn('openPath(v.getContext(), resourcePackDirectory, false);', menu)
            self.assertIn('Tools.isDemoProfile(v.getContext())', menu)

    def test_resourcepacks_long_press_can_enable_disable_zips_safely(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            self.assertIn("mMyLauncherResourcePacksButton.setOnLongClickListener", menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "resourcepacks")', menu)
            self.assertIn('normalizedName.endsWith(".zip")', menu)
            self.assertIn('normalizedName.endsWith(".zip.disabled")', menu)
            self.assertIn('selectedName + ".disabled"', menu)
            self.assertIn('selectedName.substring(0, selectedName.length() - ".disabled".length())', menu)
            self.assertIn('setPositiveButton(wasDisabled ? "Enable" : "Disable"', menu)
            self.assertIn("destination.exists()", menu)
            self.assertIn("!selectedPack.renameTo(destination)", menu)
            self.assertIn("The selected resource pack is no longer available", menu)
            self.assertIn('Tools.isDemoProfile(v.getContext())', menu)
            self.assertIn("A file already exists with that name", menu)
            self.assertIn("mMyLauncherResourcePacksButton.setContentDescription(getString(R.string.main_resourcepacks_folder_hint));", menu)
            self.assertLess(menu.index("mMyLauncherResourcePacksButton.setOnClickListener"), menu.index("mMyLauncherResourcePacksButton.setOnLongClickListener"))

    def test_world_saves_folder_shortcut_opens_the_selected_profile_saves_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            for relative in ("src/main/res/layout/fragment_launcher.xml", "src/main/res/layout-land/fragment_launcher.xml"):
                layout = (root / "app_pojavlauncher" / relative).read_text(encoding="utf-8")
                self.assertIn('android:id="@+id/mylauncher_world_saves_button"', layout)
                self.assertIn('android:text="@string/main_world_saves_folder"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_resourcepacks_button"', layout)
            self.assertIn('<string name="main_world_saves_folder">World saves</string>', strings)
            self.assertIn('new File(getCurrentProfileDirectory(), "saves")', menu)
            self.assertIn('if (!worldSavesDirectory.isDirectory() && !worldSavesDirectory.mkdirs())', menu)
            self.assertIn('openPath(v.getContext(), worldSavesDirectory, false);', menu)
            self.assertIn('Tools.isDemoProfile(v.getContext())', menu)

    def test_world_backup_delete_is_confirmed_and_confined_to_profile_backups(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="main_world_backup_delete">Delete a ZIP backup</string>', strings)
            self.assertIn('<string name="main_world_backup_delete_confirm_message">This permanently removes the selected backup ZIP, not any restored world.</string>', strings)
            self.assertIn("private void showWorldBackupDeletePicker(", menu)
            self.assertIn("main_world_backup_delete_confirm_message", menu)
            self.assertIn("selectedBackup.getName()", menu)
            self.assertIn(".setPositiveButton(R.string.main_world_backup_delete", menu)
            self.assertIn("selectedBackup.getCanonicalFile()", menu)
            self.assertIn("canonicalBackupsParent.equals(canonicalProfile)", menu)
            self.assertIn("!canonicalSelectedBackup.getName().equals(selectedBackup.getName())", menu)
            self.assertIn("canonicalSelectedBackup.getParentFile().equals(canonicalBackupsDirectory)", menu)
            self.assertIn("!canonicalSelectedBackup.delete()", menu)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)

    def test_world_backup_pickers_show_size_and_last_modified_metadata(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            self.assertIn("private static String worldBackupDisplayLabel(File backupFile)", menu)
            self.assertIn("backupFile.length()", menu)
            self.assertIn("backupFile.lastModified()", menu)
            self.assertIn("java.text.DateFormat.getDateTimeInstance(", menu)
            self.assertIn('sizeLabel + " · " + modifiedLabel', menu)
            self.assertIn("return backupFile.getName() +", menu)
            self.assertIn('return backupFile.getName() + "\\n" + sizeLabel', menu)
            self.assertGreaterEqual(menu.count("backupNames[i] = worldBackupDisplayLabel(backupFiles[i]);"), 3)
            self.assertEqual(menu.count("backupNames[i] = backupFiles[i].getName();"), 0)
            self.assertGreaterEqual(menu.count("Long.compare(right.lastModified(), left.lastModified())"), 3)
            self.assertIn("private void showWorldBackupPicker(", menu)
            self.assertIn("private void showWorldBackupVerifyPicker(", menu)
            self.assertIn("private void showWorldBackupDeletePicker(", menu)

    def test_world_backup_verification_checks_paths_entries_crc_and_bounds(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="main_world_backup_verify">Verify a ZIP backup</string>', strings)
            self.assertIn('<string name="main_world_backup_verify_picker_title">Choose a backup to verify</string>', strings)
            self.assertIn('<string name="main_world_backup_verify_progress">Verifying world backup…</string>', strings)
            self.assertIn('<string name="main_world_backup_verify_success">World backup passed archive checks</string>', strings)
            self.assertIn('<string name="main_world_backup_verify_failure">World backup verification failed</string>', strings)
            self.assertIn("showWorldBackupVerifyPicker(requireActivity(), profileDirectory)", menu)
            self.assertIn("private void showWorldBackupVerifyPicker(", menu)
            self.assertIn("private static void verifyWorldBackupArchive(", menu)
            self.assertIn("private static File validatedWorldBackupFile(", menu)
            self.assertIn("private static int scanWorldBackupZip(", menu)
            self.assertIn("canonicalBackupsParent.equals(canonicalProfile)", menu)
            self.assertIn("!canonicalSelectedBackup.getName().equals(selectedBackup.getName())", menu)
            self.assertIn("new java.util.zip.ZipFile(canonicalBackup)", menu)
            self.assertIn("entry.getCrc() >= 0L && entry.getCrc() != crc.getValue()", menu)
            self.assertIn("entry.getSize() != entryUncompressedBytes", menu)
            self.assertIn("The ZIP contains a checksum mismatch", menu)
            self.assertIn("while ((count = zipInput.read(buffer)) != -1)", menu)
            self.assertIn("entryCount > maxEntries", menu)
            self.assertIn("count > maxUncompressedBytes - uncompressedBytes", menu)
            self.assertIn("!seenEntries.add(normalizedName)", menu)
            self.assertIn("fileEntries.contains(parentPath)", menu)
            self.assertIn("zipInput.closeEntry()", menu)
            self.assertIn("VoidCraftWorldBackupVerify", menu)
            self.assertIn("main_world_backup_verify_progress", menu)
            self.assertIn("main_world_backup_verify_failure", menu)
            self.assertIn("main_world_backup_verify_success", menu)
            self.assertIn("if (which == 3)", menu)

    def test_world_saves_long_press_creates_safe_zip_backup(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="main_world_saves_backup_hint">World saves. Long-press to create a ZIP backup.</string>', strings)
            self.assertIn('<string name="main_world_saves_open_folder">Open saves folder</string>', strings)
            self.assertIn('<string name="main_world_backup_restore">Restore a ZIP backup</string>', strings)
            self.assertIn('<string name="main_world_backup_picker_title">Choose a world backup</string>', strings)
            self.assertIn('<string name="main_world_backup_confirm_message">Existing worlds will not be overwritten. Name conflicts will be restored with a suffix.</string>', strings)
            self.assertIn("mMyLauncherWorldSavesButton.setOnLongClickListener", menu)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "backups")', menu)
            self.assertIn('String backupName = "worlds-" + System.currentTimeMillis() + ".zip";', menu)
            self.assertIn('new File(backupsDirectory, backupName + ".tmp")', menu)
            self.assertIn("private static void zipWorldSaves(", menu)
            self.assertIn("current.getCanonicalPath()", menu)
            self.assertIn('canonicalPath.startsWith(rootCanonicalPath + File.separator)', menu)
            self.assertIn("new ZipEntry(relativePath)", menu)
            self.assertIn("new byte[16384]", menu)
            self.assertIn("if (!tempBackupFile.renameTo(backupFile))", menu)
            self.assertIn("World backup saved:", menu)
            self.assertIn("World backup failed:", menu)

    def test_world_backup_restore_is_staged_and_does_not_overwrite_existing_worlds(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn("showWorldBackupPicker(requireActivity(), profileDirectory)", menu)
            self.assertIn("setItems(new String[]", menu)
            self.assertIn("private static void extractWorldBackup(", menu)
            self.assertIn("entryName.indexOf((char) 92) >= 0", menu)
            self.assertIn("entryName.indexOf(':') >= 0", menu)
            self.assertIn('segment.equals("..")', menu)
            self.assertIn("outputPath.startsWith(stagingRoot + File.separator)", menu)
            self.assertIn("new ZipInputStream(new FileInputStream(backupArchive))", menu)
            self.assertIn("private static File uniqueRestoredWorldDestination(", menu)
            self.assertIn('originalName + " (restored"', menu)
            self.assertIn("new Thread(() ->", menu)
            self.assertIn("deleteRestoreTree(stagingDirectory)", menu)
            self.assertIn("Existing worlds will not be overwritten", strings)
            self.assertIn('<string name="main_world_backup_confirm_title">Restore world backup?</string>', strings)
            self.assertIn('<string name="main_world_backup_picker_title">Choose a world backup</string>', strings)
            self.assertIn('archivePath.startsWith(backupRoot + File.separator)', menu)
            self.assertIn('outputFile.exists()', menu)
            self.assertIn("new android.os.StatFs(stagingDirectory.getAbsolutePath()).getAvailableBytes()", menu)
            self.assertIn("storageBudgetBytes", menu)
            self.assertIn("final long reserveBytes = Math.min(256L * 1024L * 1024L, Math.max(8L * 1024L * 1024L, availableBytes / 10L));", menu)
            self.assertIn("The ZIP exceeds the available storage budget or the 20 GB safety limit", menu)
            self.assertIn("Not enough free storage to restore a world backup safely", menu)

    def test_logs_folder_shortcut_opens_the_selected_profile_logs_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="main_logs_folder">Game logs</string>', strings)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "logs")', menu)
            self.assertIn('if (!logsDirectory.isDirectory() && !logsDirectory.mkdirs())', menu)
            self.assertIn('openPath(v.getContext(), logsDirectory, false);', menu)
            for relative in ("src/main/res/layout/fragment_launcher.xml", "src/main/res/layout-land/fragment_launcher.xml"):
                layout = (root / "app_pojavlauncher" / relative).read_text(encoding="utf-8")
                self.assertEqual(layout.count('android:id="@+id/mylauncher_logs_button"'), 1)
                self.assertIn('android:text="@string/main_logs_folder"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_world_saves_button"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_logs_button"', layout)

    def test_live_log_filter_is_case_insensitive_bounded_and_clearable(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            logger_view = (root / "app_pojavlauncher/src/main/java/com/kdt/LoggerView.java").read_text(encoding="utf-8")
            logger_layout = root / "app_pojavlauncher/src/main/res/layout/view_logger.xml"
            layout_text = logger_layout.read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn("private void renderFilteredLog()", logger_view)
            self.assertIn("line.toLowerCase(Locale.ROOT).contains(filter)", logger_view)
            self.assertIn("MAX_LOG_BUFFER_CHARS = 300000", logger_view)
            self.assertIn("mLogBuffer.delete(0, removeChars)", logger_view)
            self.assertIn('mLogFilterInput.setText("")', logger_view)
            self.assertIn("mLogToggle.isChecked()", logger_view)
            self.assertIn("main_live_log_filter_hint", strings)
            self.assertIn("main_live_log_count_matches", strings)
            self.assertIn("main_live_log_truncated", strings)
            ET.parse(logger_layout)
            for view_id in ("log_filter_row", "content_log_filter_input", "content_log_filter_clear", "content_log_filter_count"):
                self.assertEqual(layout_text.count('android:id="@+id/' + view_id + '"'), 1)
            self.assertIn('app:layout_constraintTop_toBottomOf="@+id/log_filter_row"', layout_text)

    def test_long_press_game_logs_offers_copy_share_and_safe_diagnosis(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn("mMyLauncherLogsButton.setOnLongClickListener", menu)
            self.assertIn('new File(new File(getCurrentProfileDirectory(), "logs"), "latest.log")', menu)
            self.assertIn("final long maxLogBytes = 1024L * 1024L;", menu)
            self.assertIn("logReader.seek(offset);", menu)
            self.assertIn("access[_-]?token|refresh[_-]?token|client[_-]?secret|authorization", menu)
            self.assertIn('Bearer [REDACTED]', menu)
            self.assertIn("android.content.ClipboardManager", menu)
            self.assertIn('new android.app.AlertDialog.Builder(requireContext())', menu)
            self.assertIn('setItems(new String[]{"Copy latest log", "Share latest log", getString(R.string.main_log_diagnose_action)}', menu)
            self.assertIn("toLowerCase(java.util.Locale.ROOT)", menu)
            self.assertIn("substring(Math.max(0, normalizedLog.length() - 65536))", menu)
            self.assertIn('diagnosisText.contains("virglrenderer initialization failed")', menu)
            self.assertNotIn('diagnosisText.contains("virglrenderer") ||', menu)
            self.assertIn('diagnosisText.contains("/versions/")', menu)
            self.assertIn('diagnosisText.contains("no such file")', menu)
            self.assertIn('diagnosisText.contains("missing")))) {', menu)
            self.assertIn("R.string.main_log_diagnosis_memory", menu)
            self.assertIn("R.string.main_log_diagnosis_renderer", menu)
            self.assertIn("R.string.main_log_diagnosis_missing_files", menu)
            self.assertIn("R.string.main_log_diagnosis_mod", menu)
            self.assertIn("R.string.main_log_diagnosis_network", menu)
            self.assertIn("R.string.main_log_diagnosis_storage", menu)
            self.assertIn("setMessage(diagnosisResource)", menu)
            self.assertIn("Intent.ACTION_SEND", menu)
            self.assertIn("Intent.EXTRA_SUBJECT", menu)
            self.assertIn("Intent.EXTRA_TEXT", menu)
            self.assertIn("review before sending", menu)
            self.assertIn("Latest game log copied with sensitive tokens redacted", menu)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)
            self.assertIn('<string name="main_log_diagnose_action">Diagnose latest log</string>', strings)
            self.assertIn('<string name="main_log_diagnosis_title">Possible cause from latest log</string>', strings)
            self.assertIn("available device RAM", strings)
            self.assertNotIn("device's available RAM", strings)
            for resource_name in (
                "main_log_diagnosis_unknown", "main_log_diagnosis_storage",
                "main_log_diagnosis_memory", "main_log_diagnosis_java",
                "main_log_diagnosis_renderer", "main_log_diagnosis_missing_files",
                "main_log_diagnosis_mod", "main_log_diagnosis_network",
            ):
                self.assertRegex(strings, r'<string\s+name="' + resource_name + r'">[^<]+</string>')

    def test_screenshots_folder_shortcut_opens_the_selected_profile_screenshots_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="main_screenshots_folder">Screenshots</string>', strings)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "screenshots")', menu)
            self.assertIn('if (!screenshotsDirectory.isDirectory() && !screenshotsDirectory.mkdirs())', menu)
            self.assertIn('openPath(v.getContext(), screenshotsDirectory, false);', menu)
            for relative in ("src/main/res/layout/fragment_launcher.xml", "src/main/res/layout-land/fragment_launcher.xml"):
                layout = (root / "app_pojavlauncher" / relative).read_text(encoding="utf-8")
                self.assertEqual(layout.count('android:id="@+id/mylauncher_screenshots_button"'), 1)
                self.assertIn('android:text="@string/main_screenshots_folder"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_logs_button"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_screenshots_button"', layout)
                self.assertIn('android:id="@+id/mylauncher_crash_reports_button"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_crash_reports_button"', layout)

    def test_crash_reports_shortcut_opens_the_selected_profile_crash_reports_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="main_crash_reports_folder">Crash reports</string>', strings)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "crash-reports")', menu)
            self.assertIn('if (!crashReportsDirectory.isDirectory() && !crashReportsDirectory.mkdirs())', menu)
            self.assertIn('openPath(v.getContext(), crashReportsDirectory, false);', menu)
            for relative in ("src/main/res/layout/fragment_launcher.xml", "src/main/res/layout-land/fragment_launcher.xml"):
                layout = (root / "app_pojavlauncher" / relative).read_text(encoding="utf-8")
                self.assertEqual(layout.count('android:id="@+id/mylauncher_crash_reports_button"'), 1)
                self.assertIn('android:text="@string/main_crash_reports_folder"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_screenshots_button"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_crash_reports_button"', layout)

    def test_long_press_crash_reports_shares_bounded_redacted_latest_report(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            self.assertIn("mMyLauncherCrashReportsButton.setOnLongClickListener", menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "crash-reports")', menu)
            self.assertIn('normalizedName.endsWith(".txt") || normalizedName.endsWith(".log")', menu)
            self.assertIn("Long.compare(right.lastModified(), left.lastModified())", menu)
            self.assertIn("final long maxShareBytes = 2L * 1024L * 1024L;", menu)
            self.assertIn("reportReader.seek(offset);", menu)
            self.assertIn(r'reportText = "[Earlier crash report content omitted; sharing final 2 MiB only.]\n" + reportText;', menu)
            self.assertIn("access[_-]?token|refresh[_-]?token|client[_-]?secret|authorization", menu)
            self.assertIn('Bearer [REDACTED]', menu)
            self.assertIn("Intent.ACTION_SEND", menu)
            self.assertIn("Intent.EXTRA_TEXT", menu)
            self.assertIn("review before sending", menu)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)

    def test_config_folder_shortcut_opens_the_selected_profile_config_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="main_config_folder">Config folder</string>', strings)
            self.assertIn('<string name="main_config_export_hint">Config folder. Long-press to export, import or reset interface settings.</string>', strings)
            self.assertIn("Account data, session tokens, profile selection", strings)
            self.assertIn("Button mMyLauncherConfigButton = view.findViewById(R.id.mylauncher_config_button);", menu)
            self.assertIn("mMyLauncherConfigButton.setOnClickListener", menu)
            self.assertIn("mMyLauncherConfigButton.setOnLongClickListener", menu)
            self.assertIn('<string name="main_config_export_subject">VoidCraft Launcher safe settings export</string>', strings)
            self.assertIn('<string name="main_config_export_no_values">No saved portable settings were found to export.</string>', strings)
            self.assertIn("profile selection, free-form Java arguments", strings)
            export_start = menu.index("mMyLauncherConfigButton.setOnLongClickListener")
            export_end = menu.index("mMyLauncherShaderPacksButton.setOnClickListener", export_start)
            export_block = menu[export_start:export_end]
            self.assertIn("LauncherPreferences.DEFAULT_PREF.getAll()", export_block)
            self.assertIn("MYLAUNCHER_PORTABLE_SETTING_KEYS", export_block)
            self.assertIn('"buttonscale"', menu)
            self.assertIn('"allocation"', menu)
            self.assertIn("MYLAUNCHER_PORTABLE_BOOLEAN_SETTING_KEYS", menu)
            self.assertIn("MYLAUNCHER_PORTABLE_INTEGER_SETTING_KEYS", menu)
            self.assertIn("value instanceof Boolean", export_block)
            self.assertIn("value instanceof Integer", export_block)
            self.assertIn('new org.json.JSONObject(portableEnvelope).toString()', export_block)
            self.assertIn("Intent.ACTION_SEND", export_block)
            self.assertIn("Intent.EXTRA_TEXT", export_block)
            self.assertNotIn('"javaArgs"', export_block)
            self.assertNotIn('"defaultCtrl"', export_block)
            self.assertNotIn('"currentProfile"', export_block)
            self.assertNotIn("PREF_KEY_CURRENT_PROFILE", export_block)
            self.assertIn("Tools.isDemoProfile(v.getContext())", export_block)
            self.assertIn('new File(getCurrentProfileDirectory(), "config")', menu)
            self.assertIn('if (!configDirectory.isDirectory() && !configDirectory.mkdirs())', menu)
            self.assertIn('openPath(v.getContext(), configDirectory, false);', menu)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)
            for relative in ("src/main/res/layout/fragment_launcher.xml", "src/main/res/layout-land/fragment_launcher.xml"):
                layout = (root / "app_pojavlauncher" / relative).read_text(encoding="utf-8")
                self.assertEqual(layout.count('android:id="@+id/mylauncher_config_button"'), 1)
                self.assertIn('android:text="@string/main_config_folder"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_crash_reports_button"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_config_button"', layout)

    def test_config_settings_import_is_versioned_bounded_allowlisted_and_confirmed(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")

            self.assertIn('<string name="main_config_actions_title">Portable launcher settings</string>', strings)
            self.assertIn('<string name="main_config_import_action">Import portable settings</string>', strings)
            self.assertIn('<string name="main_config_import_apply">Apply settings</string>', strings)
            self.assertIn("mMyLauncherConfigButton.setOnLongClickListener", menu)
            self.assertIn("Intent.ACTION_OPEN_DOCUMENT", menu)
            self.assertIn("Intent.EXTRA_MIME_TYPES", menu)
            self.assertIn("MYLAUNCHER_SETTINGS_IMPORT_REQUEST_CODE", menu)
            self.assertIn("MYLAUNCHER_SETTINGS_IMPORT_MAX_BYTES = 65536", menu)
            self.assertIn("totalBytes > MYLAUNCHER_SETTINGS_IMPORT_MAX_BYTES", menu)
            self.assertIn('"voidcraft-launcher-settings".equals(envelope.optString("format"))', menu)
            self.assertIn('((Integer) envelope.opt("version")).intValue() != 1', menu)
            self.assertIn("Unknown settings export field", menu)
            self.assertIn("containsPortableSettingKey(MYLAUNCHER_PORTABLE_SETTING_KEYS, key)", menu)
            self.assertIn("MYLAUNCHER_PORTABLE_BOOLEAN_SETTING_KEYS", menu)
            self.assertIn("MYLAUNCHER_PORTABLE_INTEGER_SETTING_KEYS", menu)
            self.assertIn("validatePortableSettingValue(key, settings.get(key), requireContext())", menu)
            self.assertIn("Math.min(intValue, recommendedPortableAllocation(context))", menu)
            self.assertIn("setPositiveButton(R.string.main_config_import_apply", menu)
            self.assertIn("LauncherPreferences.DEFAULT_PREF.edit()", menu)
            self.assertIn("editor.apply();", menu)
            self.assertIn("No settings were changed", strings)
            self.assertIn("Account/session data, profile selection", strings)

            keys_start = menu.index("private static final String[] MYLAUNCHER_PORTABLE_SETTING_KEYS")
            keys_end = menu.index("private static final String[] MYLAUNCHER_PORTABLE_BOOLEAN_SETTING_KEYS", keys_start)
            allowed_keys = menu[keys_start:keys_end]
            self.assertNotIn('"javaArgs"', allowed_keys)
            self.assertNotIn('"defaultCtrl"', allowed_keys)
            self.assertNotIn('"currentProfile"', allowed_keys)
            self.assertNotIn("PREF_KEY_CURRENT_PROFILE", allowed_keys)
            self.assertLess(
                menu.index("setPositiveButton(R.string.main_config_import_apply"),
                menu.index("editor.apply();"),
            )

    def test_config_interface_reset_is_confirmed_and_limited_to_ui_keys(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")

            self.assertIn('<string name="main_config_reset_action">Reset interface settings</string>', strings)
            self.assertIn('<string name="main_config_reset_confirm_title">Reset interface settings?</string>', strings)
            self.assertIn("renderer settings, accounts, profile selection, Java arguments", strings.lower())
            self.assertIn("getString(R.string.main_config_reset_action)", menu)
            self.assertIn("setTitle(R.string.main_config_reset_confirm_title)", menu)
            self.assertIn("setMessage(R.string.main_config_reset_confirm_message)", menu)
            self.assertIn("setPositiveButton(android.R.string.ok", menu)
            self.assertIn("for (String key : MYLAUNCHER_INTERFACE_RESET_KEYS)", menu)
            self.assertIn("if (storedPreferences.containsKey(key))", menu)
            self.assertIn("editor.remove(key);", menu)
            self.assertIn("editor.apply();", menu)
            self.assertIn("getString(R.string.main_config_reset_success, resetCount)", menu)
            self.assertIn("R.string.main_config_reset_no_values", menu)

            reset_start = menu.index("private static final String[] MYLAUNCHER_INTERFACE_RESET_KEYS")
            reset_end = menu.index("\\n    };", reset_start)
            reset_keys = menu[reset_start:reset_end]
            for expected in (
                '"buttonscale"', '"mousescale"', '"mousespeed"', '"ignoreNotch"',
                '"resolutionRatio"', '"enableGyro"', '"gyroSensitivity"',
                '"gamepad_deadzone_scale"', '"keyboardPanning"',
            ):
                self.assertIn(expected, reset_keys)
            for excluded in (
                '"allocation"', '"javaArgs"', '"defaultCtrl"', '"defaultRuntime"',
                '"currentProfile"', '"force_vsync"', '"zinkPreferSystemDriver"',
                '"verifyManifest"', '"downloadSource"',
            ):
                self.assertNotIn(excluded, reset_keys)

            reset_confirm = menu.index("setPositiveButton(android.R.string.ok")
            reset_remove = menu.index("editor.remove(key);")
            self.assertLess(reset_confirm, reset_remove)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)

    def test_shaderpacks_folder_shortcut_opens_the_selected_profile_shaderpacks_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="main_shaderpacks_folder">Shader packs</string>', strings)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "shaderpacks")', menu)
            self.assertIn('if (!shaderPacksDirectory.isDirectory() && !shaderPacksDirectory.mkdirs())', menu)
            self.assertIn('openPath(v.getContext(), shaderPacksDirectory, false);', menu)
            for relative in ("src/main/res/layout/fragment_launcher.xml", "src/main/res/layout-land/fragment_launcher.xml"):
                layout = (root / "app_pojavlauncher" / relative).read_text(encoding="utf-8")
                self.assertEqual(layout.count('android:id="@+id/mylauncher_shaderpacks_button"'), 1)
                self.assertIn('android:text="@string/main_shaderpacks_folder"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_screenshots_button"', layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_shaderpacks_button"', layout)

    def test_shaderpacks_long_press_can_enable_disable_zips_safely(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="main_shaderpacks_folder_hint">Shader packs. Long-press to enable or disable ZIP packs.</string>', strings)
            self.assertIn("mMyLauncherShaderPacksButton.setContentDescription(getString(R.string.main_shaderpacks_folder_hint));", menu)
            self.assertIn("mMyLauncherShaderPacksButton.setOnLongClickListener", menu)
            self.assertIn('new File(getCurrentProfileDirectory(), "shaderpacks")', menu)
            self.assertIn('normalizedName.endsWith(".zip")', menu)
            self.assertIn('normalizedName.endsWith(".zip.disabled")', menu)
            self.assertIn('selectedName + ".disabled"', menu)
            self.assertIn('selectedName.substring(0, selectedName.length() - ".disabled".length())', menu)
            self.assertIn('setPositiveButton(wasDisabled ? "Enable" : "Disable"', menu)
            self.assertIn("destination.exists()", menu)
            self.assertIn("!selectedPack.renameTo(destination)", menu)
            self.assertIn("The selected shader pack is no longer available", menu)
            self.assertIn("A file already exists with that name", menu)
            self.assertIn("Tools.isDemoProfile(v.getContext())", menu)
            self.assertIn("!shaderPackDirectory.isDirectory()", menu)
            self.assertIn("new File(directory, name).isFile()", menu)
            self.assertIn("selectedPath.startsWith(packsRoot + File.separator)", menu)
            self.assertIn("destinationPath.startsWith(packsRoot + File.separator)", menu)
            self.assertIn("!selectedPack.renameTo(destination)", menu)
            self.assertLess(menu.index("mMyLauncherShaderPacksButton.setOnClickListener"), menu.index("mMyLauncherShaderPacksButton.setOnLongClickListener"))
            handler_start = menu.index("mMyLauncherShaderPacksButton.setOnLongClickListener")
            shader_handler = menu[handler_start:handler_start + 8000]
            self.assertIn('new File(getCurrentProfileDirectory(), "shaderpacks")', shader_handler)
            self.assertNotIn('new File(getCurrentProfileDirectory(), "resourcepacks")', shader_handler)
            self.assertLess(menu.index("mMyLauncherShaderPacksButton.setOnClickListener"), handler_start)

    def test_is_idempotent(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            customizer.patch_upstream(root)
            first_snapshot = {
                path.relative_to(root).as_posix(): path.read_bytes()
                for path in root.rglob("*")
                if path.is_file()
            }
            customizer.patch_upstream(root)
            second_snapshot = {
                path.relative_to(root).as_posix(): path.read_bytes()
                for path in root.rglob("*")
                if path.is_file()
            }
            self.assertEqual(first_snapshot, second_snapshot)

    def test_rejects_missing_launcher_button_instead_of_silently_skipping_it(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            layout = root / "app_pojavlauncher/src/main/res/layout/fragment_launcher.xml"
            layout.write_text(
                layout.read_text(encoding="utf-8").replace(
                    'android:id="@+id/discord_button"',
                    'android:id="@+id/discord_missing"',
                ),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "Could not find discord_button"):
                customizer.patch_upstream(root)

    def test_rejects_minimum_sdk_above_android_9(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            gradle = root / "app_pojavlauncher/build.gradle"
            gradle.write_text(
                gradle.read_text(encoding="utf-8").replace("minSdkVersion 21", "minSdkVersion 29"),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "above Android 9/API 28"):
                customizer.patch_upstream(root)

    def test_rejects_malformed_generated_xml(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            login_layout = root / "app_pojavlauncher/src/main/res/layout/fragment_local_login.xml"
            login_layout.write_text(
                '<androidx.constraintlayout.widget.ConstraintLayout '
                'xmlns:android="http://schemas.android.com/apk/res/android">'
                '<EditText android:inputType="textEmailAddress" />'
                '<com.kdt.mcgui.MineButton android:text="@string/login_online_login_label" />\n',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "Generated Android XML is invalid"):
                customizer.patch_upstream(root)

    def test_missing_upstream_file_fails_clearly(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(FileNotFoundError):
                customizer.patch_upstream(Path(temp))


class ApplyLogoTests(unittest.TestCase):
    def test_applies_voidcraft_icon_to_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            app = root / "app_pojavlauncher"
            (app / "src/main").mkdir(parents=True)
            manifest = app / "src/main/AndroidManifest.xml"
            manifest.write_text(
                '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
                '<application android:icon="@mipmap/ic_launcher" '
                'android:roundIcon="@mipmap/ic_launcher_round" /></manifest>',
                encoding="utf-8",
            )
            logo_applier.main(root)
            manifest_root = ET.parse(manifest).getroot()
            app_node = manifest_root.find("application")
            self.assertIsNotNone(app_node)
            assert app_node is not None
            self.assertEqual(app_node.get("{http://schemas.android.com/apk/res/android}icon"),
                             "@drawable/voidcraft_launcher_logo")
            self.assertEqual(app_node.get("{http://schemas.android.com/apk/res/android}roundIcon"),
                             "@drawable/voidcraft_launcher_logo")
            icon = app / "src/main/res/drawable/voidcraft_launcher_logo.xml"
            ET.parse(icon)
            icon_text = icon.read_text(encoding="utf-8")
            self.assertIn('android:strokeColor="#6EF3FF"', icon_text)
            self.assertIn('android:strokeColor="#7B61FF"', icon_text)


class BuildWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        workflow_path = Path(__file__).parents[1] / "workflows" / "build.yml"
        cls.workflow = workflow_path.read_text(encoding="utf-8")
        cls.apply_logo_script = Path(__file__).with_name("apply_logo.py").read_text(encoding="utf-8")
        cls.smoke_script = Path(__file__).with_name("emulator_smoke_test.sh").read_text(encoding="utf-8")
        cls.kvm_script = Path(__file__).with_name("prepare_emulator_kvm.sh").read_text(encoding="utf-8")

    def test_ssh_submodule_urls_are_rewritten_before_checkout(self) -> None:
        ssh_rewrite = 'git config --global url."https://github.com/".insteadOf git@github.com:'
        nested_ssh_rewrite = 'git config --global --add url."https://github.com/".insteadOf ssh://git@github.com/'
        sync = "git -C upstream submodule sync --recursive"
        update = "git -C upstream submodule update --init --recursive"
        self.assertIn(ssh_rewrite, self.workflow)
        self.assertIn(nested_ssh_rewrite, self.workflow)
        self.assertLess(self.workflow.index(ssh_rewrite), self.workflow.index(update))
        self.assertLess(self.workflow.index(nested_ssh_rewrite), self.workflow.index(update))
        self.assertLess(self.workflow.index(sync), self.workflow.index(update))
        self.assertIn("grep -Eq '^[+-U]'", self.workflow)

    def test_workflow_installs_and_launches_apk_in_headless_emulator(self) -> None:
        self.assertIn("uses: reactivecircus/android-emulator-runner@v2", self.workflow)
        self.assertIn("Stop Gradle daemons before emulator startup", self.workflow)
        self.assertLess(
            self.workflow.index("Stop Gradle daemons before emulator startup"),
            self.workflow.index("Install and launch APK in a headless Android emulator"),
        )
        self.assertIn("profile: pixel_2", self.workflow)
        self.assertIn("cores: 2", self.workflow)
        self.assertIn("ram-size: 1536M", self.workflow)
        self.assertIn("heap-size: 512M", self.workflow)
        self.assertIn("disable-linux-hw-accel: false", self.workflow)
        self.assertIn("pre-emulator-launch-script: sh .github/scripts/prepare_emulator_kvm.sh", self.workflow)
        self.assertIn("sudo chmod 666 /dev/kvm", self.kvm_script)
        self.assertIn("test -r /dev/kvm", self.kvm_script)
        self.assertIn("test -w /dev/kvm", self.kvm_script)
        self.assertIn('script: sh .github/scripts/emulator_smoke_test.sh "$APK_PATH"', self.workflow)
        self.assertNotIn("script: |", self.workflow[self.workflow.index("Install and launch APK in a headless Android emulator"):self.workflow.index("Upload debug APK")])
        self.assertIn('timeout -k 3s 180s adb install -r "$APK_PATH"', self.smoke_script)
        self.assertIn("android.permission.READ_EXTERNAL_STORAGE", self.smoke_script)
        self.assertIn("android.permission.WRITE_EXTERNAL_STORAGE", self.smoke_script)
        self.assertIn("com.mf.mylauncher.debug/net.kdt.pojavlaunch.TestStorageActivity", self.smoke_script)
        self.assertLess(
            self.smoke_script.index("android.permission.WRITE_EXTERNAL_STORAGE"),
            self.smoke_script.index("net.kdt.pojavlaunch.TestStorageActivity"),
        )
        self.assertIn('test -s "$SCREENSHOT_PATH"', self.smoke_script)
        self.assertIn("adb exec-out screencap -p", self.smoke_script)
        self.assertIn('while [ "$attempt" -le 3 ]', self.smoke_script)
        self.assertIn('if [ "$SCREENSHOT_CAPTURED" != true ]; then', self.smoke_script)
        self.assertIn("Unable to capture a non-empty emulator screenshot after 3 attempts.", self.smoke_script)
        self.assertIn("upstream/mylauncher-emulator-screenshot.png", self.workflow)
        self.assertIn('if timeout -k 3s 20s adb shell am start -n com.mf.mylauncher.debug/net.kdt.pojavlaunch.TestStorageActivity; then', self.smoke_script)
        self.assertIn('[ "$start_status" -eq 124 ]', self.smoke_script)
        self.assertIn("timed out after dispatch", self.smoke_script)
        self.assertIn('exit "$start_status"', self.smoke_script)
        self.assertIn("(mResumedActivity|topResumedActivity)", self.smoke_script)
        self.assertIn(r"net\.kdt\.pojavlaunch\.LauncherActivity", self.smoke_script)
        self.assertNotIn(r"net\.kd\.pojavlaunch\.LauncherActivity", self.smoke_script)
        self.assertIn('grep -Eq "$ACTIVITY_PATTERN"', self.smoke_script)
        self.assertIn("timeout -k 3s 5s adb shell dumpsys activity activities", self.smoke_script)
        self.assertIn("ACTIVITY_QUERY_ERRORS=0", self.smoke_script)
        self.assertIn('[ "$ACTIVITY_QUERY_ERRORS" -ge 5 ]', self.smoke_script)
        self.assertIn("ADB activity queries failed repeatedly", self.smoke_script)
        self.assertIn("capture_diagnostic_screenshot", self.smoke_script)
        self.assertIn("LauncherActivity was not resumed at the end of the emulator smoke test", self.smoke_script)
        self.assertIn("Emulator smoke test passed: APK installed and LauncherActivity resumed.", self.smoke_script)
        self.assertIn("name: voidcraft-launcher-emulator-diagnostics", self.workflow)
        self.assertIn("upstream/mylauncher-emulator-logcat.txt", self.workflow)
        self.assertIn("if: ${{ always() && env.APK_PATH != '' }}", self.workflow)
        self.assertIn("if: ${{ always() && hashFiles('upstream/VOIDCRAFT_LAUNCHER_BUILD_INFO.txt') != '' }}", self.workflow)
        self.assertLess(
            self.workflow.index("Upload debug APK"),
            self.workflow.index("Upload upstream build notes"),
        )

    def test_workflow_checks_source_pin_and_android_9_apk(self) -> None:
        self.assertIn("  push:\n    branches:\n      - main", self.workflow)
        self.assertIn("  pull_request:", self.workflow)
        self.assertIn("  workflow_dispatch:", self.workflow)
        self.assertIn("group: voidcraft-launcher-build-v2-${{ github.ref }}", self.workflow)
        self.assertIn("EXPECTED_UPSTREAM_COMMIT=330c6eae3164df64bdc4828e946a9e62cc5169e4", self.workflow)
        # Keep the hosted build toolchain aligned with the pinned upstream project.
        self.assertIn("java-version: '21'", self.workflow)
        self.assertIn("uses: android-actions/setup-android@v4", self.workflow)
        self.assertIn("accept-android-sdk-licenses: false", self.workflow)
        self.assertIn("packages: ''", self.workflow)
        self.assertIn("yes | sdkmanager --licenses", self.workflow)
        self.assertIn("sdkmanager --install 'platforms;android-37.0' 'ndk;27.3.13750724' --channel=3", self.workflow)
        self.assertIn("./gradlew --no-daemon :jre_lwjgl3glfw:build :app_pojavlauncher:assembleDebug --stacktrace", self.workflow)
        self.assertIn('plugin_management = settings.split("dependencyResolutionManagement", 1)[0]', self.workflow)
        self.assertIn("Maven Central must be configured in pluginManagement for AGP classpath dependencies", self.workflow)
        py_compile_lines = [
            line.strip() for line in self.workflow.splitlines()
            if line.strip().startswith("python3 -m py_compile ")
        ]
        self.assertEqual(len(py_compile_lines), 1)
        for required_script in (
            ".github/scripts/customize_amethyst.py",
            ".github/scripts/apply_logo.py",
            ".github/scripts/patch_renderpearl_workaround.py",
            ".github/scripts/test_customizer.py",
            ".github/scripts/test_renderpearl_workaround.py",
        ):
            self.assertIn(required_script, py_compile_lines[0])
        self.assertIn("sh -n .github/scripts/prepare_emulator_kvm.sh", self.workflow)
        self.assertIn("sh -n .github/scripts/emulator_smoke_test.sh", self.workflow)
        self.assertIn("python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v", self.workflow)
        self.assertIn("git -C upstream diff --check", self.workflow)
        self.assertIn('test "$MIN_SDK" -le 28', self.workflow)
        self.assertIn('name apksigner', self.workflow)
        self.assertIn('"$APKSIGNER" verify --verbose "$APK"', self.workflow)
        self.assertIn('ET.register_namespace("android", ANDROID[1:-1])', self.apply_logo_script)
        self.assertIn('ET.register_namespace("tools", TOOLS[1:-1])', self.apply_logo_script)
        self.assertIn("voidcraft-launcher-debug-apk", self.workflow)
        self.assertIn('sha256sum "$(basename "$APK")"', self.workflow)
        self.assertIn("${{ env.APK_SHA256_PATH }}", self.workflow)
        self.assertIn("APK SHA-256:", self.workflow)


if __name__ == "__main__":
    unittest.main()