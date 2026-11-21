package com.kp.movierecap;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Offline evidence from TEXT subtitle tracks embedded in a user-selected file.
 * Some MP4/MKV files have no text track and burned-in captions cannot be
 * extracted this way. Foreign text is evidence, NOT Burmese translation.
 * Unsupported closed-caption/binary codecs are deliberately ignored.
 */
public final class MovieSubtitleExtractor {
    private MovieSubtitleExtractor() {}

    public static JSONObject read(Context context, Uri movie, int maxCues) throws Exception {
        if (context == null || movie == null || maxCues < 1 || maxCues > 500)
            throw new IllegalArgumentException("Invalid subtitle extraction request");
        JSONArray cues = new JSONArray();
        String language = "", mime = "";
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, movie, null);
            int track = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat info = extractor.getTrackFormat(i);
                String candidate = info.getString(MediaFormat.KEY_MIME);
                if (!supported(candidate)) continue;
                String tag = info.containsKey(MediaFormat.KEY_LANGUAGE)
                    ? info.getString(MediaFormat.KEY_LANGUAGE) : "";
                if (tag == null) tag = "";
                // Burmese subtitles are usable as Burmese evidence without
                // inventing a translation; otherwise preserve original text.
                if (track < 0 || tag.toLowerCase(Locale.ROOT).startsWith("my")) {
                    track = i;
                    mime = candidate;
                    language = tag;
                }
                if (tag.toLowerCase(Locale.ROOT).startsWith("my")) break;
            }
            if (track >= 0) {
                extractor.selectTrack(track);
                ByteBuffer buffer = ByteBuffer.allocate(32768);
                double lastTime = -1;
                int seen = 0;
                while (seen < maxCues) {
                    buffer.clear();
                    int n = extractor.readSampleData(buffer, 0);
                    if (n < 0) break;
                    long micros = extractor.getSampleTime();
                    if (micros < 0) break;
                    String text = n == 0 || n >= buffer.capacity() ? ""
                        : cleanText(buffer.array(), n, mime);
                    double time = micros / 1000000.0;
                    if (!text.isEmpty() && time >= 0 && time >= lastTime) {
                        JSONObject cue = new JSONObject();
                        cue.put("time", time);
                        cue.put("lang", language);
                        cue.put("text", text);
                        cues.put(cue);
                        lastTime = time;
                        seen++;
                    }
                    if (!extractor.advance()) break;
                }
            }
        } finally {
            extractor.release();
        }
        JSONObject result = new JSONObject();
        result.put("cues", cues);
        result.put("language", language);
        result.put("format", mime);
        result.put("method", "offline embedded text subtitle samples");
        return result;
    }

    private static boolean supported(String mime) {
        return "application/x-subrip".equals(mime)
            || "text/vtt".equals(mime)
            || "text/plain".equals(mime)
            || "text/3gpp".equals(mime);
    }

    private static String cleanText(byte[] bytes, int size, String mime) {
        int offset = 0, count = size;
        if ("text/3gpp".equals(mime)) {
            // Timed 3GPP text samples begin with a 16-bit UTF-8 text length.
            if (size < 3) return "";
            count = ((bytes[0] & 255) << 8) | (bytes[1] & 255);
            if (count < 1 || count > size - 2) return "";
            offset = 2;
        }
        String text = new String(bytes, offset, count, StandardCharsets.UTF_8)
            .replace(String.valueOf((char) 0xFEFF), "")
            .replaceAll("(?m)^WEBVTT.*$", "")
            .replaceAll("(?m)^\\s*\\d+\\s*$", "")
            .replaceAll("(?m)^.*?-->.*$", "")
            .replaceAll("<[^>]{1,160}>", "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace((char) 0, ' ')
            .replaceAll("[\\r\\n\\t]+", " ")
            .replaceAll(" {2,}", " ")
            .trim();
        int letters = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) letters++;
            else if (Character.isISOControl(c)) return "";
        }
        return letters < 2 ? "" : text.substring(0, Math.min(220, text.length()));
    }
}
