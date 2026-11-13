package com.kp.movierecap;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A small local, lossless rough-cut exporter. Only MP4 muxing-compatible input codecs
 * are supported. Intervals snap backwards to the nearest video keyframe, so the
 * output has cleanly decodable scenes but approximate cut points.
 *
 * The output retains the original sound, NOT the new TTS voice or subtitles.
 */
public final class VideoHighlightsExporter {
    public static final class Range {
        public final long startUs;
        public final long endUs;
        public Range(long startUs, long endUs) {
            this.startUs = startUs;
            this.endUs = endUs;
        }
    }
    private static final class Track {
        final MediaExtractor extractor;
        final int sourceIndex;
        final int outputIndex;
        long lastPtsUs = -1;
        Track(MediaExtractor e, int sourceIndex, int outputIndex) {
            this.extractor = e;
            this.sourceIndex = sourceIndex;
            this.outputIndex = outputIndex;
        }
    }

    private VideoHighlightsExporter() { }

    private static MediaExtractor openTrack(Context context, Uri uri, int index) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, uri, null);
            extractor.selectTrack(index);
            return extractor;
        } catch (Exception e) {
            extractor.release();
            throw e;
        }
    }

    public static void export(Context context, Uri source, FileDescriptor destination, List<Range> ranges)
            throws Exception {
        if (ranges == null || ranges.isEmpty()) throw new IllegalArgumentException("No scenes selected");
        MediaExtractor info = new MediaExtractor();
        MediaMuxer muxer = null;
        List<Track> tracks = new ArrayList<>();
        boolean started = false;
        try {
            info.setDataSource(context, source, null);
            int video = -1, audio = -1;
            for (int i = 0; i < info.getTrackCount(); i++) {
                MediaFormat format = info.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) continue;
                if (video == -1 && mime.startsWith("video/")) video = i;
                if (audio == -1 && mime.startsWith("audio/")) audio = i;
            }
            if (video < 0) throw new IllegalArgumentException("No video track found");
            String videoMime = info.getTrackFormat(video).getString(MediaFormat.KEY_MIME);
            if (!("video/avc".equals(videoMime) || "video/hevc".equals(videoMime) ||
                    "video/mp4v-es".equals(videoMime) || "video/3gpp".equals(videoMime))) {
                throw new IllegalArgumentException("Please choose an H.264/H.265 MP4 video");
            }
            muxer = new MediaMuxer(destination, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            // Pass through rotation metadata instead of accidentally rotating phone movies.
            MediaMetadataRetriever meta = new MediaMetadataRetriever();
            try {
                meta.setDataSource(context, source);
                String rotation = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if (rotation != null) {
                    int degrees = Integer.parseInt(rotation);
                    if (degrees == 0 || degrees == 90 || degrees == 180 || degrees == 270) {
                        muxer.setOrientationHint(degrees);
                    }
                }
            } catch (Exception ignored) { /* Rotation hint is optional. */ }
            finally { meta.release(); }
            tracks.add(new Track(openTrack(context, source, video), video,
                    muxer.addTrack(info.getTrackFormat(video))));
            if (audio >= 0) {
                MediaFormat audioFormat = info.getTrackFormat(audio);
                String audioMime = audioFormat.getString(MediaFormat.KEY_MIME);
                if ("audio/mp4a-latm".equals(audioMime) || "audio/3gpp".equals(audioMime) ||
                        "audio/amr-wb".equals(audioMime) || "audio/mpeg".equals(audioMime)) {
                    try {
                        int outIndex = muxer.addTrack(audioFormat);
                        tracks.add(new Track(openTrack(context, source, audio), audio, outIndex));
                    } catch (Exception ignored) { /* Export video-only if input audio cannot be muxed. */ }
                }
            }
            muxer.start();
            started = true;
            ByteBuffer buffer = ByteBuffer.allocateDirect(8 * 1024 * 1024);
            MediaCodec.BufferInfo sample = new MediaCodec.BufferInfo();
            long nextBaseUs = 0;
            boolean wroteVideo = false;
            for (Range requested : ranges) {
                long anchorUs = Math.max(0, requested.startUs);
                MediaExtractor videoEx = tracks.get(0).extractor;
                videoEx.seekTo(anchorUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                if (videoEx.getSampleTime() >= 0) anchorUs = videoEx.getSampleTime();
                if (requested.endUs <= anchorUs) continue;
                for (Track track : tracks) {
                    MediaExtractor ex = track.extractor;
                    ex.seekTo(anchorUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                    while (true) {
                        long sourcePts = ex.getSampleTime();
                        if (sourcePts < 0 || sourcePts >= requested.endUs) break;
                        if (sourcePts < anchorUs) { if (!ex.advance()) break; continue; }
                        buffer.clear();
                        int size = ex.readSampleData(buffer, 0);
                        if (size < 0) break;
                        if (size > buffer.capacity()) throw new IllegalStateException("Video sample too large");
                        long outputPts = nextBaseUs + sourcePts - anchorUs;
                        if (outputPts <= track.lastPtsUs) outputPts = track.lastPtsUs + 1;
                        sample.set(0, size, outputPts,
                                (ex.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                                        ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                        buffer.position(0);
                        buffer.limit(size);
                        muxer.writeSampleData(track.outputIndex, buffer, sample);
                        track.lastPtsUs = outputPts;
                        if (track == tracks.get(0)) wroteVideo = true;
                        if (!ex.advance()) break;
                    }
                }
                nextBaseUs += Math.max(0, requested.endUs - anchorUs) + 10_000; // small time gap
            }
            if (!wroteVideo) throw new IllegalStateException("No supported video samples were found");
        } finally {
            info.release();
            for (Track track : tracks) track.extractor.release();
            if (muxer != null) {
                if (started) {
                    try { muxer.stop(); } catch (Exception ignored) { }
                }
                muxer.release();
            }
        }
    }
}
