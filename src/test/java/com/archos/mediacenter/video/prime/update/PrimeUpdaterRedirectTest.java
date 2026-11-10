package com.archos.mediacenter.video.prime.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.net.URL;

/** The updater only follows "moved" redirects of this owner's renamed repo on github.com. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class PrimeUpdaterRedirectTest {

    private static boolean ok(String u) throws Exception {
        return PrimeUpdater.isMovedRepoLatestPage(new URL(u));
    }

    @Test
    public void renamedRepoIsFollowed() throws Exception {
        assertTrue(ok("https://github.com/tasin-py/NovaPlayer-Plus/releases/latest"));
        assertTrue(ok("https://github.com/tasin-py/Some.Other_Name-2/releases/latest"));
    }

    @Test
    public void anythingElseIsNot() throws Exception {
        assertFalse(ok("http://github.com/tasin-py/NovaPlayer-Plus/releases/latest"));
        assertFalse(ok("https://evil.com/tasin-py/NovaPlayer-Plus/releases/latest"));
        assertFalse(ok("https://github.com.evil.com/tasin-py/NovaPlayer-Plus/releases/latest"));
        assertFalse(ok("https://github.com/someone-else/NovaPlayer-Plus/releases/latest"));
        assertFalse(ok("https://github.com/tasin-py/NovaPlayer-Plus/releases"));
        assertFalse(ok("https://github.com/tasin-py/NovaPlayer-Plus/releases/latest?x=1"));
        assertFalse(ok("https://github.com:8443/tasin-py/NovaPlayer-Plus/releases/latest"));
        assertFalse(ok("https://github.com/login?return_to=/tasin-py/NovaPlayer-Plus/releases/latest"));
    }

    @Test
    public void plusAndEarlyPrimeTagsAreUnderstood() {
        assertEquals(2674900, PrimeUpdater.parseVersionCode("plus-2674900"));
        assertEquals(2674812, PrimeUpdater.parseVersionCode("prime-2674812"));
        assertEquals(-1, PrimeUpdater.parseVersionCode("v1.0"));
        assertEquals(-1, PrimeUpdater.parseVersionCode(null));
        assertEquals("NovaPlayerPlus-", PrimeUpdater.apkNamePrefix("plus-2674900"));
        assertEquals("prime-", PrimeUpdater.apkNamePrefix("prime-2674812"));
    }
}
