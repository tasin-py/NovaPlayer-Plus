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

package com.archos.mediacenter.video.prime.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.archos.mediacenter.video.BuildConfig;
import com.archos.mediacenter.video.prime.PrimeConfig;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Checks the fork's GitHub Releases for a newer build and installs it in one click.
 *
 * Releases are tagged "prime-&lt;versionCode&gt;" by the GitHub Actions workflow and carry one APK per ABI.
 * The APK is streamed straight into an Android PackageInstaller session (no file left behind) and
 * committed with "user action not required", so on Android 12+ updates after the first one install
 * without any confirmation screen. Android itself may still ask once (first self-update, or the
 * one-time "allow installs from this app" switch); older Android versions always show the system
 * confirmation, which apps cannot bypass.
 */
public final class PrimeUpdater {

    private static final Logger log = LoggerFactory.getLogger(PrimeUpdater.class);

    public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, INSTALLING, ERROR }

    public interface Listener {
        void onUpdateStateChanged(PrimeUpdater updater);
    }

    private static final String PREFS = "prime_updater";
    private static final String KEY_LAST_CHECK = "last_check";
    private static final String KEY_AVAILABLE_CODE = "available_code";
    private static final String KEY_AVAILABLE_NAME = "available_name";
    private static final String KEY_AVAILABLE_URL = "available_url";
    private static final String KEY_AVAILABLE_SIZE = "available_size";
    private static final int MAX_API_RESPONSE = 2 * 1024 * 1024;
    private static final long AUTO_CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(6);

    static final String ACTION_INSTALL_STATUS = "com.archos.mediacenter.video.prime.INSTALL_STATUS";

    private static PrimeUpdater sInstance;

    private final Context mContext;
    private final SharedPreferences mPrefs;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();

    private volatile State mState = State.IDLE;
    private volatile int mProgress = 0;
    private volatile String mError = null;

    public static synchronized PrimeUpdater get(Context context) {
        if (sInstance == null) sInstance = new PrimeUpdater(context.getApplicationContext());
        return sInstance;
    }

    private PrimeUpdater(Context context) {
        mContext = context;
        mPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Forget a stored update once it is installed
        if (mPrefs.getLong(KEY_AVAILABLE_CODE, 0) <= BuildConfig.VERSION_CODE) clearAvailable();
        else mState = State.AVAILABLE;
    }

    // ---------------------------------------------------------------- state

    public State getState() { return mState; }
    public int getProgress() { return mProgress; }
    public String getError() { return mError; }
    public String getAvailableVersionName() { return mPrefs.getString(KEY_AVAILABLE_NAME, ""); }

    /** True when a newer release is known: drives the red dot on the Settings icon. */
    public boolean isUpdateAvailable() {
        return mPrefs.getLong(KEY_AVAILABLE_CODE, 0) > BuildConfig.VERSION_CODE;
    }

    public void addListener(Listener l) { mListeners.addIfAbsent(l); }
    public void removeListener(Listener l) { mListeners.remove(l); }

    private void setState(State state) {
        mState = state;
        mMain.post(() -> {
            for (Listener l : mListeners) l.onUpdateStateChanged(this);
        });
    }

    // ---------------------------------------------------------------- check

    /** Background check, at most every 6 hours. Called when the home screen shows. */
    public void checkIfDue() {
        long last = mPrefs.getLong(KEY_LAST_CHECK, 0);
        if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) return;
        check();
    }

    /** Checks GitHub now (the Settings button). */
    public void check() {
        if (mState == State.CHECKING || mState == State.DOWNLOADING || mState == State.INSTALLING) return;
        mError = null;
        setState(State.CHECKING);
        mExecutor.execute(() -> {
            try {
                JSONObject release = new JSONObject(httpGetString(PrimeConfig.LATEST_RELEASE_API));
                mPrefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
                String tag = release.optString("tag_name", "");
                long code = parseVersionCode(tag);
                if (code <= BuildConfig.VERSION_CODE) {
                    clearAvailable();
                    setState(State.UP_TO_DATE);
                    return;
                }
                JSONObject asset = pickApk(release.optJSONArray("assets"));
                if (asset == null) {
                    clearAvailable();
                    fail("No APK in release " + tag);
                    return;
                }
                String name = release.optString("name", tag);
                mPrefs.edit()
                        .putLong(KEY_AVAILABLE_CODE, code)
                        .putString(KEY_AVAILABLE_NAME, name.isEmpty() ? tag : name)
                        .putString(KEY_AVAILABLE_URL, asset.optString("browser_download_url"))
                        .putLong(KEY_AVAILABLE_SIZE, asset.optLong("size", -1))
                        .apply();
                setState(State.AVAILABLE);
            } catch (Exception e) {
                log.warn("update check failed", e);
                fail(e.getMessage());
            }
        });
    }

    static long parseVersionCode(String tag) {
        if (tag == null || !tag.startsWith(PrimeConfig.RELEASE_TAG_PREFIX)) return -1;
        try {
            return Long.parseLong(tag.substring(PrimeConfig.RELEASE_TAG_PREFIX.length()).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Picks the APK built for this device's CPU (falls back to a universal APK). */
    private static JSONObject pickApk(JSONArray assets) {
        if (assets == null) return null;
        for (String abi : Build.SUPPORTED_ABIS) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                String n = a == null ? "" : a.optString("name", "").toLowerCase(Locale.ROOT);
                if (n.endsWith(".apk") && n.contains("-" + abi.toLowerCase(Locale.ROOT) + "-")) return a;
            }
        }
        JSONObject anyApk = null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            String n = a == null ? "" : a.optString("name", "").toLowerCase(Locale.ROOT);
            if (!n.endsWith(".apk")) continue;
            if (n.contains("universal")) return a;
            if (anyApk == null) anyApk = a;
        }
        return anyApk;
    }

    // ---------------------------------------------------------------- install

    /** Downloads and installs the available update. No dialogs from the app itself. */
    public void install() {
        if (!isUpdateAvailable()) return;
        if (mState == State.DOWNLOADING || mState == State.INSTALLING || mState == State.CHECKING) return;
        final String url = mPrefs.getString(KEY_AVAILABLE_URL, null);
        final long size = mPrefs.getLong(KEY_AVAILABLE_SIZE, -1);
        if (url == null || !url.startsWith("https://github.com/")) {
            check();
            return;
        }
        mProgress = 0;
        mError = null;
        setState(State.DOWNLOADING);
        mExecutor.execute(() -> {
            PackageInstaller installer = mContext.getPackageManager().getPackageInstaller();
            int sessionId = -1;
            PackageInstaller.Session session = null;
            HttpURLConnection c = null;
            try {
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(mContext.getPackageName());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                }
                if (size > 0) params.setSize(size);
                sessionId = installer.createSession(params);
                session = installer.openSession(sessionId);

                c = open(url);
                long header = -1;
                try {
                    String len = c.getHeaderField("Content-Length");
                    if (len != null) header = Long.parseLong(len.trim());
                } catch (NumberFormatException ignored) {
                }
                long total = header > 0 ? header : size;
                try (InputStream in = c.getInputStream();
                     OutputStream out = session.openWrite("base.apk", 0, total > 0 ? total : -1)) {
                    byte[] buf = new byte[64 * 1024];
                    long done = 0;
                    int n, lastPct = -1;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        done += n;
                        if (total > 0) {
                            int pct = (int) (done * 100 / total);
                            if (pct != lastPct) {
                                lastPct = pct;
                                mProgress = pct;
                                setState(State.DOWNLOADING);
                            }
                        }
                    }
                    session.fsync(out);
                }

                Intent status = new Intent(mContext, PrimeInstallReceiver.class).setAction(ACTION_INSTALL_STATUS);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
                PendingIntent pi = PendingIntent.getBroadcast(mContext, sessionId, status, flags);
                setState(State.INSTALLING);
                session.commit(pi.getIntentSender());
                session.close();
                session = null;
            } catch (Exception e) {
                log.error("update install failed", e);
                if (session != null) session.abandon();
                else if (sessionId != -1) {
                    try { installer.abandonSession(sessionId); } catch (Exception ignored) {}
                }
                fail(e.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    /** Called by {@link PrimeInstallReceiver} when Android reports the result. */
    void onInstallFinished(boolean success, String message) {
        if (success) {
            clearAvailable();
            setState(State.UP_TO_DATE);
        } else {
            fail(message);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** ERROR state; {@link #isUpdateAvailable()} still tells whether a retry would install. */
    private void fail(String message) {
        mError = message == null ? "" : message;
        setState(State.ERROR);
    }

    private void clearAvailable() {
        mPrefs.edit().remove(KEY_AVAILABLE_CODE).remove(KEY_AVAILABLE_NAME)
                .remove(KEY_AVAILABLE_URL).remove(KEY_AVAILABLE_SIZE).apply();
    }

    private static HttpURLConnection open(String url) throws Exception {
        // GitHub download links redirect to another host: follow manually to be safe
        String current = url;
        for (int i = 0; i < 5; i++) {
            URL u = new URL(current);
            // HTTPS only, on every hop (Android also checks the APK signature before installing)
            if (!"https".equalsIgnoreCase(u.getProtocol())) throw new Exception("Refusing non-HTTPS URL");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "NovaPlayer-Prime-Updater");
            c.setRequestProperty("Accept", url.contains("api.github.com") ? "application/vnd.github+json" : "*/*");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new Exception("Redirect without location");
                current = new URL(new URL(current), loc).toString();
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new Exception("HTTP " + code);
            }
            return c;
        }
        throw new Exception("Too many redirects");
    }

    private static String httpGetString(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_API_RESPONSE) throw new Exception("Response too large");
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }
}
