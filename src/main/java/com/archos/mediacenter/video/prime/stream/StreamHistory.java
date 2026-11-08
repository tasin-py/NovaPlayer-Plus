// Copyright 2026 tasin-py (NovaPlayer Prime)
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
 * Remembers streamed links for {@link #RETENTION_MS} (30 days) after they were last played.
 * Stored in a private SharedPreferences file so it survives app restarts and TV reboots.
 */
public final class StreamHistory {

    public static final long RETENTION_MS = TimeUnit.DAYS.toMillis(30);
    private static final int MAX_ENTRIES = 50;

    private static final String PREFS = "prime_stream_history";
    private static final String KEY = "entries";

    public static final class Entry {
        public final String url;
        public final String name;
        public final long lastPlayed;

        Entry(String url, String name, long lastPlayed) {
            this.url = url;
            this.name = name;
            this.lastPlayed = lastPlayed;
        }
    }

    private StreamHistory() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Entries newest first, with anything older than 30 days dropped. */
    public static synchronized List<Entry> load(Context context) {
        List<Entry> result = new ArrayList<>();
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        boolean pruned = false;
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", "");
                long time = o.optLong("time", 0);
                if (url.isEmpty() || time < cutoff) {
                    pruned = true;
                    continue;
                }
                result.add(new Entry(url, o.optString("name", url), time));
            }
        } catch (JSONException e) {
            pruned = true; // corrupted: start fresh
        }
        if (pruned) save(context, result);
        return result;
    }

    /** Adds or refreshes a link and moves it to the top. */
    public static synchronized void add(Context context, String url, String name) {
        List<Entry> entries = load(context);
        String keepName = name;
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).url.equals(url)) {
                if (keepName == null || keepName.isEmpty()) keepName = entries.get(i).name;
                entries.remove(i);
            }
        }
        if (keepName == null || keepName.isEmpty()) keepName = url;
        entries.add(0, new Entry(url, keepName, System.currentTimeMillis()));
        while (entries.size() > MAX_ENTRIES) entries.remove(entries.size() - 1);
        save(context, entries);
    }

    /** Updates the display name of a link (used once the real file name is known). */
    public static synchronized void rename(Context context, String url, String name) {
        if (name == null || name.isEmpty()) return;
        List<Entry> entries = load(context);
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.url.equals(url)) entries.set(i, new Entry(e.url, name, e.lastPlayed));
        }
        save(context, entries);
    }

    public static synchronized void remove(Context context, String url) {
        List<Entry> entries = load(context);
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).url.equals(url)) entries.remove(i);
        }
        save(context, entries);
    }

    public static synchronized void clear(Context context) {
        prefs(context).edit().remove(KEY).apply();
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry e : entries) {
            try {
                array.put(new JSONObject().put("url", e.url).put("name", e.name).put("time", e.lastPlayed));
            } catch (JSONException ignored) {
            }
        }
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }
}
