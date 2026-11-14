package com.kp.recapstudio;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Multiplexes an authorized local video with an independently supplied
 * AAC/M4A narration track without network access. This is not AI dubbing.
 *
 * Video is copied without re-encoding; audio is copied without re-encoding.
 * Narration starts at time zero and is clipped to the video's duration.
 * The original soundtrack is intentionally omitted in this first version.
 */
public final class OfflineNarratedMuxer {
    public interface Progress { void onProgress(String message); }
    private OfflineNarratedMuxer() {}

    public static void export(Context context, Uri video, Uri narration, Uri output,
                              Progress progress) throws IOException {
        if (video == null || narration == null || output == null) {
            throw new IOException("Video, narration and output file are required.");
        }
        MediaExtractor videoEx = new MediaExtractor();
        MediaExtractor audioEx = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean muxStarted = false;
        try {
            setDataSource(videoEx, context, video);
            setDataSource(audioEx, context, narration);
            int videoTrack = findTrack(videoEx, "video/");
            int audioTrack = findTrack(audioEx, "audio/");
            if (videoTrack < 0 || audioTrack < 0) {
                throw new IOException("Video and audio tracks required.");
            }
            MediaFormat videoFormat = videoEx.getTrackFormat(videoTrack);
            MediaFormat audioFormat = audioEx.getTrackFormat(audioTrack);
            String videoMime = videoFormat.getString(MediaFormat.KEY_MIME);
            String audioMime = audioFormat.getString(MediaFormat.KEY_MIME);
            if (!("video/avc".equals(videoMime) || "video/hevc".equals(videoMime))) {
                throw new IOException("Video must be MP4 H.264/H.265.");
            }
            if (!"audio/mp4a-latm".equals(audioMime)) {
                throw new IOException("Narration must be AAC/M4A, or generate a WAV in Audiobook first.");
            }
            long videoDurationUs = videoFormat.containsKey(MediaFormat.KEY_DURATION)
                    ? videoFormat.getLong(MediaFormat.KEY_DURATION) : -1;
            videoEx.selectTrack(videoTrack);
            audioEx.selectTrack(audioTrack);

            try (ParcelFileDescriptor target =
                     context.getContentResolver().openFileDescriptor(output, "rwt")) {
                if (target == null) throw new IOException("Cannot write selected output MP4.");
                muxer = new MediaMuxer(target.getFileDescriptor(),
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
                    int angle = videoFormat.getInteger(MediaFormat.KEY_ROTATION);
                    if (angle == 0 || angle == 90 || angle == 180 || angle == 270) {
                        muxer.setOrientationHint(angle);
                    }
                }
                int videoOut = muxer.addTrack(videoFormat);
                int audioOut = muxer.addTrack(audioFormat);
                muxer.start();
                muxStarted = true;

                long videoEnd = copyTrack(videoEx, muxer, videoOut, videoDurationUs,
                        "video", progress);
                long cap = videoDurationUs > 0 ? videoDurationUs : Math.max(1, videoEnd);
                copyTrack(audioEx, muxer, audioOut, cap, "audio", progress);
                muxer.stop();
                muxStarted = false;
            }
        } catch (RuntimeException e) {
            throw new IOException("MP4 mux failed: " + e.getMessage(), e);
        } finally {
            if (muxer != null) {
                if (muxStarted) try { muxer.stop(); } catch (Exception ignored) {}
                try { muxer.release(); } catch (Exception ignored) {}
            }
            videoEx.release();
            audioEx.release();
        }
    }

    private static void setDataSource(MediaExtractor ex, Context ctx, Uri uri) throws IOException {
        if ("file".equals(uri.getScheme())) {
            ex.setDataSource(uri.getPath());
        } else {
            ex.setDataSource(ctx, uri, null);
        }
    }
    private static int findTrack(MediaExtractor extractor, String prefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) return i;
        }
        return -1;
    }
    private static long copyTrack(MediaExtractor extractor, MediaMuxer muxer, int track,
                                  long durationCapUs, String label, Progress progress)
                                  throws IOException {
        ByteBuffer data = ByteBuffer.allocateDirect(16 * 1024 * 1024);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long firstTime = -1, lastTime = -1, count = 0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Export interrupted");
            long sampleTime = extractor.getSampleTime();
            if (sampleTime < 0) break;
            if (firstTime < 0) firstTime = sampleTime;
            long pts = Math.max(0, sampleTime - firstTime);
            if (durationCapUs > 0 && pts > durationCapUs) break;
            data.clear();
            int n = extractor.readSampleData(data, 0);
            if (n < 0) break;
            if (n > data.capacity()) throw new IOException("Media sample too large");
            if (pts <= lastTime) pts = lastTime + 1;
            int flags = extractor.getSampleFlags() & MediaCodec.BUFFER_FLAG_KEY_FRAME;
            info.set(0, n, pts, flags);
            data.position(0); data.limit(n);
            muxer.writeSampleData(track, data, info);
            lastTime = pts;
            count++;
            if (count % 180 == 0 && progress != null) {
                progress.onProgress(String.format(Locale.US,
                        "Mux %s: %.1f seconds", label, pts / 1e6));
            }
            if (!extractor.advance()) break;
        }
        if (count == 0) throw new IOException("No " + label + " samples for mux");
        if (progress != null) progress.onProgress("Mux " + label + " completed");
        return lastTime;
    }
}
