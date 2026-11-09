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

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a web server's folder listing (h5ai, nginx/Apache/IIS autoindex and similar)
 * and keeps the sub-folders and video files directly inside the folder.
 */
public final class FolderParser {

    public static final class Item {
        public final String url;
        public final String name;
        public final boolean folder;
        /** Size in bytes when the listing shows it, else -1. */
        public final long size;

        Item(String url, String name, boolean folder, long size) {
            this.url = url;
            this.name = name;
            this.folder = folder;
            this.size = size;
        }
    }

    static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mkv", "mp4", "m4v", "avi", "mov", "wmv", "flv", "webm", "ts", "m2ts", "mts",
            "mpg", "mpeg", "vob", "3gp", "ogv", "divx", "xvid", "rmvb", "rm", "asf", "m3u8"));

    private static final Pattern ANCHOR = Pattern.compile("(?is)<a\\s[^>]*?href\\s*=\\s*([\"'])(.*?)\\1[^>]*>.*?</a>");
    private static final Pattern SIZE_UNIT = Pattern.compile("(?i)(\\d+(?:[.,]\\d+)?)\\s?(B|KB|MB|GB|TB|KiB|MiB|GiB|TiB|K|M|G|T)\\s*$");
    private static final Pattern SIZE_BYTES = Pattern.compile("\\s(\\d{1,15})\\s*$");

    private FolderParser() {}

    /** @param baseUrl the folder's URL after redirects (should end with "/"). */
    public static List<Item> parse(String html, String baseUrl) {
        List<Item> folders = new ArrayList<>();
        List<Item> files = new ArrayList<>();
        if (html == null || baseUrl == null) return folders;
        URL base;
        try {
            base = new URL(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        } catch (Exception e) {
            return folders;
        }
        String basePath = percentDecode(base.getPath());
        Map<String, Item> seen = new LinkedHashMap<>();

        Matcher m = ANCHOR.matcher(html);
        while (m.find()) {
            String href = unescapeHtml(m.group(2).trim());
            if (href.isEmpty() || href.startsWith("#") || href.startsWith("?")
                    || href.toLowerCase(Locale.ROOT).startsWith("javascript:")
                    || href.toLowerCase(Locale.ROOT).startsWith("mailto:")) continue;
            URL u;
            try {
                u = new URL(base, href);
            } catch (Exception e) {
                continue;
            }
            if (!u.getProtocol().equalsIgnoreCase(base.getProtocol())
                    || !u.getHost().equalsIgnoreCase(base.getHost())
                    || u.getPort() != base.getPort()
                    || u.getQuery() != null) continue;
            String path = percentDecode(u.getPath());
            if (!path.startsWith(basePath) || path.length() == basePath.length()) continue;
            String rest = path.substring(basePath.length());
            boolean folder = rest.endsWith("/");
            String name = folder ? rest.substring(0, rest.length() - 1) : rest;
            if (name.isEmpty() || name.contains("/") || name.equals(".") || name.equals("..")) continue;
            if (!folder && !VIDEO_EXTENSIONS.contains(extension(name))) continue;

            String clean = encodeUnsafe(u.toString());
            int hash = clean.indexOf('#');
            if (hash >= 0) clean = clean.substring(0, hash);
            if (seen.containsKey(clean)) continue;

            // Text after the link on the same row usually holds date and size
            int end = html.length();
            int nextA = indexOfIgnoreCase(html, "<a ", m.end());
            int nextRow = indexOfIgnoreCase(html, "</tr", m.end());
            int nextLine = html.indexOf('\n', m.end());
            if (nextA >= 0) end = Math.min(end, nextA);
            if (nextRow >= 0) end = Math.min(end, nextRow);
            if (nextLine >= 0) end = Math.min(end, nextLine);
            long size = folder ? -1 : parseSize(html.substring(m.end(), end));

            Item item = new Item(clean, name, folder, size);
            seen.put(clean, item);
            (folder ? folders : files).add(item);
        }
        folders.addAll(files);
        return folders;
    }

    static long parseSize(String rowText) {
        String text = rowText.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").trim();
        if (text.isEmpty()) return -1;
        Matcher u = SIZE_UNIT.matcher(text);
        if (u.find()) {
            try {
                double v = Double.parseDouble(u.group(1).replace(',', '.'));
                String unit = u.group(2).toUpperCase(Locale.ROOT);
                // "KB/MB/GB" (h5ai and most listings) are decimal; "KiB" and Apache's bare "K/M/G" are binary
                double k = unit.length() == 2 ? 1000 : 1024;
                switch (unit.charAt(0)) {
                    case 'K': return (long) (v * k);
                    case 'M': return (long) (v * k * k);
                    case 'G': return (long) (v * k * k * k);
                    case 'T': return (long) (v * k * k * k * k);
                    default: return (long) v;
                }
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        Matcher b = SIZE_BYTES.matcher(" " + text);
        if (b.find()) {
            try {
                return Long.parseLong(b.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    /** "2.1 GB" style; empty for unknown. */
    public static String formatSize(long bytes) {
        if (bytes < 0) return "";
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int i = -1;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(Locale.US, v >= 100 ? "%.0f %s" : "%.1f %s", v, units[i]);
    }

    /** Escapes characters that are not allowed in a URL (servers sometimes redirect to raw spaces). */
    static String encodeUnsafe(String url) {
        StringBuilder b = new StringBuilder(url.length());
        for (byte x : url.getBytes(StandardCharsets.UTF_8)) {
            int ch = x & 0xff;
            if (ch <= 0x20 || ch >= 0x7f || "\"<>[]{}|\\^`".indexOf(ch) >= 0) {
                b.append('%').append(String.format(Locale.ROOT, "%02X", ch));
            } else {
                b.append((char) ch);
            }
        }
        return b.toString();
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Percent-decoding for URL paths ("+" stays "+"). */
    static String percentDecode(String s) {
        if (s == null || s.indexOf('%') < 0) return s == null ? "" : s;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] in = s.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < in.length; i++) {
            if (in[i] == '%' && i + 2 < in.length) {
                int hi = Character.digit(in[i + 1], 16);
                int lo = Character.digit(in[i + 2], 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) | lo);
                    i += 2;
                    continue;
                }
            }
            out.write(in[i]);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String unescapeHtml(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    private static int indexOfIgnoreCase(String s, String needle, int from) {
        int n = needle.length();
        for (int i = from; i <= s.length() - n; i++) {
            if (s.regionMatches(true, i, needle, 0, n)) return i;
        }
        return -1;
    }
}
