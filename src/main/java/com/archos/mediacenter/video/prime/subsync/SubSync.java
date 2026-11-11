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

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * "Sync subtitles automatically": listens to four stretches of the sound, matches them with
 * the subtitle file and reports the best delay (and frame-rate fix) for NOVA's own settings.
 * Runs on its own thread; results come back on the main thread. One run at a time.
 */
public final class SubSync {

    private static final Logger log = LoggerFactory.getLogger(SubSync.class);
    private static final int WINDOW_MS = 40_000;
    private static final double[] WINDOW_AT = {0.12, 0.37, 0.62, 0.87};
    private static final int MAX_SUB_BYTES = 8 * 1024 * 1024;

    public enum Failure {
        /** The selected subtitle is built into the video or is a picture format. */
        NOT_A_FILE,
        /** The subtitle file could not be read, or holds no timed lines. */
        UNREADABLE,
        /** None of the video's sound tracks can be decoded by the TV here (often DTS). */
        NO_DECODER,
        /** The video could not be opened or read for analysis. */
        NO_SOUND,
        /** No delay stands out: the subtitles may be for another version of the film. */
        NO_MATCH,
    }

    public interface Listener {
        void onProgress(int done, int total);
        void onSynced(int delayMs, int ratioIndex);
        void onFailed(Failure why);
    }

    private static volatile SubSync sRunning;

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private volatile boolean mCancelled;

    private SubSync() {}

    public static boolean isRunning() {
        return sRunning != null;
    }

    public static void cancelRunning() {
        SubSync s = sRunning;
        if (s != null) s.mCancelled = true;
    }

    /**
     * @param media        what the player is playing (its local proxy address for network shares)
     * @param subtitlePath the selected external subtitle file
     * @param durationMs   length of the video
     * @return false when another sync is still running
     */
    public static synchronized boolean start(Context context, Uri media, String subtitlePath, int durationMs, Listener listener) {
        if (sRunning != null) return false;
        SubSync s = new SubSync();
        sRunning = s;
        final Context app = context.getApplicationContext();
        Thread t = new Thread(() -> s.run(app, media, subtitlePath, durationMs, listener), "SubSync");
        t.setPriority(Thread.MIN_PRIORITY); // playback first
        t.start();
        return true;
    }

    private void run(Context context, Uri media, String subtitlePath, int durationMs, Listener listener) {
        try {
            List<SubtitleCues.Cue> cues;
            try {
                cues = SubtitleCues.parse(readText(context, subtitlePath));
            } catch (IOException e) {
                log.warn("run: cannot read subtitles {}", subtitlePath, e);
                cues = null;
            }
            if (cues == null || cues.size() < 20) {
                post(() -> listener.onFailed(Failure.UNREADABLE));
                return;
            }
            int length = durationMs > 0 ? durationMs : cues.get(cues.size() - 1).end;
            int[] starts = new int[WINDOW_AT.length];
            for (int i = 0; i < starts.length; i++) {
                starts[i] = (int) Math.max(0, Math.min(length - WINDOW_MS, length * WINDOW_AT[i])) / 10 * 10;
            }
            List<SubSyncMatcher.Window> windows;
            try {
                windows = SpeechSampler.sample(context, media, starts, WINDOW_MS, new SpeechSampler.Progress() {
                    @Override
                    public void onWindow(int done, int total) {
                        post(() -> listener.onProgress(done, total));
                    }

                    @Override
                    public boolean cancelled() {
                        return mCancelled;
                    }
                });
            } catch (SpeechSampler.NoDecoderException e) {
                log.warn("run: {}", e.getMessage());
                post(() -> listener.onFailed(Failure.NO_DECODER));
                return;
            } catch (IOException | RuntimeException e) {
                log.warn("run: cannot sample sound of {}", media, e);
                post(() -> listener.onFailed(Failure.NO_SOUND));
                return;
            }
            if (mCancelled) return;
            if (windows.size() < 2) {
                post(() -> listener.onFailed(Failure.NO_SOUND));
                return;
            }
            SubSyncMatcher.Result r = SubSyncMatcher.match(cues, windows);
            log.info("run: delay={} ratio={} correlation={} peakZ={} confident={}",
                    r.delayMs, r.ratioIndex, r.correlation, r.peakZ, r.confident);
            if (mCancelled) return;
            if (!r.confident) post(() -> listener.onFailed(Failure.NO_MATCH));
            else post(() -> listener.onSynced(r.delayMs, r.ratioIndex));
        } finally {
            sRunning = null;
        }
    }

    private void post(Runnable r) {
        mMain.post(() -> {
            if (!mCancelled) r.run();
        });
    }

    static String readText(Context context, String path) throws IOException {
        InputStream in;
        if (path.startsWith("/")) {
            in = new FileInputStream(new File(path));
        } else {
            Uri u = Uri.parse(path);
            String scheme = u.getScheme() == null ? "" : u.getScheme();
            switch (scheme) {
                case "file":
                    in = new FileInputStream(new File(u.getPath()));
                    break;
                case "content":
                    in = context.getContentResolver().openInputStream(u);
                    break;
                case "http":
                case "https":
                    HttpURLConnection c = (HttpURLConnection) new URL(path).openConnection();
                    c.setConnectTimeout(15_000);
                    c.setReadTimeout(15_000);
                    in = c.getInputStream();
                    break;
                default:
                    throw new IOException("unsupported subtitle location " + scheme);
            }
        }
        if (in == null) throw new IOException("cannot open " + path);
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = s.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_SUB_BYTES) throw new IOException("subtitle file too large");
            }
            // Only the digits and separators of the timings matter: any 8-bit charset reads them
            byte[] b = out.toByteArray();
            boolean utf16 = b.length > 1 && ((b[0] == (byte) 0xFF && b[1] == (byte) 0xFE) || (b[0] == (byte) 0xFE && b[1] == (byte) 0xFF));
            return new String(b, utf16 ? StandardCharsets.UTF_16 : StandardCharsets.ISO_8859_1);
        }
    }
}
