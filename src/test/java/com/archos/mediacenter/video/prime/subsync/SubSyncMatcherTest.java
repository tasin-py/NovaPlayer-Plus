package com.archos.mediacenter.video.prime.subsync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The matcher finds the delay and frame-rate fix from noisy, partial "speech" in four stretches. */
public class SubSyncMatcherTest {

    private static final int MOVIE_MS = 110 * 60_000;

    private static List<SubtitleCues.Cue> randomCues(Random rnd) {
        List<SubtitleCues.Cue> cues = new ArrayList<>();
        int t = 30_000;
        while (t < MOVIE_MS - 10_000) {
            int len = 800 + rnd.nextInt(4200);
            cues.add(new SubtitleCues.Cue(t, t + len));
            t += len + 300 + rnd.nextInt(rnd.nextInt(10) == 0 ? 40_000 : 5_000);
        }
        return cues;
    }

    /** Four 40 s stretches where speech happens while a line would be shown with the true settings. */
    private static List<SubSyncMatcher.Window> speech(List<SubtitleCues.Cue> cues, double ratio, int delayMs,
                                                      double noise, Random rnd) {
        List<SubSyncMatcher.Window> windows = new ArrayList<>();
        for (double share : new double[]{0.12, 0.37, 0.62, 0.87}) {
            int start = (int) (MOVIE_MS * share) / 10 * 10;
            float[] a = new float[4000];
            for (int i = 0; i < a.length; i++) {
                int v = start + i * SubSyncMatcher.STEP_MS;
                boolean talking = false;
                for (SubtitleCues.Cue c : cues) {
                    // speech starts a bit after the line appears and ends a bit before it goes
                    if (v >= c.start * ratio + delayMs + 150 && v < c.end * ratio + delayMs - 200) {
                        talking = true;
                        break;
                    }
                }
                a[i] = (float) ((talking ? 1.0 : 0.0) + rnd.nextGaussian() * noise);
            }
            // a loud music stretch with no speech in each window
            for (int i = 1500; i < 2100; i++) a[i] += 1.2f;
            windows.add(new SubSyncMatcher.Window(start, a));
        }
        return windows;
    }

    @Test
    public void findsAPlainDelay() {
        Random rnd = new Random(1);
        List<SubtitleCues.Cue> cues = randomCues(rnd);
        SubSyncMatcher.Result r = SubSyncMatcher.match(cues, speech(cues, 1.0, 2340, 0.8, rnd));
        assertTrue("confident z=" + r.peakZ + " c=" + r.correlation, r.confident);
        assertEquals(0, r.ratioIndex);
        assertEquals(2340, r.delayMs, 60);
    }

    @Test
    public void findsANegativeDelay() {
        Random rnd = new Random(2);
        List<SubtitleCues.Cue> cues = randomCues(rnd);
        SubSyncMatcher.Result r = SubSyncMatcher.match(cues, speech(cues, 1.0, -47_500, 0.8, rnd));
        assertTrue(r.confident);
        assertEquals(0, r.ratioIndex);
        assertEquals(-47_500, r.delayMs, 60);
    }

    @Test
    public void findsAFrameRateMismatch() {
        Random rnd = new Random(3);
        List<SubtitleCues.Cue> cues = randomCues(rnd);
        SubSyncMatcher.Result r = SubSyncMatcher.match(cues, speech(cues, 25025.0 / 24000.0, 800, 0.8, rnd));
        assertTrue(r.confident);
        assertEquals(1, r.ratioIndex);
        assertEquals(800, r.delayMs, 60);

        Random rnd2 = new Random(4);
        List<SubtitleCues.Cue> cues2 = randomCues(rnd2);
        SubSyncMatcher.Result r2 = SubSyncMatcher.match(cues2, speech(cues2, 24000.0 / 25025.0, -1500, 0.8, rnd2));
        assertTrue(r2.confident);
        assertEquals(2, r2.ratioIndex);
        assertEquals(-1500, r2.delayMs, 60);
    }

    @Test
    public void alreadyInSyncStaysAtZero() {
        Random rnd = new Random(5);
        List<SubtitleCues.Cue> cues = randomCues(rnd);
        SubSyncMatcher.Result r = SubSyncMatcher.match(cues, speech(cues, 1.0, 0, 0.8, rnd));
        assertTrue(r.confident);
        assertEquals(0, r.ratioIndex);
        assertEquals(0, r.delayMs, 60);
    }

    @Test
    public void unrelatedSoundIsNotTrusted() {
        Random rnd = new Random(6);
        List<SubtitleCues.Cue> cues = randomCues(rnd);
        List<SubtitleCues.Cue> otherMovie = randomCues(new Random(99));
        SubSyncMatcher.Result r = SubSyncMatcher.match(cues, speech(otherMovie, 1.0, 0, 0.8, rnd));
        assertFalse("z=" + r.peakZ + " c=" + r.correlation, r.confident);
    }

    @Test
    public void readsSrtVttAndAss() {
        List<SubtitleCues.Cue> srt = SubtitleCues.parse("1\r\n00:01:02,345 --> 00:01:04,000\r\nHello\r\n\r\n2\r\n01:00:00,5 --> 01:00:01,250\r\nBye\r\n");
        assertEquals(2, srt.size());
        assertEquals(62_345, srt.get(0).start);
        assertEquals(64_000, srt.get(0).end);
        assertEquals(3_600_500, srt.get(1).start);

        List<SubtitleCues.Cue> vtt = SubtitleCues.parse("WEBVTT\n\n01:02.345 --> 01:04.000 align:start\nHi\n");
        assertEquals(1, vtt.size());
        assertEquals(62_345, vtt.get(0).start);

        List<SubtitleCues.Cue> ass = SubtitleCues.parse("[Events]\nFormat: Layer, Start, End, Style, Text\n"
                + "Dialogue: 0,0:01:02.34,0:01:04.00,Default,,0,0,0,,Hi\nDialogue: Marked=0,1:00:00.50,1:00:01.25,Default,Bye\n");
        assertEquals(2, ass.size());
        assertEquals(62_340, ass.get(0).start);
        assertEquals(3_600_500, ass.get(1).start);
    }
}
