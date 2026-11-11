// Copyright 2026 tasin-py (Nova Player Plus)
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

package com.archos.mediacenter.video.prime.subsync;

import java.util.List;

/**
 * Finds the subtitle delay (and frame-rate fix) that best lines subtitle lines up with speech.
 * <p>
 * NOVA's engine shows a line whose file time is {@code c} at video time {@code c * ratio + delay}
 * (ratio: 1, 25025/24000 "NTSC to PAL" or 24000/25025 "PAL to NTSC"). For every candidate we
 * correlate "a line is on screen" with the loudness of the speech band in sampled stretches
 * of the sound track, coarse (100 ms steps) then fine (10 ms steps). Pure Java: unit tested.
 */
public final class SubSyncMatcher {

    /** Feature resolution of the sound samples. */
    public static final int STEP_MS = 10;
    private static final int COARSE = 10; // 10 steps = 100 ms
    public static final int MAX_DELAY_MS = 120_000;
    /** NOVA's ratio settings, by index (PlayerActivity / Player.setSubtitleRatio). */
    static final double[] RATIOS = {1.0, 25025.0 / 24000.0, 24000.0 / 25025.0};
    /** A frame-rate fix must be clearly better than none. */
    private static final double RATIO_MARGIN = 1.15;
    /** How far the best match must stand out from all the other delays. */
    static final double MIN_PEAK_Z = 5.0;
    static final double MIN_CORRELATION = 0.05;

    /** One stretch of sound: speech-band loudness every {@link #STEP_MS}, starting at {@code startMs}. */
    public static final class Window {
        public final int startMs;
        public final float[] loudness;

        public Window(int startMs, float[] loudness) {
            this.startMs = startMs;
            this.loudness = loudness;
        }
    }

    public static final class Result {
        /** Value for NOVA's subtitle delay (ms; positive shows the lines later). */
        public final int delayMs;
        /** Value for NOVA's subtitle ratio setting (0 none, 1 NTSC to PAL, 2 PAL to NTSC). */
        public final int ratioIndex;
        public final double correlation;
        /** How many standard deviations the best match stands above the other delays. */
        public final double peakZ;
        public final boolean confident;

        Result(int delayMs, int ratioIndex, double correlation, double peakZ) {
            this.delayMs = delayMs;
            this.ratioIndex = ratioIndex;
            this.correlation = correlation;
            this.peakZ = peakZ;
            this.confident = peakZ >= MIN_PEAK_Z && correlation >= MIN_CORRELATION;
        }
    }

    private SubSyncMatcher() {}

    public static Result match(List<SubtitleCues.Cue> cues, List<Window> windows) {
        if (cues == null || cues.isEmpty() || windows == null || windows.isEmpty()) {
            return new Result(0, 0, 0, 0);
        }
        // Speech loudness, centred per window (so only the ups and downs count)
        float[][] audio = new float[windows.size()][];
        for (int w = 0; w < windows.size(); w++) audio[w] = centred(windows.get(w).loudness);

        Result best = null;
        Result plain = null;
        for (int r = 0; r < RATIOS.length; r++) {
            Result res = matchRatio(cues, windows, audio, r);
            if (r == 0) plain = res;
            if (best == null || res.correlation > best.correlation) best = res;
        }
        if (best.ratioIndex != 0 && best.correlation < plain.correlation * RATIO_MARGIN) best = plain;
        return best;
    }

    private static Result matchRatio(List<SubtitleCues.Cue> cues, List<Window> windows, float[][] audio, int ratioIndex) {
        double ratio = RATIOS[ratioIndex];
        // "A line is on screen" at video time v (10 ms steps) with delay 0, padded so v - delay stays inside
        int pad = MAX_DELAY_MS / STEP_MS + 1;
        int lastEnd = 0;
        for (SubtitleCues.Cue c : cues) lastEnd = Math.max(lastEnd, (int) Math.ceil(c.end * ratio / STEP_MS));
        int maxWindowEnd = 0;
        for (Window w : windows) maxWindowEnd = Math.max(maxWindowEnd, w.startMs / STEP_MS + w.loudness.length);
        int length = Math.max(lastEnd, maxWindowEnd) + 2 * pad;
        // prefix sums of the on-screen mask, shifted by pad
        int[] on = new int[length + 1];
        byte[] mask = new byte[length];
        for (SubtitleCues.Cue c : cues) {
            int s = (int) Math.round(c.start * ratio / STEP_MS) + pad;
            int e = (int) Math.round(c.end * ratio / STEP_MS) + pad;
            for (int i = Math.max(0, s); i < Math.min(length, e); i++) mask[i] = 1;
        }
        for (int i = 0; i < length; i++) on[i + 1] = on[i] + mask[i];

        // Coarse: 100 ms delays, 100 ms buckets
        int maxCoarse = MAX_DELAY_MS / (STEP_MS * COARSE);
        double[] coarse = new double[2 * maxCoarse + 1];
        float[][] audio100 = new float[audio.length][];
        for (int w = 0; w < audio.length; w++) audio100[w] = bucket(audio[w]);
        for (int k = -maxCoarse; k <= maxCoarse; k++) {
            coarse[k + maxCoarse] = correlation(windows, audio100, on, pad, k * COARSE, COARSE);
        }
        int bestK = 0;
        for (int k = 0; k < coarse.length; k++) if (coarse[k] > coarse[bestK]) bestK = k;
        double z = peakZ(coarse, bestK, 20); // ignore ±2 s around the peak

        // Fine: 10 ms delays around the coarse peak
        int centre = (bestK - maxCoarse) * COARSE;
        int span = 4 * COARSE;
        double[] fine = new double[2 * span + 1];
        int bestI = span;
        for (int i = 0; i < fine.length; i++) {
            int d = centre - span + i;
            fine[i] = Math.abs(d) * STEP_MS > MAX_DELAY_MS ? Double.NEGATIVE_INFINITY
                    : correlation(windows, audio, on, pad, d, 1);
            if (fine[i] > fine[bestI]) bestI = i;
        }
        // Speech sits inside its line with some slack, so the top is a plateau: take its middle
        double bestCorr = fine[bestI];
        double floor = bestCorr - Math.abs(bestCorr) * 0.03;
        int lo = bestI, hi = bestI;
        while (lo > 0 && fine[lo - 1] >= floor) lo--;
        while (hi < fine.length - 1 && fine[hi + 1] >= floor) hi++;
        int bestFine = centre - span + (lo + hi) / 2;
        return new Result(bestFine * STEP_MS, ratioIndex, bestCorr, z);
    }

    /**
     * Pearson correlation between speech loudness and "a line is on screen" when lines are shown
     * {@code delaySteps} later. {@code bucket} is the number of 10 ms steps per audio value.
     */
    private static double correlation(List<Window> windows, float[][] audio, int[] on, int pad, int delaySteps, int bucket) {
        double sumAM = 0, sumAA = 0, sumM = 0;
        long n = 0;
        for (int w = 0; w < audio.length; w++) {
            float[] a = audio[w];
            int base = windows.get(w).startMs / STEP_MS - delaySteps + pad;
            for (int i = 0; i < a.length; i++) {
                int from = base + i * bucket;
                int to = from + bucket;
                if (from < 0 || to >= on.length) continue;
                double m = (on[to] - on[from]) / (double) bucket; // share of the bucket with a line on screen
                sumAM += a[i] * m;
                sumAA += a[i] * (double) a[i];
                sumM += m;
                n++;
            }
        }
        if (n == 0 || sumAA <= 0) return 0;
        double meanM = sumM / n;
        // variance of the mask (m is 0..1; m² <= m, exact for 10 ms steps)
        double varM = sumM / n - meanM * meanM;
        if (varM <= 1e-9) return 0;
        // audio is centred per window, so cov = mean(a*m)
        return (sumAM / n) / Math.sqrt((sumAA / n) * varM);
    }

    private static double peakZ(double[] values, int peak, int exclude) {
        double sum = 0, sq = 0;
        int n = 0;
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(i - peak) <= exclude) continue;
            sum += values[i];
            sq += values[i] * values[i];
            n++;
        }
        if (n < 2) return 0;
        double mean = sum / n;
        double sd = Math.sqrt(Math.max(1e-12, sq / n - mean * mean));
        return (values[peak] - mean) / sd;
    }

    private static float[] centred(float[] x) {
        double mean = 0;
        for (float v : x) mean += v;
        mean /= Math.max(1, x.length);
        float[] out = new float[x.length];
        for (int i = 0; i < x.length; i++) out[i] = (float) (x[i] - mean);
        return out;
    }

    private static float[] bucket(float[] x) {
        float[] out = new float[x.length / COARSE];
        for (int i = 0; i < out.length; i++) {
            float s = 0;
            for (int j = 0; j < COARSE; j++) s += x[i * COARSE + j];
            out[i] = s / COARSE;
        }
        return out;
    }
}
