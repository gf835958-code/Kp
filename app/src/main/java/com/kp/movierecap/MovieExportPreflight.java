package com.kp.movierecap;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.StatFs;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Verifies the REAL selected source and output-space prerequisites before
 * expensive speech synthesis / Media3 encoding. No cloud, permissions or API key.
 * This is a reliability gate, not a movie comprehension engine.
 */
public final class MovieExportPreflight {
    private MovieExportPreflight() {}

    public static void verify(Context context, Uri movie,
            List<VideoHighlightsExporter.Range> ranges) throws Exception {
        if (movie == null || ranges == null || ranges.isEmpty() || ranges.size() > 16)
            throw new IllegalArgumentException("Video အပိုင်းအခြား မမှန်ပါ။");
        long durationMs;
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(context, movie);
            String duration = metadata.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION);
            String width = metadata.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = metadata.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (duration == null || width == null || height == null)
                throw new IOException("Video ကြာချိန် သို့မဟုတ် ရုပ်ပုံ မဖတ်နိုင်ပါ။");
            durationMs = Long.parseLong(duration);
            if (durationMs < 1000 || Integer.parseInt(width) <= 0 ||
                    Integer.parseInt(height) <= 0)
                throw new IOException("ပျက်နေသော Video သို့မဟုတ် Video အလွန်တိုနေပါသည်။");
        } finally {
            metadata.release();
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, movie, null);
            boolean hasVideo = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat track = extractor.getTrackFormat(i);
                String mime = track.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) hasVideo = true;
            }
            if (!hasVideo) throw new IOException("ရွေးထားသော ဖိုင်မှာ Video Track မရှိပါ။");
        } finally {
            extractor.release();
        }

        long previousEnd = 0;
        long selectedMicros = 0;
        final long maximumUs = durationMs * 1000L + 200000L;
        for (VideoHighlightsExporter.Range range : ranges) {
            if (range == null || range.startUs < previousEnd ||
                    range.endUs <= range.startUs || range.endUs > maximumUs ||
                    range.endUs - range.startUs > 15001000L)
                throw new IllegalArgumentException(
                    "Video အပိုင်းများ ထပ်နေခြင်း၊ အစဉ်မကျခြင်း သို့မဟုတ် အချိန်လွန်ခြင်း ရှိပါသည်။");
            selectedMicros += range.endUs - range.startUs;
            previousEnd = range.endUs;
        }

        // Two copies exist temporarily: the encoded scratch MP4 and the
        // MediaStore publication. Use a conservative estimate, not a promise
        // that storage is sufficient for every bitrate or hardware codec.
        long estimatedBytes = Math.max(64000000L,
            (long) Math.ceil(selectedMicros / 1000000.0 * 3000000.0));
        ensureFree(context.getCacheDir(), estimatedBytes);
        File external = context.getExternalFilesDir(null);
        if (external != null) ensureFree(external, estimatedBytes);
    }

    /** A saved recap is only successful when it is a readable MP4 with
     * BOTH an encoded video track and the Burmese narration audio track. */
    public static void verifyOutput(File mp4) throws Exception {
        if (mp4 == null || !mp4.isFile() || mp4.length() < 4096)
            throw new IOException("MP4 Video ဖိုင်မပြည့်စုံပါ။");
        MediaExtractor result = new MediaExtractor();
        try {
            result.setDataSource(mp4.getAbsolutePath());
            boolean video = false, audio = false;
            for (int i = 0; i < result.getTrackCount(); i++) {
                MediaFormat track = result.getTrackFormat(i);
                String mime = track.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (mime.startsWith("video/")) video = true;
                if (mime.startsWith("audio/")) audio = true;
            }
            if (!video || !audio)
                throw new IOException("ထုတ်ထားသော MP4 မှာ Video သို့မဟုတ် မြန်မာအသံ မပါပါ။");
        } finally {
            result.release();
        }
    }

    private static void ensureFree(File directory, long bytesNeeded) throws IOException {
        if (directory == null || !directory.exists())
            throw new IOException("Video သိမ်းမည့် Storage မရပါ။");
        long available = new StatFs(directory.getAbsolutePath()).getAvailableBytes();
        if (available < bytesNeeded)
            throw new IOException(
                "ဖုန်း Storage နေရာမလုံလောက်ပါ။ နေရာလွတ်ရှင်းပြီး ပြန်လုပ်ပါ။");
    }
}
