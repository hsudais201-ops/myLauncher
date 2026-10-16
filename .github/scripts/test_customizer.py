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
            'public final class Tools { public static String APP_NAME = "Amethyst"; }\n',
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
        ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{ "name", "" });
    }

    private boolean checkEditText() {
        String text = "name";
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
            tools_java = (root / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Tools.java").read_text(encoding="utf-8")
            self.assertIn('public static String APP_NAME = "myLauncher";', tools_java)
            self.assertNotIn('public static String APP_NAME = "Amethyst";', tools_java)
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
            self.assertIn("Button mMyLauncherAccountButton = view.findViewById(R.id.mylauncher_account_button);", main_menu)
            self.assertIn("mMyLauncherAccountButton.setOnClickListener", main_menu)
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
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_hero_title"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_hero_subtitle"'), 1)
                self.assertEqual(launcher_layout.count('android:id="@+id/mylauncher_account_button"'), 1)
                self.assertIn('android:text="myLauncher"', launcher_layout)
                self.assertIn('android:text="Minecraft Java Edition · built for lightweight devices"', launcher_layout)
                self.assertIn('android:text="@string/main_add_account"', launcher_layout)
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
        self.assertIn("sdkmanager --install 'platforms;android-37' 'ndk;27.3.13750724'", self.workflow)
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


if __name__ == "__main__":
    unittest.main()