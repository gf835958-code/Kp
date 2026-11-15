package com.kp.recapstudio;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.File;
import java.io.IOException;

/**
 * Sanity checks exported local MP4 files before reporting them as successful.
 * Presence of tracks and samples is not proof of perfect decoding/playback,
 * but it catches empty, wrong-container, audio-less and video-less results.
 */
public final class MediaExportValidator {
    private MediaExportValidator() {}

    public static void verify(Context context, File file, boolean requireAudio)
            throws IOException {
        if (file == null || !file.isFile() || file.length() < 1024L)
            throw new IOException("Exported MP4 is missing or empty");
        verify(context, Uri.fromFile(file), requireAudio);
    }

    public static void verify(Context context, Uri uri, boolean requireAudio)
            throws IOException {
        if (context == null || uri == null) throw new IOException("Missing output to validate");
        MediaExtractor extractor = new MediaExtractor();
        try {
            if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                extractor.setDataSource(uri.getPath());
            } else {
                extractor.setDataSource(context, uri, null);
            }
            int videoTrack = -1;
            int audioTrack = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat track = extractor.getTrackFormat(i);
                String type = track.getString(MediaFormat.KEY_MIME);
                if (type == null) continue;
                if (type.startsWith("video/")) videoTrack = i;
                if (type.startsWith("audio/")) audioTrack = i;
            }
            if (videoTrack == -1)
                throw new IOException("MP4 validation failed: no video track");
            if (requireAudio && audioTrack == -1)
                throw new IOException("MP4 validation failed: narration/audio track absent");

            extractor.selectTrack(videoTrack);
            if (extractor.getSampleTime() < 0)
                throw new IOException("MP4 validation failed: no video frames");
            extractor.unselectTrack(videoTrack);

            if (requireAudio) {
                extractor.selectTrack(audioTrack);
                if (extractor.getSampleTime() < 0)
                    throw new IOException("MP4 validation failed: no audio samples");
            }
        } catch (RuntimeException e) {
            throw new IOException("MP4 validation failed: " + e.getMessage(), e);
        } finally {
            try { extractor.release(); } catch (RuntimeException ignored) {}
        }
    }
}
