// Copyright 2025 Nova Video Player
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.archos.mediacenter.video.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.ActionBar;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.UiChoiceDialog;

/**
 * ThemeManager handles programmatic theme switching between Blue and Black themes.
 * Based on the leeroysflix fork black theme implementation.
 */
public class ThemeManager {

    private static final String TAG = "ThemeManager";
    public static final String KEY_APP_THEME = "app_theme";
    
    public static final String THEME_BLUE = "blue";
    public static final String THEME_BLACK = "black";
    // NovaPlayer Prime: extra themes. Order = index in every color array below.
    private static final String[] THEMES = {THEME_BLUE, THEME_BLACK,
            "gold", "burgundy", "emerald", "midnight", "mahogany", "purple"};
    private static final int[] LEANBACK_STYLES = {0, R.style.MyLeanbackTheme_Black,
            R.style.MyLeanbackTheme_PrimeGold, R.style.MyLeanbackTheme_PrimeBurgundy, R.style.MyLeanbackTheme_PrimeEmerald,
            R.style.MyLeanbackTheme_PrimeMidnight, R.style.MyLeanbackTheme_PrimeMahogany, R.style.MyLeanbackTheme_PrimePurple};
    private static final int[] LEANBACK_PREFERENCES_STYLES = {0, R.style.MyLeanbackTheme_Preferences_Black,
            R.style.MyLeanbackTheme_Preferences_PrimeGold, R.style.MyLeanbackTheme_Preferences_PrimeBurgundy,
            R.style.MyLeanbackTheme_Preferences_PrimeEmerald, R.style.MyLeanbackTheme_Preferences_PrimeMidnight,
            R.style.MyLeanbackTheme_Preferences_PrimeMahogany, R.style.MyLeanbackTheme_Preferences_PrimePurple};
    
    private static ThemeManager sInstance;
    private SharedPreferences mPrefs;
    
    // Theme color resource IDs - resolved at runtime via getThemeColor()
    private static final int[] GRID_ITEM_BACKGROUND = {R.color.theme_grid_item_blue, R.color.theme_grid_item_black, R.color.prime_theme_gold_grid_item, R.color.prime_theme_burgundy_grid_item, R.color.prime_theme_emerald_grid_item, R.color.prime_theme_midnight_grid_item, R.color.prime_theme_mahogany_grid_item, R.color.prime_theme_purple_grid_item /* NovaPlayer Prime */};
    private static final int[] LEANBACK_BACKGROUND = {R.color.theme_background_blue, R.color.theme_background_black, R.color.prime_theme_gold_background, R.color.prime_theme_burgundy_background, R.color.prime_theme_emerald_background, R.color.prime_theme_midnight_background, R.color.prime_theme_mahogany_background, R.color.prime_theme_purple_background /* NovaPlayer Prime */};
    private static final int[] LEANBACK_TRANSPARENT = {R.color.leanback_background_transparent, R.color.leanback_background_transparent, R.color.prime_theme_gold_transparent, R.color.prime_theme_burgundy_transparent, R.color.prime_theme_emerald_transparent, R.color.prime_theme_midnight_transparent, R.color.prime_theme_mahogany_transparent, R.color.prime_theme_purple_transparent /* NovaPlayer Prime */};
    private static final int[] LEANBACK_HEADER = {R.color.theme_header_blue, R.color.theme_header_black, R.color.prime_theme_gold_header, R.color.prime_theme_burgundy_header, R.color.prime_theme_emerald_header, R.color.prime_theme_midnight_header, R.color.prime_theme_mahogany_header, R.color.prime_theme_purple_header /* NovaPlayer Prime */};
    private static final int[] TOOLBAR_BACKGROUND = {R.color.theme_toolbar_blue, R.color.theme_toolbar_black, R.color.prime_theme_gold_toolbar, R.color.prime_theme_burgundy_toolbar, R.color.prime_theme_emerald_toolbar, R.color.prime_theme_midnight_toolbar, R.color.prime_theme_mahogany_toolbar, R.color.prime_theme_purple_toolbar /* NovaPlayer Prime */};
    private static final int[] DETAILS_PRIMARY = {R.color.theme_details_primary_blue, R.color.theme_details_primary_black, R.color.prime_theme_gold_details_primary, R.color.prime_theme_burgundy_details_primary, R.color.prime_theme_emerald_details_primary, R.color.prime_theme_midnight_details_primary, R.color.prime_theme_mahogany_details_primary, R.color.prime_theme_purple_details_primary /* NovaPlayer Prime */};
    private static final int[] DETAILS_SECONDARY = {R.color.theme_details_secondary_blue, R.color.theme_details_secondary_black, R.color.prime_theme_gold_details_secondary, R.color.prime_theme_burgundy_details_secondary, R.color.prime_theme_emerald_details_secondary, R.color.prime_theme_midnight_details_secondary, R.color.prime_theme_mahogany_details_secondary, R.color.prime_theme_purple_details_secondary /* NovaPlayer Prime */};
    private static final int[] CATEGORY_SELECTOR = {R.color.theme_category_selector_blue, R.color.theme_category_selector_black, R.color.prime_theme_gold_category_selector, R.color.prime_theme_burgundy_category_selector, R.color.prime_theme_emerald_category_selector, R.color.prime_theme_midnight_category_selector, R.color.prime_theme_mahogany_category_selector, R.color.prime_theme_purple_category_selector /* NovaPlayer Prime */};
    private static final int[] LIST_ITEM_PRESSED = {R.color.theme_list_pressed_blue, R.color.theme_list_pressed_black, R.color.prime_theme_gold_list_pressed, R.color.prime_theme_burgundy_list_pressed, R.color.prime_theme_emerald_list_pressed, R.color.prime_theme_midnight_list_pressed, R.color.prime_theme_mahogany_list_pressed, R.color.prime_theme_purple_list_pressed /* NovaPlayer Prime */};
    private static final int[] LIST_ITEM_FOCUSED = {R.color.theme_list_focused_blue, R.color.theme_list_focused_black, R.color.prime_theme_gold_list_focused, R.color.prime_theme_burgundy_list_focused, R.color.prime_theme_emerald_list_focused, R.color.prime_theme_midnight_list_focused, R.color.prime_theme_mahogany_list_focused, R.color.prime_theme_purple_list_focused /* NovaPlayer Prime */};
    private static final int[] GRADIENT_START = {R.color.theme_gradient_start_blue, R.color.theme_gradient_start_black, R.color.prime_theme_gold_gradient_start, R.color.prime_theme_burgundy_gradient_start, R.color.prime_theme_emerald_gradient_start, R.color.prime_theme_midnight_gradient_start, R.color.prime_theme_mahogany_gradient_start, R.color.prime_theme_purple_gradient_start /* NovaPlayer Prime */};
    private static final int[] GRADIENT_END = {R.color.theme_gradient_end_blue, R.color.theme_gradient_end_black, R.color.prime_theme_gold_gradient_end, R.color.prime_theme_burgundy_gradient_end, R.color.prime_theme_emerald_gradient_end, R.color.prime_theme_midnight_gradient_end, R.color.prime_theme_mahogany_gradient_end, R.color.prime_theme_purple_gradient_end /* NovaPlayer Prime */};
    private static final int[] RESCAN_COLOR = {R.color.theme_rescan_blue, R.color.theme_rescan_black, R.color.prime_theme_gold_rescan, R.color.prime_theme_burgundy_rescan, R.color.prime_theme_emerald_rescan, R.color.prime_theme_midnight_rescan, R.color.prime_theme_mahogany_rescan, R.color.prime_theme_purple_rescan /* NovaPlayer Prime */};
    private static final int[] SEARCH_AFFORDANCE = {R.color.theme_search_affordance_blue, R.color.theme_search_affordance_black, R.color.prime_theme_gold_search_affordance, R.color.prime_theme_burgundy_search_affordance, R.color.prime_theme_emerald_search_affordance, R.color.prime_theme_midnight_search_affordance, R.color.prime_theme_mahogany_search_affordance, R.color.prime_theme_purple_search_affordance /* NovaPlayer Prime */};
    
    // NovaPlayer Prime: accent and private-mode colors, same theme order as THEMES
    private static final int[] ACCENT = {R.color.theme_accent_blue, R.color.theme_accent_black, R.color.prime_theme_gold_accent, R.color.prime_theme_burgundy_accent, R.color.prime_theme_emerald_accent, R.color.prime_theme_midnight_accent, R.color.prime_theme_mahogany_accent, R.color.prime_theme_purple_accent};
    private static final int[] PRIVATE_MODE = {R.color.theme_private_mode_blue, R.color.theme_private_mode_black, R.color.prime_theme_gold_private_mode, R.color.prime_theme_burgundy_private_mode, R.color.prime_theme_emerald_private_mode, R.color.prime_theme_midnight_private_mode, R.color.prime_theme_mahogany_private_mode, R.color.prime_theme_purple_private_mode};

    private Context mContext;
    
    private int getThemeColor(int[] colorArray) {
        return ContextCompat.getColor(mContext, colorArray[getThemeIndex()]);
    }

    /** NovaPlayer Prime: index of the current theme in THEMES and in every color array (unknown → blue). */
    private int getThemeIndex() {
        String theme = getCurrentTheme();
        for (int i = 0; i < THEMES.length; i++) if (THEMES[i].equals(theme)) return i;
        return 0;
    }

    /** NovaPlayer Prime: TV theme style for the current theme, or 0 to keep the default (blue) one. */
    public int getLeanbackThemeStyle() {
        int i = getThemeIndex();
        return i == 0 ? 0 : LEANBACK_STYLES[i];
    }

    /** NovaPlayer Prime: TV settings style for the current theme, or 0 to keep the default (blue) one. */
    public int getLeanbackPreferencesStyle() {
        int i = getThemeIndex();
        return i == 0 ? 0 : LEANBACK_PREFERENCES_STYLES[i];
    }

    private ThemeManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    }
    
    public static synchronized ThemeManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ThemeManager(context.getApplicationContext());
        }
        return sInstance;
    }
    
    /**
     * Get the current theme setting
     */
    public String getCurrentTheme() {
        return mPrefs.getString(KEY_APP_THEME, THEME_BLUE);
    }
    
    /**
     * Check if black theme is active
     */
    public boolean isBlackTheme() {
        return THEME_BLACK.equals(getCurrentTheme());
    }
    
    /**
     * Get color for grid item background
     */
    public int getGridItemBackgroundColor() {
        return getThemeColor(GRID_ITEM_BACKGROUND);
    }

    /**
     * Get color for leanback background (main content area)
     */
    public int getLeanbackBackgroundColor() {
        return getThemeColor(LEANBACK_BACKGROUND);
    }

    /**
     * Get color for leanback headers (left sidebar)
     */
    public int getLeanbackHeaderColor() {
        return getThemeColor(LEANBACK_HEADER);
    }

    /**
     * Get color for leanback transparent background
     */
    public int getLeanbackTransparentColor() {
        return getThemeColor(LEANBACK_TRANSPARENT);
    }

    /**
     * Get color for toolbar background
     */
    public int getToolbarBackgroundColor() {
        return getThemeColor(TOOLBAR_BACKGROUND);
    }

    /**
     * Get primary color for details views
     */
    public int getDetailsPrimaryColor() {
        return getThemeColor(DETAILS_PRIMARY);
    }

    /**
     * Get secondary color for details views
     */
    public int getDetailsSecondaryColor() {
        return getThemeColor(DETAILS_SECONDARY);
    }

    /**
     * Get color for category selector highlight
     */
    public int getCategorySelectorColor() {
        return getThemeColor(CATEGORY_SELECTOR);
    }

    /**
     * Get color for list item pressed state
     */
    public int getListItemPressedColor() {
        return getThemeColor(LIST_ITEM_PRESSED);
    }

    /**
     * Get color for list item focused/activated state
     */
    public int getListItemFocusedColor() {
        return getThemeColor(LIST_ITEM_FOCUSED);
    }

    /**
     * Get gradient start color
     */
    public int getGradientStartColor() {
        return getThemeColor(GRADIENT_START);
    }

    /**
     * Get gradient end color
     */
    public int getGradientEndColor() {
        return getThemeColor(GRADIENT_END);
    }

    /**
     * Get rescan button color (dark green for black theme, green for blue theme)
     */
    public int getRescanColor() {
        return getThemeColor(RESCAN_COLOR);
    }

    /**
     * Get search affordance color (blue for both themes as requested)
     */
    public int getSearchAffordanceColor() {
        return getThemeColor(SEARCH_AFFORDANCE);
    }

    /**
     * Get accent color for progress bars and other accent UI elements
     */
    public int getAccentColor() {
        return getThemeColor(ACCENT);
    }

    /**
     * Get private mode background color
     * Blue theme: dark navy blue
     * Black theme: dark charcoal grey
     */
    public int getPrivateModeColor() {
        return getThemeColor(PRIVATE_MODE);
    }

    /**
     * Check if running in phone/tablet mode (not leanback/TV)
     */
    public boolean isPhoneMode(Context context) {
        return !UiChoiceDialog.applicationIsInLeanbackMode(context);
    }

    /**
     * Get phone-specific category gradient for black theme
     * Returns gradient from black to grey
     */
    public android.graphics.drawable.Drawable getPhoneCategoryGradient() {
        android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
            new int[] { getGradientStartColor(), getGradientEndColor() }
        );
        return gradient;
    }

    /**
     * Get phone-specific window gradient for black theme
     * Applies gradient to window, decor view, and status bar
     */
    @SuppressWarnings("deprecation")
    public void applyPhoneWindowGradient(Activity activity) {
        if (activity == null) return;

        int gradientStart = getGradientStartColor();
        int gradientEnd = getGradientEndColor();
        
        android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
            new int[] { gradientStart, gradientEnd }
        );
        
        Window window = activity.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(gradient);
            
            // Set status bar color to match gradient start (black) for cutout area
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            // setStatusBarColor/setNavigationBarColor are deprecated and ignored on Android 15+
            // (edge-to-edge is enforced and bars are transparent by default there): the window/
            // decor view gradient background set above already shows through in that case.
            if (Build.VERSION.SDK_INT < 35) {
                window.setStatusBarColor(gradientStart);
                window.setNavigationBarColor(gradientEnd);
            }

            // Extend into display cutout area
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(params);
            }
        }
        
        // Also apply to decor view to ensure cutout area is covered
        if (activity.getWindow().getDecorView() != null) {
            activity.getWindow().getDecorView().setBackground(gradient);
        }
    }

    /**
     * Get phone-specific category selector color for black theme
     * Returns grey color for rounded selector
     */
    public int getPhoneCategorySelectorColor() {
        return getThemeColor(CATEGORY_SELECTOR);
    }

    /**
     * Apply gradient background to window
     */
    public void applyWindowGradient(Activity activity) {
        if (activity == null) return;

        // BOTTOM_TO_TOP to match fork's angle=90
        android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
            new int[] { getGradientStartColor(), getGradientEndColor() }
        );
        activity.getWindow().setBackgroundDrawable(gradient);
    }
    
    /**
     * Apply theme to grid item CardView
     */
    public void applyGridItemCardTheme(CardView cardView) {
        if (cardView != null) {
            cardView.setCardBackgroundColor(getGridItemBackgroundColor());
        }
    }
    
    /**
     * Apply theme to toolbar/actionbar
     */
    public void applyToolbarTheme(ActionBar actionBar, Context context) {
        if (actionBar != null) {
            actionBar.setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(getToolbarBackgroundColor())
            );
        }
    }
    
    /**
     * Apply theme to leanback details row presenter
     */
    public void applyDetailsRowTheme(Object detailsRowPresenter, Context context) {
        // This is used in NetworkShortcutDetailsFragment and similar
        // Reflection or interface needed for leanback components
        try {
            Class<?> clazz = detailsRowPresenter.getClass();
            java.lang.reflect.Method setBg = clazz.getMethod("setBackgroundColor", int.class);
            java.lang.reflect.Method setActionsBg = clazz.getMethod("setActionsBackgroundColor", int.class);
            
            setBg.invoke(detailsRowPresenter, getDetailsPrimaryColor());
            setActionsBg.invoke(detailsRowPresenter, getDarkerColor(getDetailsPrimaryColor()));
        } catch (Exception e) {
            // Fallback - ignore if methods don't exist
        }
    }
    
    /**
     * Apply theme to window status/navigation bars and background
     */
    @SuppressWarnings("deprecation")
    public void applyWindowTheme(Activity activity) {
        if (activity == null) return;

        Window window = activity.getWindow();
        if (window == null) return;

        // Check if this is a leanback activity (TV mode)
        boolean isLeanback = UiChoiceDialog.applicationIsInLeanbackMode(activity);
        
        if (!isLeanback) {
            // Phone mode: Set window background gradient
            android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] { getGradientStartColor(), getGradientEndColor() }
            );
            window.setBackgroundDrawable(gradient);

            // Also apply to decor view to ensure cutout area is covered
            if (activity.getWindow().getDecorView() != null) {
                activity.getWindow().getDecorView().setBackground(gradient);
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        // setStatusBarColor/setNavigationBarColor are deprecated and ignored on Android 15+
        // (edge-to-edge is enforced and bars are transparent by default there): the window/
        // decor view background set above (phone mode) or the theme background (TV) already
        // shows through in that case.
        if (Build.VERSION.SDK_INT < 35) {
            // Check if phone mode with black theme for special status bar handling
            if (isBlackTheme() && isPhoneMode(activity)) {
                // Phone + Black theme: status bar = black (gradient start), nav bar = grey (gradient end)
                window.setStatusBarColor(getGradientStartColor());
                window.setNavigationBarColor(getGradientEndColor());
            } else {
                // TV or Blue theme: use standard theme colors
                window.setStatusBarColor(getLeanbackBackgroundColor());
                window.setNavigationBarColor(getLeanbackBackgroundColor());
            }
        }

        // Ensure the theme color extends into the display cutout area (notch/punch-hole)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(params);
        }
    }
    
    /**
     * Apply theme to a generic view background
     */
    public void applyViewBackground(View view) {
        if (view != null) {
            view.setBackgroundColor(getLeanbackBackgroundColor());
        }
    }
    
    /**
     * Get a darker variant of a color (for action backgrounds)
     */
    private int getDarkerColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.8f;
        return Color.HSVToColor(hsv);
    }
    
    /**
     * Register listener for theme changes
     */
    public void registerThemeChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        mPrefs.registerOnSharedPreferenceChangeListener(listener);
    }
    
    /**
     * Unregister listener for theme changes
     */
    public void unregisterThemeChangeListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        mPrefs.unregisterOnSharedPreferenceChangeListener(listener);
    }
}
