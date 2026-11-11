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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads only the timings (start and end of each line) from SRT, WebVTT and ASS/SSA subtitles. */
public final class SubtitleCues {

    /** One subtitle line on screen, in milliseconds of the subtitle file's own clock. */
    public static final class Cue {
        public final int start;
        public final int end;

        public Cue(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    // SRT "00:01:02,345 --> 00:01:04,000", VTT "00:01:02.345 --> 00:01:04.000" or "01:02.345 --> 01:04.000"
    private static final Pattern ARROW = Pattern.compile(
            "(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})\\s*-->\\s*(?:(\\d{1,2}):)?(\\d{1,2}):(\\d{1,2})[,.](\\d{1,3})");
    // ASS/SSA "Dialogue: 0,0:01:02.34,0:01:04.00,Default,..." (SSA: "Dialogue: Marked=0,0:01:02.34,...")
    private static final Pattern DIALOGUE = Pattern.compile(
            "^\\s*Dialogue:\\s*[^,]*,\\s*(\\d+):(\\d{1,2}):(\\d{1,2})[.,](\\d{1,3})\\s*,\\s*(\\d+):(\\d{1,2}):(\\d{1,2})[.,](\\d{1,3})",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private SubtitleCues() {}

    /** Every cue found, sorted by start. Empty when the format is not a supported text format. */
    public static List<Cue> parse(String text) {
        List<Cue> cues = new ArrayList<>();
        if (text == null) return cues;
        Matcher d = DIALOGUE.matcher(text);
        while (d.find()) {
            add(cues, time(d.group(1), d.group(2), d.group(3), d.group(4)),
                    time(d.group(5), d.group(6), d.group(7), d.group(8)));
        }
        if (cues.isEmpty()) {
            Matcher a = ARROW.matcher(text);
            while (a.find()) {
                add(cues, time(a.group(1), a.group(2), a.group(3), a.group(4)),
                        time(a.group(5), a.group(6), a.group(7), a.group(8)));
            }
        }
        Collections.sort(cues, (x, y) -> Integer.compare(x.start, y.start));
        return cues;
    }

    private static void add(List<Cue> cues, int start, int end) {
        if (start < 0 || end <= start) return;
        if (end - start > 20_000) end = start + 20_000; // a cue held for minutes is a sign or a mistake
        cues.add(new Cue(start, end));
    }

    private static int time(String h, String m, String s, String frac) {
        try {
            int hours = h == null ? 0 : Integer.parseInt(h);
            int ms = Integer.parseInt(frac);
            if (frac.length() == 1) ms *= 100;
            else if (frac.length() == 2) ms *= 10; // ASS centiseconds
            return ((hours * 60 + Integer.parseInt(m)) * 60 + Integer.parseInt(s)) * 1000 + ms;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
