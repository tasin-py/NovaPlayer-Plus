package com.archos.mediacenter.video.prime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import com.archos.mediacenter.video.R;
import com.archos.mediacenter.video.utils.ThemeManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.Set;

/** The extra themes: every one is selectable, complete, distinct; Blue and Black are unchanged. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PrimeThemesTest {

    private final Context c = ApplicationProvider.getApplicationContext();

    @org.junit.Before
    public void resetSingleton() throws Exception {
        // Robolectric gives each test a new app; drop the instance bound to the previous one
        java.lang.reflect.Field f = ThemeManager.class.getDeclaredField("sInstance");
        f.setAccessible(true);
        f.set(null, null);
    }

    private ThemeManager select(String value) {
        PreferenceManager.getDefaultSharedPreferences(c).edit().putString(ThemeManager.KEY_APP_THEME, value).commit();
        return ThemeManager.getInstance(c);
    }

    @Test
    public void blueAndBlackUnchanged() {
        ThemeManager t = select("blue");
        assertEquals(ContextCompat.getColor(c, R.color.theme_background_blue), t.getLeanbackBackgroundColor());
        assertEquals(ContextCompat.getColor(c, R.color.theme_accent_blue), t.getAccentColor());
        assertEquals(0, t.getLeanbackThemeStyle());
        assertEquals(0, t.getLeanbackPreferencesStyle());

        t = select("black");
        assertTrue(t.isBlackTheme());
        assertEquals(ContextCompat.getColor(c, R.color.theme_background_black), t.getLeanbackBackgroundColor());
        assertEquals(ContextCompat.getColor(c, R.color.theme_private_mode_black), t.getPrivateModeColor());
        assertEquals(R.style.MyLeanbackTheme_Black, t.getLeanbackThemeStyle());
        assertEquals(R.style.MyLeanbackTheme_Preferences_Black, t.getLeanbackPreferencesStyle());
    }

    @Test
    public void everyListedThemeWorks() {
        String[] values = c.getResources().getStringArray(R.array.theme_selection_entryvalues);
        String[] names = c.getResources().getStringArray(R.array.theme_selection_entries);
        assertEquals(8, values.length);
        assertEquals(values.length, names.length);
        Set<Integer> backgrounds = new HashSet<>();
        for (int i = 2; i < values.length; i++) {
            ThemeManager t = select(values[i]);
            assertTrue(values[i], !t.isBlackTheme());
            assertNotEquals(values[i], 0, t.getLeanbackThemeStyle());
            assertNotEquals(values[i], 0, t.getLeanbackPreferencesStyle());
            // all getters resolve (a missing color would throw)
            t.getGridItemBackgroundColor(); t.getLeanbackHeaderColor(); t.getLeanbackTransparentColor();
            t.getToolbarBackgroundColor(); t.getDetailsPrimaryColor(); t.getDetailsSecondaryColor();
            t.getCategorySelectorColor(); t.getListItemPressedColor(); t.getListItemFocusedColor();
            t.getGradientStartColor(); t.getGradientEndColor(); t.getRescanColor(); t.getSearchAffordanceColor();
            t.getAccentColor(); t.getPrivateModeColor();
            assertTrue("duplicate background " + values[i], backgrounds.add(t.getLeanbackBackgroundColor()));
        }
    }

    @Test
    public void unknownThemeFallsBackToBlue() {
        ThemeManager t = select("something-else");
        assertEquals(ContextCompat.getColor(c, R.color.theme_background_blue), t.getLeanbackBackgroundColor());
        assertEquals(0, t.getLeanbackThemeStyle());
    }
}
