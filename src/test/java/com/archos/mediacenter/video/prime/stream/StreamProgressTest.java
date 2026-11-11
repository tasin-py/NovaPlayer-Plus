package com.archos.mediacenter.video.prime.stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Continue watching for streamed links, and the Search filter. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StreamProgressTest {

    private Context c;

    @Before
    public void setUp() {
        c = ApplicationProvider.getApplicationContext();
        c.getSharedPreferences("prime_stream_progress", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void positionIsRememberedForTheOpenedLink() {
        StreamProgress.start(c, "https://x.test/show/ep1.mkv", "Episode 1", "https://cdn.test/ep1");
        StreamProgress.record(c, "https://cdn.test/ep1", 125_000, 2_700_000);
        StreamProgress.Entry e = StreamProgress.find(c, "https://x.test/show/ep1.mkv");
        assertEquals(125_000, e.position);
        assertEquals(2_700_000, e.duration);
        assertEquals("Episode 1", e.name);
        assertEquals(1, StreamProgress.resumable(c).size());
    }

    @Test
    public void unknownVideosAreIgnored() {
        StreamProgress.record(c, "file:///storage/movie.mkv", 125_000, 2_700_000);
        assertTrue(StreamProgress.load(c).isEmpty());
    }

    @Test
    public void finishedOrBarelyStartedVideosAreNotOffered() {
        StreamProgress.start(c, "u1", "A", "p1");
        StreamProgress.record(c, "p1", 5_000, 100_000);
        assertTrue(StreamProgress.resumable(c).isEmpty()); // under 10 seconds
        StreamProgress.record(c, "p1", 96_000, 100_000);
        assertNull(StreamProgress.find(c, "u1")); // past 95%
        StreamProgress.start(c, "u2", "B", "p2");
        StreamProgress.record(c, "p2", 50_000, 100_000);
        StreamProgress.record(c, "p2", -2, 100_000); // NOVA: played to the end
        assertNull(StreamProgress.find(c, "u2"));
    }

    @Test
    public void reopeningKeepsThePositionAndNewestComesFirst() {
        StreamProgress.start(c, "u1", "A", "p1");
        StreamProgress.record(c, "p1", 60_000, 0);
        StreamProgress.start(c, "u2", "B", "p2");
        StreamProgress.record(c, "p2", 30_000, 0);
        assertEquals("u2", StreamProgress.resumable(c).get(0).url);
        StreamProgress.start(c, "u1", null, "p1");
        assertEquals("u1", StreamProgress.resumable(c).get(0).url);
        assertEquals(60_000, StreamProgress.find(c, "u1").position);
        assertEquals("A", StreamProgress.find(c, "u1").name);
    }

    @Test
    public void hidingFromHomeKeepsResumeUntilWatchedAgain() {
        StreamProgress.start(c, "u1", "A", "p1");
        StreamProgress.record(c, "p1", 60_000, 0);
        StreamProgress.hideFromHome(c, "u1");
        assertTrue(StreamProgress.forHome(c).isEmpty());
        assertEquals(1, StreamProgress.resumable(c).size()); // still on the Stream screen
        assertEquals(60_000, StreamProgress.find(c, "u1").position);
        StreamProgress.start(c, "u1", null, "p1");
        StreamProgress.record(c, "p1", 70_000, 0);
        assertEquals(1, StreamProgress.forHome(c).size()); // watched again: back on the home screen
    }

    @Test
    public void timesAreFormatted() {
        assertEquals("4:07", StreamProgress.formatTime(247_000));
        assertEquals("1:05:09", StreamProgress.formatTime(3_909_000));
        assertEquals("0:00", StreamProgress.formatTime(-5));
    }

    @Test
    public void searchMatchesEveryWordInAnyOrder() {
        assertTrue(StreamSearch.matches("Friends.S02E05.1080p", "s02 friends"));
        assertTrue(StreamSearch.matches("Anything", "  "));
        assertFalse(StreamSearch.matches("Friends.S02E05", "s03"));
        assertFalse(StreamSearch.matches(null, "a"));
    }
}
