package com.kp.recapstudio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Offline-only subtitle plan derived from edited narration text and measured
 * audio duration. These are ESTIMATED sentence-level timings, not forced ASR
 * alignment. No API calls and no cloud resources.
 */
public final class CaptionTimeline {
    public static final class Cue {
        public final String text;
        public final long startUs;
        public final long endUs;
        public Cue(String text, long startUs, long endUs) {
            this.text = text;
            this.startUs = startUs;
            this.endUs = endUs;
        }
    }

    private CaptionTimeline() {}

    /** Keep Burmese punctuation, whitespace and surrogate pairs intact. */
    public static List<String> split(String text, int limit) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();
        if (limit < 12) throw new IllegalArgumentException("limit too small");
        ArrayList<String> parts = new ArrayList<>();
        String source = text.replace("\r", "").trim();
        int offset = 0;
        while (offset < source.length()) {
            int end = Math.min(source.length(), offset + limit);
            if (end < source.length() && Character.isHighSurrogate(source.charAt(end - 1))) end--;
            if (end < source.length()) {
                int boundary = -1;
                for (int i = end - 1; i > offset + limit / 2; i--) {
                    char c = source.charAt(i);
                    if (Character.isWhitespace(c) || c == '။' || c == '၊' ||
                        c == '.' || c == ',' || c == '?' || c == '!') {
                        boundary = i + 1; break;
                    }
                }
                if (boundary > offset) end = boundary;
            }
            if (end <= offset) end = Math.min(offset + 1, source.length());
            String part = source.substring(offset, end).trim();
            if (!part.isEmpty()) parts.add(part);
            offset = end;
        }
        return parts;
    }

    public static List<Cue> plan(String text, long durationUs, int maxChars) {
        List<String> parts = split(text, maxChars);
        if (parts.isEmpty()) return Collections.emptyList();
        if (durationUs < 100_000L) {
            durationUs = Math.multiplyExact((long)parts.size(), 5_000_000L);
        }
        // Proportional character weights provide better estimates for long
        // captions than fixed 5-second slots. Not speech-to-word alignment.
        long total = 0;
        long[] weights = new long[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            weights[i] = Math.max(1, parts.get(i).codePointCount(0, parts.get(i).length()));
            total += weights[i];
        }
        List<Cue> cues = new ArrayList<>(parts.size());
        long prefixWeight = 0;
        for (int i = 0; i < parts.size(); i++) {
            long start = Math.multiplyExact(durationUs, prefixWeight) / total;
            prefixWeight += weights[i];
            long end = (i == parts.size() - 1)
                ? durationUs : Math.multiplyExact(durationUs, prefixWeight) / total;
            cues.add(new Cue(parts.get(i), start, Math.max(start + 1, end)));
        }
        return cues;
    }

    public static String toSrt(List<Cue> cues) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < cues.size(); i++) {
            Cue cue = cues.get(i);
            output.append(i + 1).append('\n')
                .append(srtTime(cue.startUs))
                .append(" --> ").append(srtTime(cue.endUs))
                .append('\n').append(cue.text).append("\n\n");
        }
        return output.toString();
    }

    public static String srtTime(long micros) {
        long millis = Math.max(0, micros / 1000L);
        return String.format(java.util.Locale.US,"%02d:%02d:%02d,%03d",
            millis / 3_600_000L, (millis / 60_000L) % 60L,
            (millis / 1_000L) % 60L, millis % 1000L);
    }
}
