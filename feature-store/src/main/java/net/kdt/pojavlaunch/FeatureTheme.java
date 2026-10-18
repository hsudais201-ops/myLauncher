package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

/** Applies user-selected, data-only Feature Store theme presets to launcher screens. */
public final class FeatureTheme {
    private static final String PREFS = "voidcraft_feature_store";
    private static final String KEY_BACKGROUND = "theme_background";
    private static final String KEY_ACCENT = "theme_accent";
    private static final String KEY_TEXT = "theme_text";

    private FeatureTheme() { }

    public static void applyPreset(Context context, String background, String accent, String text) {
        if (!isHexColor(background) || !isHexColor(accent) || !isHexColor(text)) {
            throw new IllegalArgumentException("Theme colors must be #RRGGBB values");
        }
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit()
                .putString(KEY_BACKGROUND, background)
                .putString(KEY_ACCENT, accent)
                .putString(KEY_TEXT, text)
                .apply();
    }

    public static void apply(Activity activity) {
        if (activity == null || activity instanceof MainActivity
                || activity instanceof JavaGUILauncherActivity) {
            return; // Never theme the in-game surface or the Java GUI installer.
        }
        SharedPreferences preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String backgroundValue = preferences.getString(KEY_BACKGROUND, "#171526");
        String accentValue = preferences.getString(KEY_ACCENT, "#8158E9");
        String textValue = preferences.getString(KEY_TEXT, "#F4F0FF");
        if (!isHexColor(backgroundValue) || !isHexColor(accentValue) || !isHexColor(textValue)) {
            return;
        }

        int background = Color.parseColor(backgroundValue);
        int accent = Color.parseColor(accentValue);
        int text = Color.parseColor(textValue);
        Window window = activity.getWindow();
        if (window != null) {
            window.setStatusBarColor(background);
            window.setNavigationBarColor(background);
        }

        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            if (content instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) content;
                for (int i = 0; i < group.getChildCount(); i++) {
                    applyToViewTree(group.getChildAt(i), background, accent, text);
                }
            }
        }
    }

    private static void applyToViewTree(View view, int background, int accent, int text) {
        if (view.getId() == R.id.fragment_menu_main) {
            view.setBackgroundColor(background);
        }
        if (view instanceof Button) {
            view.setBackgroundTintList(ColorStateList.valueOf(accent));
        }
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(text);
        }
        if (view instanceof ViewGroup) {
            ViewGroup children = (ViewGroup) view;
            for (int i = 0; i < children.getChildCount(); i++) {
                applyToViewTree(children.getChildAt(i), background, accent, text);
            }
        }
    }

    private static boolean isHexColor(String value) {
        if (value == null || value.length() != 7 || value.charAt(0) != '#') return false;
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }
}
