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
import java.util.Collections;
import java.util.List;

/** Bookmarked stream folders and videos. Kept until the user removes them (no expiry). */
public final class StreamBookmarks {

    private static final String PREFS = "prime_stream_bookmarks";
    private static final String KEY = "entries";

    public static final class Entry {
        public final String url;
        public final String name;
        public final boolean folder;
        public final long added;

        Entry(String url, String name, boolean folder, long added) {
            this.url = url;
            this.name = name;
            this.folder = folder;
            this.added = added;
        }
    }

    private StreamBookmarks() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Sorted A to Z by name (ignoring case). */
    public static synchronized List<Entry> load(Context context) {
        List<Entry> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                String url = o.optString("url", "");
                if (url.isEmpty()) continue;
                result.add(new Entry(url, o.optString("name", url), o.optBoolean("folder", false), o.optLong("time", 0)));
            }
        } catch (JSONException ignored) {
            // unreadable: keep the stored text untouched rather than wiping bookmarks
        }
        Collections.sort(result, (a, b) -> {
            int c = a.name.compareToIgnoreCase(b.name);
            return c != 0 ? c : a.url.compareTo(b.url);
        });
        return result;
    }

    public static synchronized boolean contains(Context context, String url) {
        for (Entry e : load(context)) if (e.url.equals(url)) return true;
        return false;
    }

    public static synchronized void add(Context context, String url, String name, boolean folder) {
        List<Entry> entries = load(context);
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).url.equals(url)) entries.remove(i);
        }
        entries.add(0, new Entry(url, name == null || name.isEmpty() ? url : name, folder, System.currentTimeMillis()));
        save(context, entries);
    }

    public static synchronized void remove(Context context, String url) {
        List<Entry> entries = load(context);
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).url.equals(url)) entries.remove(i);
        }
        save(context, entries);
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry e : entries) {
            try {
                array.put(new JSONObject().put("url", e.url).put("name", e.name)
                        .put("folder", e.folder).put("time", e.added));
            } catch (JSONException ignored) {
            }
        }
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }
}
