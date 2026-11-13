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
import java.util.List;
import java.util.Locale;

/**
 * Repackages short sync-frame-aligned clips from an authorized local video
 * into a silent MP4 scene montage. No AI, narration or subtitles are added.
 * Codec passthrough avoids a slow transcode on an Android-only device.
 *
 * Limitations: H.264/H.265 MP4 source only; each clip begins at a nearby
 * preceding keyframe, not frame-perfect cuts. Device codec support varies.
 */
public final class OfflineVideoMontage {
    private OfflineVideoMontage() {}

    public interface Progress {
        void onProgress(String message);
    }

    public static long write(Context context, Uri source, Uri destination,
                             List<LocalSceneAnalyzer.Scene> scenes,
                             long videoDurationMs, Progress progress) throws IOException {
        if (source == null || destination == null || scenes == null || scenes.isEmpty())
            throw new IOException("Video and scene timestamps are required");
        if (videoDurationMs <= 0) throw new IOException("Unknown video duration");
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean started = false;
        long totalSamples = 0;
        long outputPts = 0;
        try {
            extractor.setDataSource(context, source, null);
            int videoTrack = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    videoTrack = i; format = candidate; break;
                }
            }
            if (format == null) throw new IOException("Video track not found");
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!"video/avc".equals(mime) && !"video/hevc".equals(mime))
                throw new IOException("Only H.264 / H.265 MP4 source video is currently supported");

            extractor.selectTrack(videoTrack);
            try (ParcelFileDescriptor target = context.getContentResolver()
                    .openFileDescriptor(destination, "rwt")) {
                if (target == null) throw new IOException("Cannot create output MP4");
                muxer = new MediaMuxer(target.getFileDescriptor(),
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                if (format.containsKey(MediaFormat.KEY_ROTATION)) {
                    int angle = format.getInteger(MediaFormat.KEY_ROTATION);
                    if (angle == 0 || angle == 90 || angle == 180 || angle == 270)
                        muxer.setOrientationHint(angle);
                }
                int outTrack = muxer.addTrack(format);
                muxer.start();
                started = true;
                int frameCap = 6 * 1024 * 1024;
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    frameCap = Math.max(frameCap,
                        Math.min(24 * 1024 * 1024,
                            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) + 4096));
                }
                ByteBuffer buffer = ByteBuffer.allocateDirect(frameCap);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int clips = Math.min(scenes.size(), 12);
                for (int index = 0; index < clips; index++) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Export interrupted");
                    long startMs = Math.max(0L, scenes.get(index).timeMs);
                    long endMs = Math.min(videoDurationMs, startMs + 3500L);
                    if (endMs <= startMs) continue;
                    long startUs = startMs * 1000L;
                    long endUs = endMs * 1000L;
                    extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    long localStart = -1L;
                    long lastWritten = outputPts - 1;
                    int clipSamples = 0;
                    while (true) {
                        long inputTime = extractor.getSampleTime();
                        if (inputTime < 0 || inputTime > endUs) break;
                        int flags = extractor.getSampleFlags();
                        // Begin a new clip only on a decodable keyframe.
                        if (localStart < 0 && (flags & MediaExtractor.SAMPLE_FLAG_SYNC) == 0) {
                            if (!extractor.advance()) break;
                            continue;
                        }
                        buffer.clear();
                        int size = extractor.readSampleData(buffer, 0);
                        if (size < 0) break;
                        if (size > buffer.capacity())
                            throw new IOException("Video frame exceeds supported buffer size");
                        if (localStart < 0) localStart = inputTime;
                        long pts = outputPts + Math.max(0L, inputTime - localStart);
                        if (pts <= lastWritten) pts = lastWritten + 1;
                        info.set(0, size, pts, flags & MediaExtractor.SAMPLE_FLAG_SYNC);
                        buffer.position(0);
                        buffer.limit(size);
                        muxer.writeSampleData(outTrack, buffer, info);
                        lastWritten = pts;
                        clipSamples++;
                        totalSamples++;
                        if (!extractor.advance()) break;
                    }
                    if (clipSamples > 0) outputPts = lastWritten + 33333L;
                    final String update = String.format(Locale.US,
                        "Export MP4: scene %d / %d, %d frames", index+1, clips, totalSamples);
                    if (progress != null) progress.onProgress(update);
                }
                if (totalSamples == 0) throw new IOException("No usable video samples exported");
                muxer.stop();
                started = false;
            }
            return totalSamples;
        } catch (RuntimeException e) {
            throw new IOException("MP4 video montage unavailable: " + e.getMessage(), e);
        } finally {
            if (muxer != null) {
                if (started) try { muxer.stop(); } catch (Exception ignored) {}
                try { muxer.release(); } catch (Exception ignored) {}
            }
            try { extractor.release(); } catch (RuntimeException ignored) {}
        }
    }
}
