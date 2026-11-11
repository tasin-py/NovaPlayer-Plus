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
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes a few stretches of a video's sound track with Android's own decoders and turns them
 * into speech-band loudness every 10 ms (for {@link SubSyncMatcher}). Only reads what it needs:
 * it seeks to each stretch, so a network video is not downloaded whole.
 */
final class SpeechSampler {

    private static final Logger log = LoggerFactory.getLogger(SpeechSampler.class);
    private static final long TIMEOUT_US = 10_000;

    interface Progress {
        void onWindow(int done, int total);
        boolean cancelled();
    }

    static final class NoDecoderException extends IOException {
        NoDecoderException(String mime) {
            super("no decoder for " + mime);
        }
    }

    private SpeechSampler() {}

    /**
     * @param startsMs where each stretch starts (video time)
     * @param lengthMs how long each stretch is
     */
    static List<SubSyncMatcher.Window> sample(Context context, Uri media, int[] startsMs, int lengthMs,
                                              Progress progress) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            if ("file".equals(media.getScheme()) || media.getScheme() == null) {
                extractor.setDataSource(media.getPath());
            } else {
                extractor.setDataSource(context, media, null);
            }
            int track = -1;
            MediaFormat format = null;
            String firstMime = null;
            MediaCodecList codecs = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) continue;
                if (firstMime == null) firstMime = mime;
                if (codecs.findDecoderForFormat(f) != null) {
                    track = i;
                    format = f;
                    break;
                }
            }
            if (track < 0) throw new NoDecoderException(firstMime == null ? "none" : firstMime);
            extractor.selectTrack(track);
            String mime = format.getString(MediaFormat.KEY_MIME);
            codec = MediaCodec.createByCodecName(codecs.findDecoderForFormat(format));
            codec.configure(format, null, null, 0);
            codec.start();
            if (log.isDebugEnabled()) log.debug("sample: track {} {} with {}", track, mime, codec.getName());

            List<SubSyncMatcher.Window> windows = new ArrayList<>();
            for (int w = 0; w < startsMs.length; w++) {
                if (progress.cancelled()) return windows;
                SubSyncMatcher.Window win = decodeWindow(extractor, codec, format, startsMs[w], lengthMs, progress);
                if (win != null) windows.add(win);
                progress.onWindow(w + 1, startsMs.length);
            }
            return windows;
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (RuntimeException ignored) {
                }
                codec.release();
            }
            extractor.release();
        }
    }

    private static SubSyncMatcher.Window decodeWindow(MediaExtractor extractor, MediaCodec codec, MediaFormat inFormat,
                                                      int startMs, int lengthMs, Progress progress) {
        int steps = lengthMs / SubSyncMatcher.STEP_MS;
        double[] energy = new double[steps];
        int[] count = new int[steps];
        long startUs = startMs * 1000L, endUs = (startMs + lengthMs) * 1000L;

        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        codec.flush();
        int sampleRate = inFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 48000;
        int channels = inFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
        boolean isFloat = false;
        BandPass filter = new BandPass(sampleRate);
        boolean inputDone = false;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long lastOutUs = -1;
        int idle = 0;

        while (!progress.cancelled()) {
            if (!inputDone) {
                int in = codec.dequeueInputBuffer(TIMEOUT_US);
                if (in >= 0) {
                    ByteBuffer buf = codec.getInputBuffer(in);
                    int size = buf == null ? -1 : extractor.readSampleData(buf, 0);
                    long t = extractor.getSampleTime();
                    if (size < 0 || t < 0 || t > endUs + 500_000) {
                        codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        codec.queueInputBuffer(in, 0, size, t, 0);
                        extractor.advance();
                    }
                }
            }
            int out = codec.dequeueOutputBuffer(info, TIMEOUT_US);
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat f = codec.getOutputFormat();
                if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    filter = new BandPass(sampleRate);
                }
                if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING)
                        && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT;
                continue;
            }
            if (out < 0) {
                if (inputDone && ++idle > 200) break; // decoder stuck: use what we have
                continue;
            }
            idle = 0;
            ByteBuffer pcm = codec.getOutputBuffer(out);
            if (pcm != null && info.size > 0 && info.presentationTimeUs + 1_000_000 >= startUs) {
                pcm.position(info.offset);
                pcm.limit(info.offset + info.size);
                pcm.order(ByteOrder.nativeOrder());
                int bytesPerSample = isFloat ? 4 : 2;
                int frames = info.size / (bytesPerSample * Math.max(1, channels));
                for (int i = 0; i < frames; i++) {
                    float mono = 0;
                    for (int c = 0; c < channels; c++) {
                        mono += isFloat ? pcm.getFloat() : pcm.getShort() / 32768f;
                    }
                    mono /= channels;
                    float y = filter.process(mono);
                    long tUs = info.presentationTimeUs + i * 1_000_000L / sampleRate;
                    int step = (int) ((tUs - startUs) / (SubSyncMatcher.STEP_MS * 1000L));
                    if (step >= 0 && step < steps) {
                        energy[step] += y * y;
                        count[step]++;
                    }
                }
                lastOutUs = info.presentationTimeUs;
            }
            codec.releaseOutputBuffer(out, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || lastOutUs > endUs) break;
        }
        // Loudness in dB-like units; steps with no samples (gaps) take the window's median
        float[] loud = new float[steps];
        List<Float> seen = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            if (count[i] > 0) {
                loud[i] = (float) (10 * Math.log10(energy[i] / count[i] + 1e-10));
                seen.add(loud[i]);
            } else {
                loud[i] = Float.NaN;
            }
        }
        if (seen.size() < steps / 4) return null; // almost nothing decoded
        java.util.Collections.sort(seen);
        float median = seen.get(seen.size() / 2);
        float q90 = seen.get((int) (seen.size() * 0.9));
        float q10 = seen.get((int) (seen.size() * 0.1));
        float spread = Math.max(3f, q90 - q10);
        for (int i = 0; i < steps; i++) {
            float v = Float.isNaN(loud[i]) ? median : loud[i];
            // relative to the window's own level, clipped so a few explosions don't dominate
            loud[i] = Math.max(-1.5f, Math.min(1.5f, (v - median) / spread * 2f));
        }
        return new SubSyncMatcher.Window(startMs, loud);
    }

    /** Keeps roughly the speech band (about 250 Hz to 3.4 kHz): 2nd-order high-pass then low-pass. */
    static final class BandPass {
        private final Biquad hp, lp;

        BandPass(int sampleRate) {
            hp = Biquad.highPass(sampleRate, 250);
            lp = Biquad.lowPass(sampleRate, 3400);
        }

        float process(float x) {
            return lp.process(hp.process(x));
        }
    }

    static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        static Biquad lowPass(int fs, double f) {
            double w = 2 * Math.PI * Math.min(f, fs * 0.45) / fs, cos = Math.cos(w), alpha = Math.sin(w) / Math.sqrt(2);
            return new Biquad((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        static Biquad highPass(int fs, double f) {
            double w = 2 * Math.PI * f / fs, cos = Math.cos(w), alpha = Math.sin(w) / Math.sqrt(2);
            return new Biquad((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        float process(float x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return (float) y;
        }
    }
}
