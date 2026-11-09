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
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Network side of the Stream screen: finds out whether a link is a folder or a video,
 * reads folder listings and the video's size/name. Blocking: call off the UI thread.
 */
final class StreamNet {

    private static final int MAX_HTML = 5 * 1024 * 1024;
    private static final Pattern CONTENT_RANGE_TOTAL = Pattern.compile("/(\\d+)\\s*$");
    private static final Pattern CHARSET = Pattern.compile("(?i)charset=\\s*\"?([\\w.:-]+)");

    static final class Result {
        boolean folder;
        String finalUrl;
        List<FolderParser.Item> items;
        long size = -1;
        String fileName;
        String error;
    }

    private StreamNet() {}

    /** Works out what an http(s) link points to. Non-http links are always treated as videos. */
    static Result probe(StreamLink link) {
        Result r = new Result();
        r.finalUrl = link.playUri.toString();
        if (!isHttp(r.finalUrl)) return r;
        HttpURLConnection c = null;
        try {
            c = open(r.finalUrl, true);
            r.finalUrl = c.getURL().toString();
            String type = c.getContentType() == null ? "" : c.getContentType().toLowerCase(Locale.ROOT);
            // Share links (Drive, Dropbox…) may answer with an HTML page: let the player try them anyway
            if (type.startsWith("text/html") && link.kind == StreamLink.Kind.DIRECT) {
                boolean partial = c.getResponseCode() == HttpURLConnection.HTTP_PARTIAL;
                String html = partial ? null : readText(c);
                c.disconnect();
                c = null;
                return listFolder(r.finalUrl, html);
            }
            r.size = totalSize(c);
            r.fileName = StreamLink.fileNameFromContentDisposition(c.getHeaderField("Content-Disposition"));
        } catch (Exception e) {
            r.error = message(e);
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    /** Reads a folder listing. */
    static Result listFolder(String url) {
        return listFolder(url, null);
    }

    private static Result listFolder(String url, String alreadyRead) {
        Result r = new Result();
        r.folder = true;
        r.finalUrl = url;
        HttpURLConnection c = null;
        try {
            String html = alreadyRead;
            if (html == null) {
                c = open(url, false);
                r.finalUrl = c.getURL().toString();
                html = readText(c);
            }
            r.items = FolderParser.parse(html, r.finalUrl);
        } catch (Exception e) {
            r.error = message(e);
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    static boolean isHttp(String url) {
        String l = url.toLowerCase(Locale.ROOT);
        return l.startsWith("http://") || l.startsWith("https://");
    }

    private static HttpURLConnection open(String url, boolean firstByteOnly) throws IOException {
        String current = url;
        for (int i = 0; i < 6; i++) {
            URL u = new URL(current);
            if (!isHttp(current)) throw new IOException("Unsupported redirect");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(false); // followed below, also between http and https
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            if (firstByteOnly) c.setRequestProperty("Range", "bytes=0-0");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) throw new IOException("HTTP " + code);
                current = FolderParser.encodeUnsafe(new URL(u, loc).toString());
                continue;
            }
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                c.disconnect();
                throw new IOException("HTTP " + code);
            }
            return c;
        }
        throw new IOException("Too many redirects");
    }

    private static long totalSize(HttpURLConnection c) {
        String range = c.getHeaderField("Content-Range");
        if (range != null) {
            Matcher m = CONTENT_RANGE_TOTAL.matcher(range);
            if (m.find()) {
                try {
                    return Long.parseLong(m.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        try {
            if (c.getResponseCode() == HttpURLConnection.HTTP_OK) {
                String len = c.getHeaderField("Content-Length");
                if (len != null) return Long.parseLong(len.trim());
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static String readText(HttpURLConnection c) throws IOException {
        Charset cs = StandardCharsets.UTF_8;
        String type = c.getContentType();
        if (type != null) {
            Matcher m = CHARSET.matcher(type);
            if (m.find()) {
                try {
                    cs = Charset.forName(m.group(1));
                } catch (Exception ignored) {
                }
            }
        }
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_HTML) break; // huge listing: show what we have
            }
            return new String(out.toByteArray(), cs);
        }
    }

    private static String message(Exception e) {
        String m = e.getMessage();
        if (e instanceof java.net.SocketTimeoutException) return "timed out";
        if (e instanceof java.net.UnknownHostException) return "server not found";
        if (e instanceof java.net.ConnectException) return "server not reachable";
        return m == null || m.isEmpty() ? e.getClass().getSimpleName() : m;
    }
}
