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

import android.net.Uri;
import android.util.Base64;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns whatever the user pasted into something the player can open:
 * plain http(s) file links, HLS/DASH, rtsp/rtmp/udp, and share links from
 * Google Drive, Dropbox and OneDrive (converted to direct download links).
 */
public final class StreamLink {

    public enum Kind { DIRECT, GOOGLE_DRIVE, DROPBOX, ONEDRIVE }

    /** What the user typed (trimmed). Used as the history key. */
    public final String original;
    /** What is actually handed to the player. */
    public final Uri playUri;
    public final String mimeType;
    public final Kind kind;
    /** Best name we can guess without network access. */
    public final String guessedName;

    private StreamLink(String original, Uri playUri, String mimeType, Kind kind, String guessedName) {
        this.original = original;
        this.playUri = playUri;
        this.mimeType = mimeType;
        this.kind = kind;
        this.guessedName = guessedName;
    }

    private static final java.util.Set<String> ALLOWED_SCHEMES = new java.util.HashSet<>(java.util.Arrays.asList(
            "http", "https", "rtsp", "rtmp", "rtmps", "rtp", "udp", "mms", "mmsh", "ftp"));
    private static final Pattern DRIVE_FILE = Pattern.compile("/file/d/([A-Za-z0-9_-]{10,})");
    private static final Pattern URL_IN_TEXT = Pattern.compile("(?i)\\b((?:https?|rtsp|rtmps?|rtp|udp|mms|mmsh|ftp)://\\S+)");

    /** @return null when the text does not look like a playable link. */
    public static StreamLink parse(String text) {
        if (text == null) return null;
        String input = text.trim();
        if (input.isEmpty()) return null;

        // Pasted text may contain more than the link ("Watch this: https://...")
        Matcher m = URL_IN_TEXT.matcher(input);
        if (m.find()) input = m.group(1);
        else if (!input.contains("://")) {
            if (!input.contains(".") || input.contains(" ")) return null;
            input = "http://" + input;
        }

        Uri uri = Uri.parse(input);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getHost().isEmpty()) return null;
        // Network streams only: never hand file://, content:// or intent: links to the player
        if (!ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))) return null;
        String host = uri.getHost().toLowerCase(Locale.ROOT);

        // Google Drive
        if (host.equals("drive.google.com") || host.equals("docs.google.com") || host.equals("drive.usercontent.google.com")) {
            String id = driveId(uri);
            if (id != null) {
                Uri direct = Uri.parse("https://drive.usercontent.google.com/download?id=" + id + "&export=download&confirm=t");
                return new StreamLink(input, direct, "video/*", Kind.GOOGLE_DRIVE, "Google Drive video");
            }
        }

        // Dropbox: dl=0 shows a web page, dl=1 sends the file
        if (host.equals("dropbox.com") || host.endsWith(".dropbox.com")) {
            Uri.Builder b = uri.buildUpon().clearQuery();
            boolean hasDl = false;
            for (String name : uri.getQueryParameterNames()) {
                if (name.equals("dl")) {
                    b.appendQueryParameter("dl", "1");
                    hasDl = true;
                } else {
                    for (String v : uri.getQueryParameters(name)) b.appendQueryParameter(name, v);
                }
            }
            if (!hasDl) b.appendQueryParameter("dl", "1");
            Uri direct = b.build();
            return new StreamLink(input, direct, mimeFor(uri), Kind.DROPBOX, nameFromPath(uri, "Dropbox video"));
        }

        // OneDrive personal share links
        if (host.equals("1drv.ms") || host.equals("onedrive.live.com")) {
            String token = "u!" + Base64.encodeToString(input.getBytes(StandardCharsets.UTF_8),
                    Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            Uri direct = Uri.parse("https://api.onedrive.com/v1.0/shares/" + token + "/root/content");
            return new StreamLink(input, direct, "video/*", Kind.ONEDRIVE, "OneDrive video");
        }

        return new StreamLink(input, uri, mimeFor(uri), Kind.DIRECT, nameFromPath(uri, host));
    }

    /** True for links that point at a folder listing rather than a file (e.g. ".../Movies/"). */
    public boolean looksLikeFolder() {
        if (kind != Kind.DIRECT) return false;
        String scheme = playUri.getScheme();
        if (scheme == null || !scheme.toLowerCase(Locale.ROOT).startsWith("http")) return false;
        String path = playUri.getPath();
        return path != null && path.length() > 1 && path.endsWith("/") && playUri.getQuery() == null;
    }

    static String fileNameFromContentDisposition(String cd) {
        if (cd == null) return null;
        Matcher star = Pattern.compile("(?i)filename\\*\\s*=\\s*(?:UTF-8|utf-8)''([^;]+)").matcher(cd);
        if (star.find()) return decode(star.group(1).trim());
        Matcher plain = Pattern.compile("(?i)filename\\s*=\\s*\"?([^\";]+)\"?").matcher(cd);
        if (plain.find()) return plain.group(1).trim();
        return null;
    }

    private static String driveId(Uri uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        Matcher m = DRIVE_FILE.matcher(path);
        if (m.find()) return m.group(1);
        String id = uri.getQueryParameter("id");
        return (id != null && !id.isEmpty()) ? id : null;
    }

    private static String nameFromPath(Uri uri, String fallback) {
        String last = uri.getLastPathSegment(); // already decoded by Uri
        if (last == null || last.trim().isEmpty()) return fallback;
        String name = stripExtension(last.trim());
        return (name == null || name.isEmpty()) ? fallback : name;
    }

    private static String stripExtension(String name) {
        if (name == null) return null;
        String ext = extension(name);
        return ext == null ? name : name.substring(0, name.length() - ext.length() - 1);
    }

    private static String extension(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return null;
        String ext = name.substring(dot + 1);
        return (ext.length() <= 5 && ext.matches("[A-Za-z0-9]+")) ? ext.toLowerCase(Locale.ROOT) : null;
    }

    private static String mimeFor(Uri uri) {
        String ext = extension(uri.getLastPathSegment());
        if (ext == null) return "video/*";
        switch (ext) {
            case "m3u8": case "m3u": return "application/x-mpegurl";
            case "mpd": return "application/dash+xml";
            case "mp4": case "m4v": return "video/mp4";
            case "mkv": return "video/x-matroska";
            case "webm": return "video/webm";
            case "avi": return "video/avi";
            case "ts": case "m2ts": case "mts": return "video/mp2t";
            case "mov": return "video/quicktime";
            default: return "video/*";
        }
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return s;
        }
    }
}
