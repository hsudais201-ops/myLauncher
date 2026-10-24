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
        (root / "settings.gradle").write_text("rootProject.name='Amethyst'\n", encoding="utf-8")

        tools_java = app / "src/main/java/net/kdt/pojavlaunch/Tools.java"
        tools_java.parent.mkdir(parents=True, exist_ok=True)
        tools_java.write_text(
            'public final class Tools { public static String APP_NAME = "Amethyst"; public static String DIR_GAME_HOME = Environment.getExternalStorageDirectory().getAbsolutePath() + "/games/Amethyst"; }\n',
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
        android:id="@+id/custom_control_button"
        android:layout_width="match_parent"
        android:text="@string/mcl_option_customcontrol"
        app:layout_constraintTop_toBottomOf="@id/news_button" />
    <com.kdt.mcgui.LauncherMenuButton
        android:id="@+id/install_jar_button"
        android:layout_width="match_parent"
        app:layout_constraintTop_toBottomOf="@id/custom_control_button" />
    <com.kdt.mcgui.LauncherMenuButton
        android:id="@+id/share_logs_button"
        android:layout_width="match_parent"
        app:layout_constraintTop_toBottomOf="@id/install_jar_button" />
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
    void importCurseforgeModpack(File modpackFile) throws IOException {
                ModLoader loaderInfo = new CommonApi(getString(R.string.curseforge_api_key)).importModpack(modpackFile);
    }
}
""",
            encoding="utf-8",
        )

        main_menu = app / "src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java"
        main_menu.parent.mkdir(parents=True, exist_ok=True)
        main_menu.write_text(
            """import android.content.Intent;
import android.widget.Button;
import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

class MainMenuFragment {
    void onViewCreated(View view) {
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
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

        api_dir = app / "src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api"
        api_dir.mkdir(parents=True, exist_ok=True)
        (api_dir / "CommonApi.java").write_text(
            """class CommonApi {
    private final ModpackApi mCurseforgeApi;
    private final ModpackApi mModrinthApi;
    private final ModpackApi[] mModpackApis;
    public CommonApi(String curseforgeApiKey) {
        mCurseforgeApi = new CurseforgeApi(curseforgeApiKey);
        mModrinthApi = new ModrinthApi();
        mModpackApis = new ModpackApi[]{mModrinthApi, mCurseforgeApi};
    }
    private ModpackApi getModpackApi(File modpackFile) {
        boolean isCurseforge = true;
            if (isCurseforge) return mCurseforgeApi;
        return mModrinthApi;
    }
}
""",
            encoding="utf-8",
        )
        (api_dir / "ModpackApi.java").write_text(
            """import android.content.Context;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import com.kdt.mcgui.ProgressLayout;
interface ModpackApi {
    default void handleInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                ModLoader loaderInfo = installMod(modDetail, selectedVersion);
                if (loaderInfo == null) return;
                loaderInfo.getDownloadTask(new NotificationDownloadListener(context, loaderInfo)).run();
            }catch (IOException e) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            }
        });
    }
}
""",
            encoding="utf-8",
        )
        (api_dir / "ModrinthApi.java").write_text(
            """class ModrinthApi {
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException{
        //TODO considering only modpacks for now
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installMrpack);
    }
}
""",
            encoding="utf-8",
        )
        (api_dir / "CurseforgeApi.java").write_text(
            """class CurseforgeApi {
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException{
        //TODO considering only modpacks for now
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installCurseforgeZip);
    }
}
""",
            encoding="utf-8",
        )
        modpacks_dir = app / "src/main/java/net/kdt/pojavlaunch/modloaders/modpacks"
        modpacks_dir.mkdir(parents=True, exist_ok=True)
        (modpacks_dir / "ModItemAdapter.java").write_text(
            """class ModItemAdapter {
        private void setDetailedStateDefault() {
            setInstallEnabled(false);
            mExtendedSpinner.setAdapter(mLoadingAdapter);
        }
}
""",
            encoding="utf-8",
        )

        search_java = app / "src/main/java/net/kdt/pojavlaunch/fragments/SearchModFragment.java"
        search_java.parent.mkdir(parents=True, exist_ok=True)
        search_java.write_text(
            """package net.kdt.pojavlaunch.fragments;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
class SearchModFragment extends Fragment {
    public static final String TAG = "SearchModFragment";
    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private androidx.recyclerview.widget.RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private ColorStateList mDefaultTextColor;
    private ModpackApi modpackApi;
    private final SearchFilters mSearchFilters = new SearchFilters();
    public SearchModFragment() { mSearchFilters.isModpack = true; }
    public void onAttach(@NonNull Context context) {
        modpackApi = new CommonApi(context.getString(R.string.curseforge_api_key));
    }
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mFilterButton = view.findViewById(R.id.search_mod_filter);
        mRecyclerview.setAdapter(mModItemAdapter);
        mFilterButton.setOnClickListener(v -> displayFilterDialog());
        searchMods(null);
    }
    public void onSearchError(int error) {
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setText(R.string.search_modpack_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setText(R.string.search_modpack_no_result);
                break;
        }
    }
    private void searchMods(String name) {
        mSearchFilters.name = name == null ? "" : name;
    }
    private void displayFilterDialog() {
    }
}
""",
            encoding="utf-8",
        )
        search_layout = app / "src/main/res/layout/fragment_mod_search.xml"
        search_layout.parent.mkdir(parents=True, exist_ok=True)
        search_layout.write_text(
            """<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools">
    <androidx.constraintlayout.widget.ConstraintLayout
        android:id="@+id/search_mod_overlay">
        <EditText
            android:id="@+id/search_mod_edittext"
            app:layout_constraintEnd_toStartOf="@id/search_mod_filter" />
        <ImageButton android:id="@+id/search_mod_filter" />
        <ProgressBar
            android:id="@+id/search_mod_progressbar"
            app:layout_constraintTop_toBottomOf="@+id/search_mod_edittext"
            tools:layout_editor_absoluteX="13dp" />
    </androidx.constraintlayout.widget.ConstraintLayout>
</androidx.constraintlayout.widget.ConstraintLayout>
""",
            encoding="utf-8",
        )

        downloader_java = app / "src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java"
        downloader_java.parent.mkdir(parents=True, exist_ok=True)
        downloader_java.write_text(
            """class MinecraftDownloader {
    private boolean isLocalProfile;
    private boolean isOnline;
    public void start(@Nullable Activity activity, @Nullable JMinecraftVersionList.Version version,
                      @NonNull String realVersion,
                      @NonNull AsyncMinecraftDownloader.DoneListener listener) {
        if(isLocalProfile) return;
    }
    private void downloadGame(Activity activity, JMinecraftVersionList.Version version, String realVersion) throws Exception {
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

        (app / "src/main/res/values/headings_array.xml").write_text(
            """<resources>
    <string-array name="menu_ingame">
        <item>@string/control_forceclose</item>
        <item>@string/control_viewout</item>
        <item>@string/control_customkey</item>
        <item>@string/quick_setting_title</item>
        <item>@string/mcl_option_customcontrol</item>
    </string-array>
</resources>
""",
            encoding="utf-8",
        )
        pref_control = app / "src/main/res/xml/pref_control.xml"
        pref_control.parent.mkdir(parents=True, exist_ok=True)
        pref_control.write_text(
            '<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android" '
            'xmlns:app2="http://schemas.android.com/apk/res-auto"></PreferenceScreen>\n',
            encoding="utf-8",
        )

        main_activity = app / "src/main/java/net/kdt/pojavlaunch/MainActivity.java"
        main_activity.write_text(
            """package net.kdt.pojavlaunch;
import android.widget.FrameLayout;
class MainActivity {
    private FrameLayout contentFrame;
    private ControlLayout mControlLayout;
    private GyroControl mGyroControl;
    private void bindMenu() {
        switch(position) {
            case 4: openCustomControls(); break;
        }
    }
    private void loadControls() {
        mControlLayout.toggleControlVisible();
    }
    public void onAttachedToWindow() {
        mControlLayout.post(() -> {
            Tools.getDisplayMetrics(this);
            loadControls();
        });
    }
    public void onResume() {
        super.onResume();
    }
    protected void onPause() {
        mGyroControl.disable();
    }
    private void openCustomControls() {
    }
}
""",
            encoding="utf-8",
        )

        jre_utils = app / "src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java"
        jre_utils.parent.mkdir(parents=True, exist_ok=True)
        jre_utils.write_text(
            """package net.kdt.pojavlaunch.utils;
class JREUtils {
    public static native int[] renderAWTScreenFrame(/* Object canvas, int width, int height */);
}
""",
            encoding="utf-8",
        )

        egl_bridge = app / "src/main/jni/egl_bridge.c"
        egl_bridge.parent.mkdir(parents=True, exist_ok=True)
        egl_bridge.write_text(
            """#include <jni.h>
#include <stdint.h>
#define EXTERNAL_API __attribute__((used))
#define ABI_COMPAT __attribute__((unused))
void br_swap_buffers(void) {}
EXTERNAL_API void pojavSwapBuffers() {
    br_swap_buffers();
}
""",
            encoding="utf-8",
        )

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
            self.assertIn('"myLauncher (Debug)"', build)
            self.assertIn('"app_name", "myLauncher"', build)
            self.assertIn("'application_package', 'com.mf.mylauncher.debug'", build)
            self.assertIn("'application_package', 'com.mf.mylauncher'", build)
            self.assertIn("'shareProviderAuthority', 'com.mf.mylauncher.scoped.controlfolder.debug'", build)
            self.assertNotIn("org.angelauramc.amethyst", build)
            self.assertNotIn("Amethyst", strings)
            self.assertIn("myLauncher crashed", strings)
            self.assertIn('<string name="mylauncher_graphics_renderer">Graphics &amp; Renderer</string>', strings)
            self.assertIn('<string name="mylauncher_download_versions">Download Minecraft Versions</string>', strings)
            self.assertIn('<string name="mylauncher_version_download_title">Minecraft Version Downloads</string>', strings)
            self.assertIn('<string name="mylauncher_version_download_button">Download selected version</string>', strings)
            self.assertIn('<string name="mylauncher_customize_controls">Customize Controls</string>', strings)
            self.assertIn('<string name="mylauncher_control_settings">Controls &amp; Touch Settings</string>', strings)
            tools_java = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Tools.java").read_text(encoding="utf-8")
            self.assertIn('public static String APP_NAME = "myLauncher";', tools_java)
            self.assertNotIn('public static String APP_NAME = "Amethyst";', tools_java)
            self.assertIn('public static String DIR_GAME_HOME = null;', tools_java)
            self.assertNotIn('public static String DIR_GAME_HOME = Environment.getExternalStorageDirectory().getAbsolutePath() + "/games/Amethyst";', tools_java)
            self.assertIn("rootProject.name='myLauncher'", settings)
            self.assertIn("if (deviceRam <= 2048) return 512;", prefs)
            self.assertIn("int targetSide = Tools.getTotalDeviceMemory(context) <= 2048 ? 480 : (isDevicePowerful ? 1080 : 720);", prefs)
            self.assertNotIn("int targetSide = isDevicePowerful ? 1080 :", prefs)
            local_login = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java").read_text(encoding="utf-8")
            login_layout = (root / "app_pojavlauncher/src/main/res/layout/fragment_local_login.xml").read_text(encoding="utf-8")
            self.assertNotIn("if (!hasOnlineProfile())", local_login)
            self.assertNotIn("import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;", local_login)
            self.assertIn("ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{", local_login)
            self.assertIn('Pattern.compile("^[a-zA-Z0-9_]*$")', local_login)
            self.assertIn("text.length() < 3", local_login)
            self.assertIn("text.length() > 16", local_login)
            self.assertIn('new File(Tools.DIR_ACCOUNT_NEW + "/" + text + ".json").exists()', local_login)
            self.assertIn('android:inputType="textPersonName"', login_layout)
            self.assertIn('android:text="@string/main_add_account"', login_layout)
            self.assertNotIn('android:text="@string/login_online_login_label"', login_layout)
            main_menu = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/MainMenuFragment.java").read_text(encoding="utf-8")
            self.assertIn("Button mCustomControlButton = view.findViewById(R.id.custom_control_button);", main_menu)
            self.assertIn("CustomControlsActivity.class", main_menu)
            self.assertIn("mCustomControlButton.setOnClickListener", main_menu)
            self.assertIn("Button mMyLauncherControlSettingsButton = view.findViewById(R.id.mylauncher_control_settings_button);", main_menu)
            self.assertIn("mMyLauncherControlSettingsButton.setOnClickListener", main_menu)
            self.assertIn("LauncherPreferenceControlFragment.class", main_menu)
            self.assertIn("LauncherActivity.SETTING_FRAGMENT_TAG", main_menu)
            self.assertIn("import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceControlFragment;", main_menu)
            main_activity = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MainActivity.java").read_text(encoding="utf-8")
            self.assertIn("private PerformanceOverlayView mPerformanceOverlayView;", main_activity)
            self.assertIn("case 5: togglePerformanceOverlay(); break;", main_activity)
            self.assertIn("mPerformanceOverlayView.attachTo(mControlLayout);", main_activity)
            self.assertIn("mPerformanceOverlayView.bringToFront();", main_activity)
            self.assertIn("mPerformanceOverlayView.startUpdates();", main_activity)
            self.assertIn("mPerformanceOverlayView.stopUpdates();", main_activity)
            overlay = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/PerformanceOverlayView.java").read_text(encoding="utf-8")
            self.assertIn("JREUtils.nativeGetRenderFrameCount()", overlay)
            self.assertIn("Debug.getMemoryInfo(memoryInfo)", overlay)
            self.assertIn("MotionEvent.ACTION_MOVE", overlay)
            self.assertIn("PREF_X_DP", overlay)
            self.assertIn("PREF_Y_DP", overlay)
            self.assertIn("mylauncher_performance_overlay_enabled", overlay)
            self.assertIn("mylauncher_performance_overlay_fps", overlay)
            self.assertIn("mylauncher_performance_overlay_memory", overlay)
            pref_control = (root / "app_pojavlauncher/src/main/res/xml/pref_control.xml").read_text(encoding="utf-8")
            self.assertIn('android:key="mylauncher_performance_overlay_enabled"', pref_control)
            self.assertIn('android:key="mylauncher_performance_overlay_fps"', pref_control)
            self.assertIn('android:key="mylauncher_performance_overlay_memory"', pref_control)
            arrays = (root / "app_pojavlauncher/src/main/res/values/headings_array.xml").read_text(encoding="utf-8")
            self.assertIn("<item>@string/mylauncher_toggle_performance_overlay</item>", arrays)
            jre_utils = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java").read_text(encoding="utf-8")
            self.assertIn("nativeGetRenderFrameCount", jre_utils)
            egl_bridge = (root / "app_pojavlauncher/src/main/jni/egl_bridge.c").read_text(encoding="utf-8")
            self.assertIn("Java_net_kdt_pojavlaunch_utils_JREUtils_nativeGetRenderFrameCount", egl_bridge)
            self.assertIn("__atomic_add_fetch(&mylauncher_render_frame_count, 1, __ATOMIC_RELAXED);", egl_bridge)
            self.assertIn("Button mMyLauncherAccountButton = view.findViewById(R.id.mylauncher_account_button);", main_menu)
            self.assertIn("mMyLauncherAccountButton.setOnClickListener", main_menu)
            self.assertIn("Button mMyLauncherGraphicsButton = view.findViewById(R.id.mylauncher_graphics_button);", main_menu)
            self.assertIn("mMyLauncherGraphicsButton.setOnClickListener", main_menu)
            self.assertIn("LauncherPreferenceVideoFragment.class", main_menu)
            self.assertIn("LauncherActivity.SETTING_FRAGMENT_TAG", main_menu)
            self.assertIn("import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;", main_menu)
            self.assertIn("import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;", main_menu)
            self.assertIn("import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;", main_menu)
            self.assertIn("Button mMyLauncherInstancesButton = view.findViewById(R.id.mylauncher_instances_button);", main_menu)
            self.assertIn("mMyLauncherInstancesButton.setOnClickListener", main_menu)
            self.assertIn("ProfileTypeSelectFragment.class", main_menu)
            self.assertIn("Button mMyLauncherRuntimeButton = view.findViewById(R.id.mylauncher_java_runtime_button);", main_menu)
            self.assertIn("mMyLauncherRuntimeButton.setOnClickListener", main_menu)
            self.assertIn("LauncherPreferenceJavaFragment.class", main_menu)
            self.assertIn("Button mMyLauncherSettingsButton = view.findViewById(R.id.mylauncher_launcher_settings_button);", main_menu)
            self.assertIn("mMyLauncherSettingsButton.setOnClickListener", main_menu)
            self.assertIn("LauncherPreferenceFragment.class", main_menu)
            self.assertIn("VersionDownloadFragment.class", main_menu)
            self.assertIn("Button mMyLauncherVersionsButton = view.findViewById(R.id.mylauncher_download_versions_button);", main_menu)
            self.assertIn("import net.kdt.pojavlaunch.fragments.VersionDownloadFragment;", main_menu)
            self.assertIn("mMyLauncherVersionsButton.setOnClickListener", main_menu)
            self.assertIn("ProfileTypeSelectFragment.class", main_menu)
            self.assertIn("Button mMyLauncherModsButton = view.findViewById(R.id.mylauncher_mods_button);", main_menu)
            self.assertIn("mMyLauncherModsButton.setOnClickListener", main_menu)
            self.assertIn("SearchModFragment.ARG_IS_MODPACK, false", main_menu)
            self.assertIn("Button mMyLauncherFeatureStoreButton = view.findViewById(R.id.mylauncher_feature_store_button);", main_menu)
            self.assertIn("mMyLauncherFeatureStoreButton.setOnClickListener", main_menu)
            self.assertIn("FeatureStoreFragment.class", main_menu)
            self.assertIn("Button mMyLauncherBrowseModrinthButton = view.findViewById(R.id.mylauncher_browse_modrinth_button);", main_menu)
            self.assertIn('SearchModFragment.ARG_PROVIDER, "modrinth"', main_menu)
            self.assertIn("Button mMyLauncherBrowseCurseForgeButton = view.findViewById(R.id.mylauncher_browse_curseforge_button);", main_menu)
            self.assertIn('SearchModFragment.ARG_PROVIDER, "curseforge"', main_menu)
            self.assertIn("SearchModFragment.class", main_menu)
            self.assertIn("ExtraConstants.SELECT_AUTH_METHOD", main_menu)
            self.assertIn("Tools.isDemoProfile(v.getContext())", main_menu)
            self.assertIn("else openPath(v.getContext(), getCurrentProfileDirectory(), false);", main_menu)
            self.assertNotIn("else if (!hasOnlineProfile())", main_menu)
            activity = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/LauncherActivity.java").read_text(encoding="utf-8")
            self.assertIn("ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);", activity)
            self.assertIn("Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);", activity)
            manifest = (root / "app_pojavlauncher/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
            icon = (root / "app_pojavlauncher/src/main/res/drawable/mylauncher_icon.xml").read_text(encoding="utf-8")
            self.assertIn('android:icon="@drawable/mylauncher_icon"', manifest)
            self.assertIn('android:roundIcon="@drawable/mylauncher_icon"', manifest)
            self.assertIn('android:label="@string/app_name"', manifest)
            self.assertNotIn("org.angelauramc.amethyst", manifest)
            self.assertIn('android:fillColor="#7C5CFF"', icon)
            for relative_layout in (
                "src/main/res/layout/fragment_launcher.xml",
                "src/main/res/layout-land/fragment_launcher.xml",
            ):
                layout_text = (root / "app_pojavlauncher" / relative_layout).read_text(encoding="utf-8")
                self.assertIn('android:id="@+id/mylauncher_graphics_button"', layout_text)
                self.assertIn('android:text="@string/mylauncher_graphics_renderer"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_account_button"', layout_text)
                self.assertIn('android:id="@+id/mylauncher_instances_button"', layout_text)
                self.assertIn('android:text="@string/mylauncher_instances"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_graphics_button"', layout_text)
                self.assertIn('android:id="@+id/mylauncher_java_runtime_button"', layout_text)
                self.assertIn('android:text="@string/mylauncher_java_runtime"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_instances_button"', layout_text)
                self.assertIn('android:id="@+id/mylauncher_launcher_settings_button"', layout_text)
                self.assertIn('android:text="@string/mylauncher_launcher_settings"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_java_runtime_button"', layout_text)
                self.assertIn('android:id="@+id/mylauncher_download_versions_button"', layout_text)
                self.assertIn('android:text="@string/mylauncher_download_versions"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_launcher_settings_button"', layout_text)
                self.assertIn('android:id="@+id/mylauncher_mods_button"', layout_text)
                self.assertIn('android:text="@string/mylauncher_modrinth_curseforge"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_download_versions_button"', layout_text)
                self.assertIn('android:id="@+id/mylauncher_feature_store_button"', layout_text)
                self.assertIn('android:text="@string/mylauncher_feature_store"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_mods_button"', layout_text)
                self.assertIn('android:id="@+id/mylauncher_browse_modrinth_button"', layout_text)
                self.assertIn('android:text="@string/mylauncher_browse_modrinth"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_feature_store_button"', layout_text)
                self.assertIn('android:id="@+id/mylauncher_browse_curseforge_button"', layout_text)
                self.assertIn('android:text="@string/mylauncher_browse_curseforge"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_browse_modrinth_button"', layout_text)
                self.assertIn('android:id="@+id/custom_control_button"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/news_button"', layout_text)
                self.assertIn('android:text="@string/mylauncher_customize_controls"', layout_text)
                self.assertIn('android:id="@+id/mylauncher_control_settings_button"', layout_text)
                self.assertIn('android:drawableStart="@drawable/ic_setting_control_scale"', layout_text)
                self.assertIn('android:text="@string/mylauncher_control_settings"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/custom_control_button"', layout_text)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/mylauncher_control_settings_button"', layout_text)
            # All transformed resources must remain well-formed XML.
            for xml_path in (
                root / "app_pojavlauncher/src/main/res/layout/fragment_launcher.xml",
                root / "app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml",
                root / "app_pojavlauncher/src/main/res/layout/fragment_mod_search.xml",
                root / "app_pojavlauncher/src/main/res/layout/fragment_version_download.xml",
                root / "app_pojavlauncher/src/main/res/layout/fragment_local_login.xml",
                root / "app_pojavlauncher/src/main/AndroidManifest.xml",
                root / "app_pojavlauncher/src/main/res/drawable/mylauncher_icon.xml",
                root / "app_pojavlauncher/src/main/res/values/strings.xml",
                root / "app_pojavlauncher/src/main/res/values/colors.xml",
                root / "app_pojavlauncher/src/main/res/values/headings_array.xml",
                root / "app_pojavlauncher/src/main/res/xml/pref_control.xml",
            ):
                with self.subTest(xml=str(xml_path.relative_to(root))):
                    ET.parse(xml_path)
            strings = (root / "app_pojavlauncher/src/main/res/values/strings.xml").read_text(encoding="utf-8")
            self.assertIn('<string name="mylauncher_accounts">Accounts: Microsoft / Offline</string>', strings)
            self.assertIn('<string name="mylauncher_launcher_settings">Launcher Settings</string>', strings)
            search_mod = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/SearchModFragment.java").read_text(encoding="utf-8")
            self.assertIn("ARG_IS_MODPACK", search_mod)
            self.assertIn("ARG_PROVIDER", search_mod)
            self.assertIn('mProvider = searchArguments.getString(ARG_PROVIDER, "all")', search_mod)
            self.assertIn("mSearchFilters.isModpack = !mSearchFilters.isModpack;", search_mod)
            self.assertIn("showCurseForgeKeyDialog()", search_mod)
            self.assertIn('"curseforge".equalsIgnoreCase(mProvider)', search_mod)
            self.assertIn('putString("curseforge_api_key", key)', search_mod)
            self.assertIn("new CommonApi(savedKey, mProvider)", search_mod)
            feature_store = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/FeatureStoreFragment.java").read_text(encoding="utf-8")
            self.assertIn('public static final String TAG = "FeatureStoreFragment";', feature_store)
            self.assertIn('private static final String ITEMS = "user_added_features";', feature_store)
            self.assertIn('getString(ITEMS, "[]")', feature_store)
            self.assertIn('item.put("name", featureName)', feature_store)
            self.assertIn('item.put("url", featureUrl)', feature_store)
            self.assertIn("Tools.openURL(requireActivity(), url)", feature_store)
            self.assertIn("saveItems(remaining)", feature_store)
            self.assertIn('("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))', feature_store)
            self.assertIn('<string name="mylauncher_feature_store">Feature Store</string>', strings)
            self.assertIn('<string name="mylauncher_browse_modrinth">Browse Modrinth</string>', strings)
            self.assertIn('<string name="mylauncher_browse_curseforge">Browse CurseForge</string>', strings)
            self.assertIn('<string name="mylauncher_curseforge_key_message">', strings)
            common_api = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/CommonApi.java").read_text(encoding="utf-8")
            self.assertIn('"DUMMY".equalsIgnoreCase(key)', common_api)
            self.assertIn('this(curseforgeApiKey, "all");', common_api)
            self.assertIn('"modrinth".equalsIgnoreCase(provider)', common_api)
            self.assertIn('"curseforge".equalsIgnoreCase(provider)', common_api)
            self.assertIn("CurseForge modpack import requires an approved API key.", common_api)
            modpack_api = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/ModpackApi.java").read_text(encoding="utf-8")
            self.assertIn("import android.widget.Toast;", modpack_api)
            self.assertIn("!modDetail.isModpack", modpack_api)
            self.assertIn("R.string.mylauncher_mod_install_success", modpack_api)
            self.assertIn("Toast.makeText(context", modpack_api)
            for relative_api in ("ModrinthApi.java", "CurseforgeApi.java"):
                api_text = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api" / relative_api).read_text(encoding="utf-8")
                self.assertIn("ModFileInstaller.install(modDetail, selectedVersion)", api_text)
            installer = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/api/ModFileInstaller.java").read_text(encoding="utf-8")
            self.assertIn('new File(gameDirectory, "mods")', installer)
            self.assertIn('DownloadUtils.ensureSha1(temporary, sha1', installer)
            self.assertIn('"https".equalsIgnoreCase(uri.getScheme())', installer)
            self.assertIn('endsWith(".forgecdn.net")', installer)
            self.assertIn('endsWith(".cursecdn.com")', installer)
            self.assertIn('endsWith(".jar")', installer)
            mod_adapter = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/modloaders/modpacks/ModItemAdapter.java").read_text(encoding="utf-8")
            self.assertIn("mExtendedButton.setText(mModItem.isModpack", mod_adapter)
            search_layout = root / "app_pojavlauncher/src/main/res/layout/fragment_mod_search.xml"
            ET.parse(search_layout)
            search_layout_text = search_layout.read_text(encoding="utf-8")
            self.assertIn('android:id="@+id/search_mod_type_toggle"', search_layout_text)
            self.assertIn('android:id="@+id/search_mod_curseforge_key"', search_layout_text)
            version_fragment = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/VersionDownloadFragment.java").read_text(encoding="utf-8")
            self.assertIn("VersionSelectorDialog.open", version_fragment)
            self.assertIn("AsyncMinecraftDownloader.getListedVersion(normalizedVersion)", version_fragment)
            self.assertIn("new MinecraftDownloader().downloadVersionFiles(requireActivity(), listedVersion, normalizedVersion", version_fragment)
            downloader = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/tasks/MinecraftDownloader.java").read_text(encoding="utf-8")
            self.assertIn("public void downloadVersionFiles(", downloader)
            self.assertIn("downloadGame(activity, version, realVersion);", downloader)
            self.assertIn("listener.onDownloadFailed(e);", downloader)
            self.assertIn("R.string.mylauncher_version_download_in_progress", version_fragment)
            self.assertIn("R.string.mylauncher_version_download_done", version_fragment)
            self.assertIn("new AsyncMinecraftDownloader.DoneListener()", version_fragment)
            self.assertIn("ensureVersionListAvailable()", version_fragment)
            self.assertIn("new AsyncVersionList().getVersionList", version_fragment)
            self.assertIn("ExtraConstants.RELEASE_TABLE", version_fragment)
            self.assertIn("R.string.mylauncher_version_list_loading", version_fragment)
            self.assertIn("R.string.mylauncher_version_list_failed", version_fragment)
            self.assertIn('<string name="mylauncher_version_list_loading">', strings)
            self.assertIn('<string name="mylauncher_version_list_failed">', strings)
            self.assertIn('getSharedPreferences("mylauncher_mod_sources", MODE_PRIVATE)', activity)
            self.assertIn("new CommonApi(curseForgeApiKey).importModpack(modpackFile)", activity)
            version_layout = root / "app_pojavlauncher/src/main/res/layout/fragment_version_download.xml"
            ET.parse(version_layout)
            version_layout_text = version_layout.read_text(encoding="utf-8")
            self.assertIn('android:id="@+id/mylauncher_select_version_button"', version_layout_text)
            self.assertIn('android:id="@+id/mylauncher_download_version_button"', version_layout_text)
            auth_selector = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/SelectAuthFragment.java").read_text(encoding="utf-8")
            self.assertNotIn("hasNoOnlineProfileDialog", auth_selector)
            self.assertIn("mLocalButton.setOnClickListener(v -> Tools.swapFragment", auth_selector)
            self.assertIn("mMicrosoftButton.setOnClickListener(v -> Tools.swapFragment", auth_selector)
            for relative in ("src/main/res/layout/fragment_launcher.xml", "src/main/res/layout-land/fragment_launcher.xml"):
                launcher_layout = (root / "app_pojavlauncher" / relative).read_text(encoding="utf-8")
                self.assertFalse(
                    any(line.endswith((" ", "\t")) for line in launcher_layout.splitlines()),
                    "generated launcher layout must not contain trailing whitespace",
                )
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_hero_title"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_hero_subtitle"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_account_button"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_control_settings_button"'), 1)
                self.assertIn('android:text="@string/mylauncher_customize_controls"', launcher_layout)
                self.assertIn('android:text="myLauncher"', launcher_layout)
                self.assertIn('android:text="Minecraft Java Edition · built for lightweight devices"', launcher_layout)
                self.assertIn('android:text="@string/mylauncher_accounts"', launcher_layout)
                self.assertIn('android:drawableStart="@android:drawable/ic_menu_myplaces"', launcher_layout)
                self.assertIn('app:layout_constraintTop_toBottomOf="@id/open_files_button"', launcher_layout)
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

    def test_rejects_offline_login_placeholder_username(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.fixture(root)
            local_login = root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/fragments/LocalLoginFragment.java"
            login_text = local_login.read_text(encoding="utf-8")
            good_submission = 'new String[]{ mUsernameEditText.getText().toString(), "" }'
            self.assertIn(good_submission, login_text)
            local_login.write_text(
                login_text.replace(good_submission, 'new String[]{ "name", "" }', 1),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "does not submit the validated username"):
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


class BuildWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        workflow_path = Path(__file__).parents[1] / "workflows" / "build.yml"
        cls.workflow = workflow_path.read_text(encoding="utf-8")

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

    def test_workflow_checks_source_pin_and_android_9_apk(self) -> None:
        self.assertIn("  push:\n    branches:\n      - main", self.workflow)
        self.assertIn("  pull_request:", self.workflow)
        self.assertIn("  workflow_dispatch:", self.workflow)
        self.assertIn("EXPECTED_UPSTREAM_COMMIT=330c6eae3164df64bdc4828e946a9e62cc5169e4", self.workflow)
        # Keep the hosted build toolchain aligned with the pinned upstream project.
        self.assertIn("java-version: '21'", self.workflow)
        self.assertIn("sdkmanager --install 'platforms;android-37.0' 'ndk;27.3.13750724' --channel=3", self.workflow)
        self.assertIn("./gradlew --no-daemon :jre_lwjgl3glfw:build :app_pojavlauncher:assembleDebug --stacktrace", self.workflow)
        self.assertIn("python3 -m py_compile .github/scripts/customize_amethyst.py .github/scripts/test_customizer.py", self.workflow)
        self.assertIn("python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v", self.workflow)
        self.assertIn("git -C upstream diff --check", self.workflow)
        self.assertIn('test "$MIN_SDK" -le 28', self.workflow)
        self.assertIn('name apksigner', self.workflow)
        self.assertIn('"$APKSIGNER" verify --verbose "$APK"', self.workflow)
        self.assertIn("myLauncher-debug-apk", self.workflow)
        self.assertIn('sha256sum "$(basename "$APK")"', self.workflow)
        self.assertIn("${{ env.APK_SHA256_PATH }}", self.workflow)
        self.assertIn("APK SHA-256:", self.workflow)
        self.assertIn('bash .github/scripts/emulator_smoke_test.sh "$APK_PATH"', self.workflow)
        smoke_test = (Path(__file__).parent / "emulator_smoke_test.sh").read_text(encoding="utf-8")
        self.assertIn("adb shell pm grant com.mf.mylauncher.debug android.permission.POST_NOTIFICATIONS", smoke_test)
        self.assertIn("adb shell dumpsys activity activities > upstream/mylauncher-activities.txt", smoke_test)
        self.assertIn("adb shell dumpsys window windows > upstream/mylauncher-windows.txt", smoke_test)
        self.assertIn("ResumedActivity:.*com[.]mf[.]mylauncher[.]debug/net[.]kdt[.]pojavlaunch[.]LauncherActivity", smoke_test)
        self.assertIn("! grep -Fq 'Splash Screen com.mf.mylauncher.debug' upstream/mylauncher-windows.txt", smoke_test)
        self.assertIn("adb shell run-as com.mf.mylauncher.debug kill -3", smoke_test)
        self.assertNotIn("mCurrentFocus=.*com[.]mf[.]mylauncher[.]debug/net[.]kdt[.]pojavlaunch[.]LauncherActivity", self.workflow)


if __name__ == "__main__":
    unittest.main()