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

package com.archos.mediacenter.video.prime.stream;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Where each streamed video was stopped, for "Continue watching" and "Resume from".
 * NOVA only remembers positions of videos in its library; streamed links are not in it.
 * The player reports positions by its play address; {@link #start} links that address to
 * the link the user opened. Finished videos drop out; everything is forgotten after 30 days.
 */
public final class StreamProgress {

    public static final long RETENTION_MS = TimeUnit.DAYS.toMillis(30);
    /** Shorter than this is "just looked at it", not worth resuming. */
    public static final int MIN_RESUME_MS = 10_000;
    /** Past this share of the video it counts as watched to the end. */
    static final double FINISHED_SHARE = 0.95;
    private static final int MAX_ENTRIES = 30;

    private static final String PREFS = "prime_stream_progress";
    private static final String KEY = "entries";
    /** NOVA's PlayerService.LAST_POSITION_END: the video played to its end. */
    private static final int POSITION_END = -2;

    public static final class Entry {
        public final String url;
        public final String name;
        public final String playUri;
        public final int position;
        public final int duration;
        public final long time;
        /** Removed from the home screen's Recently added row (still resumable on the Stream screen). */
        public final boolean hiddenFromHome;

        Entry(String url, String name, String playUri, int position, int duration, long time) {
            this(url, name, playUri, position, duration, time, false);
        }

        Entry(String url, String name, String playUri, int position, int duration, long time, boolean hiddenFromHome) {
            this.url = url;
            this.name = name;
            this.playUri = playUri;
            this.position = position;
            this.duration = duration;
            this.time = time;
            this.hiddenFromHome = hiddenFromHome;
        }

        public boolean canResume() {
            return position >= MIN_RESUME_MS;
        }
    }

    private StreamProgress() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Newest first, older than 30 days dropped. */
    public static synchronized List<Entry> load(Context context) {
        List<Entry> result = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", "");
                long time = o.optLong("time", 0);
                if (url.isEmpty() || time < cutoff) continue;
                result.add(new Entry(url, o.optString("name", url), o.optString("play", url),
                        o.optInt("pos", 0), o.optInt("dur", 0), time, o.optBoolean("hidden", false)));
            }
        } catch (JSONException ignored) {
            // unreadable: behave as empty
        }
        return result;
    }

    /** The videos that can be resumed, newest first. */
    public static List<Entry> resumable(Context context) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : load(context)) if (e.canResume()) result.add(e);
        return result;
    }

    /** What the home screen shows: resumable and not removed from there. */
    public static List<Entry> forHome(Context context) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : resumable(context)) if (!e.hiddenFromHome) result.add(e);
        return result;
    }

    /** Takes a video off the home screen only; watching it again brings it back. */
    public static synchronized void hideFromHome(Context context, String url) {
        List<Entry> entries = load(context);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.url.equals(url)) entries.set(i, new Entry(e.url, e.name, e.playUri, e.position, e.duration, e.time, true));
        }
        save(context, entries);
    }

    public static Entry find(Context context, String url) {
        if (url == null) return null;
        for (Entry e : load(context)) if (e.url.equals(url)) return e;
        return null;
    }

    /** Called right before the player opens a link: remember which link this play address belongs to. */
    public static synchronized void start(Context context, String url, String name, String playUri) {
        if (url == null || url.isEmpty() || playUri == null) return;
        List<Entry> entries = load(context);
        int position = 0, duration = 0;
        String keepName = name;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            if (e.url.equals(url) || e.playUri.equals(playUri)) {
                if (e.url.equals(url)) {
                    position = e.position;
                    duration = e.duration;
                    if (keepName == null || keepName.isEmpty()) keepName = e.name;
                }
                entries.remove(i);
            }
        }
        if (keepName == null || keepName.isEmpty()) keepName = url;
        entries.add(0, new Entry(url, keepName, playUri, position, duration, System.currentTimeMillis()));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
        save(context, entries);
    }

    /**
     * Called by the player whenever NOVA saves its own position. Does nothing for anything
     * that was not opened from the Stream screens (library videos keep NOVA's own resume).
     */
    public static synchronized void record(Context context, String playUri, int position, int duration) {
        if (context == null || playUri == null) return;
        List<Entry> entries = load(context);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (!e.playUri.equals(playUri)) continue;
            int dur = duration > 0 ? duration : e.duration;
            if (isFinished(position, dur)) {
                entries.remove(i);
            } else if (position >= 0) {
                entries.remove(i);
                entries.add(0, new Entry(e.url, e.name, e.playUri, position, dur, System.currentTimeMillis()));
            } else {
                return; // unknown position: keep what we had
            }
            save(context, entries);
            return;
        }
    }

    static boolean isFinished(int position, int duration) {
        if (position == POSITION_END) return true;
        return duration > 0 && position >= duration * FINISHED_SHARE;
    }

    public static synchronized void remove(Context context, String url) {
        List<Entry> entries = load(context);
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).url.equals(url)) entries.remove(i);
        }
        save(context, entries);
    }

    /** "1:05:09" or "4:07". */
    public static String formatTime(int ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, sec)
                : String.format(java.util.Locale.US, "%d:%02d", m, sec);
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry e : entries) {
            try {
                array.put(new JSONObject().put("url", e.url).put("name", e.name).put("play", e.playUri)
                        .put("pos", e.position).put("dur", e.duration).put("time", e.time).put("hidden", e.hiddenFromHome));
            } catch (JSONException ignored) {
            }
        }
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }
}
