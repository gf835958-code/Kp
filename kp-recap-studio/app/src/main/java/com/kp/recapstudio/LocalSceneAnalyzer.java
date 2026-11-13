package com.kp.recapstudio;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Offline visual shot-change hints from locally accessible video.
 * This is NOT transcription, movie understanding or story generation.
 */
public final class LocalSceneAnalyzer {
    public static final class Scene {
        public final long timeMs;
        public final Bitmap thumbnail;
        public final double delta;
        Scene(long timeMs, Bitmap thumbnail, double delta) {
            this.timeMs = timeMs; this.thumbnail = thumbnail; this.delta = delta;
        }
    }

    public static final class Result {
        public final long durationMs;
        public final List<Scene> scenes;
        public final String warning;
        Result(long durationMs, List<Scene> scenes, String warning) {
            this.durationMs = durationMs;
            this.scenes = scenes;
            this.warning = warning;
        }
    }

    private LocalSceneAnalyzer() {}

    public static Result analyze(Context context, Uri uri) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long duration = Long.parseLong(value == null ? "0" : value);
            if (duration <= 0) throw new IOException("Video duration unavailable");
            final int samples = (int) Math.min(72L, Math.max(6L, duration / 2600L));
            final long interval = Math.max(1L, duration / samples);
            final ArrayList<Scene> candidates = new ArrayList<>();
            final ArrayList<Scene> fallback = new ArrayList<>();
            int[] last = null;
            int framesSeen = 0;
            for (int i = 0; i < samples; i++) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
                long time = Math.min(duration - 1, i * interval);
                Bitmap frame = retriever.getFrameAtTime(time * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if (frame == null) continue;
                Bitmap thumb = null;
                try {
                    thumb = Bitmap.createScaledBitmap(frame, 160, 90, true);
                    if (thumb == frame) thumb = frame.copy(Bitmap.Config.ARGB_8888, false);
                    if (thumb == null) continue;
                    int[] signature = signature(thumb);
                    double diff = last == null ? 0 : difference(last, signature);
                    Scene entry = new Scene(time, thumb, diff);
                    candidates.add(entry);
                    // Fallback sampled slots when there are no detectable shot changes.
                    if (framesSeen % Math.max(1, samples / 10) == 0) fallback.add(entry);
                    last = signature;
                    framesSeen++;
                    thumb = null; // ownership passed to candidate; do not recycle
                } finally {
                    if (thumb != null && thumb != frame) thumb.recycle();
                    if (frame != null) frame.recycle();
                }
            }
            if (candidates.isEmpty()) throw new IOException("No video thumbnails could be extracted");

            // Prioritize genuinely different visuals, then add evenly spread points.
            ArrayList<Scene> ranked = new ArrayList<>(candidates);
            ranked.sort((a, b) -> Double.compare(b.delta, a.delta));
            ArrayList<Scene> selected = new ArrayList<>();
            selected.add(candidates.get(0));
            long minGap = Math.max(1000L, duration / 30);
            for (Scene next : ranked) {
                if (selected.size() >= 12) break;
                if (next.delta < 15) continue;
                boolean near = false;
                for (Scene already : selected) if (Math.abs(next.timeMs - already.timeMs) < minGap) {
                    near = true; break;
                }
                if (!near) selected.add(next);
            }
            for (Scene next : fallback) {
                if (selected.size() >= 8) break;
                boolean near = false;
                for (Scene already : selected) if (Math.abs(next.timeMs - already.timeMs) < minGap) {
                    near = true; break;
                }
                if (!near) selected.add(next);
            }
            selected.sort(Comparator.comparingLong(a -> a.timeMs));
            for (Scene candidate : candidates) {
                if (!selected.contains(candidate)) candidate.thumbnail.recycle();
            }
            String warning = "Visual cues only — no video AI or speech transcription. "
                + "Shot hints may be approximate because keyframes are sampled.";
            return new Result(duration, selected, warning);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid video metadata", e);
        } catch (RuntimeException e) {
            throw new IOException("Could not analyze video frames: " + e.getMessage(), e);
        } finally {
            try { retriever.release(); } catch (RuntimeException ignored) {}
        }
    }

    private static int[] signature(Bitmap bitmap) {
        final int width = 12, height = 8;
        Bitmap small = Bitmap.createScaledBitmap(bitmap, width, height, true);
        int[] pixels = new int[width * height];
        small.getPixels(pixels, 0, width, 0, 0, width, height);
        int[] signature = new int[pixels.length * 3];
        for (int i = 0; i < pixels.length; i++) {
            signature[i * 3] = Color.red(pixels[i]);
            signature[i * 3 + 1] = Color.green(pixels[i]);
            signature[i * 3 + 2] = Color.blue(pixels[i]);
        }
        if (small != bitmap) small.recycle();
        return signature;
    }

    private static double difference(int[] a, int[] b) {
        double value = 0;
        for (int i = 0; i < a.length; i++) value += Math.abs(a[i] - b[i]);
        return value / a.length;
    }
}
