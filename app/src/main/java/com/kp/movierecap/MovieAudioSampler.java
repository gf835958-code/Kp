package com.kp.movierecap;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.SystemClock;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Decode a short excerpt from an input video's real audio track into 16 kHz mono
 * floating-point PCM for offline speech recognition. No network requests, no
 * audio upload, and no buffering of a full-length film into RAM.
 */
public final class MovieAudioSampler {
    private MovieAudioSampler() {}
    public static final int SAMPLE_RATE = 16000;
    public static final int MAX_SECONDS = 15;

    public static float[] sample(Context ctx, Uri movie, long middleUs, int seconds)
            throws Exception {
        if (movie == null || seconds < 1 || seconds > MAX_SECONDS || middleUs < 0)
            throw new IllegalArgumentException("Invalid audio window");
        final long windowUs = seconds * 1000000L;
        final long startUs = Math.max(0L, middleUs - windowUs / 2);
        final long endUs = startUs + windowUs;
        final float[] samples = new float[seconds * SAMPLE_RATE];
        int count = 0;
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        try {
            extractor.setDataSource(ctx, movie, null);
            int audioTrack = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { audioTrack = i; break; }
            }
            if (audioTrack < 0) return new float[0]; // silent film, don't invent dialog
            extractor.selectTrack(audioTrack);
            MediaFormat fmt = extractor.getTrackFormat(audioTrack);
            String codecType = fmt.getString(MediaFormat.KEY_MIME);
            if (codecType == null) return new float[0];
            codec = MediaCodec.createDecoderByType(codecType);
            codec.configure(fmt, null, null, 0);
            codec.start();
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            boolean inputFinished = false, outputFinished = false;
            final long maxDeadline = SystemClock.elapsedRealtime() + 60000L;
            double nextSampleUs = startUs;
            int outRate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ?
                fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 48000;
            int channels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ?
                fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 1;
            int encoding = 2; // 16-bit PCM
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (!outputFinished && count < samples.length) {
                if (SystemClock.elapsedRealtime() > maxDeadline)
                    throw new IOException("Audio decoding timed out");
                if (!inputFinished) {
                    int inId = codec.dequeueInputBuffer(5000);
                    if (inId >= 0) {
                        ByteBuffer input = codec.getInputBuffer(inId);
                        if (input == null) throw new IOException("Missing decoder input");
                        input.clear();
                        long pts = extractor.getSampleTime();
                        if (pts < 0 || pts > endUs + 1000000L) {
                            codec.queueInputBuffer(inId, 0, 0, Math.max(0, endUs),
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputFinished = true;
                        } else {
                            int size = extractor.readSampleData(input, 0);
                            if (size < 0) {
                                codec.queueInputBuffer(inId, 0, 0, Math.max(0, endUs),
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputFinished = true;
                            } else {
                                codec.queueInputBuffer(inId, 0, size, pts, 0);
                                extractor.advance();
                            }
                        }
                    }
                }
                int outId = codec.dequeueOutputBuffer(info, 10000);
                if (outId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outFmt = codec.getOutputFormat();
                    if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                        outRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                        channels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        encoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING);
                    if (outRate < 8000 || outRate > 192000 || channels < 1 || channels > 8)
                        throw new IOException("Unsupported decoded PCM layout");
                } else if (outId >= 0) {
                    ByteBuffer pcm = codec.getOutputBuffer(outId);
                    if (pcm != null && info.size > 0 && info.presentationTimeUs <= endUs) {
                        pcm.order(ByteOrder.LITTLE_ENDIAN);
                        int bytesPerSample = encoding == 4 ? 4 : 2;
                        int stride = channels * bytesPerSample;
                        if (info.offset >= 0 && info.size >= stride &&
                                info.offset + info.size <= pcm.limit()) {
                            int frames = info.size / stride;
                            for (int frame = 0; frame < frames && count < samples.length; frame++) {
                                double t = info.presentationTimeUs +
                                    frame * (1000000.0 / outRate);
                                if (t < startUs || t + 0.01 < nextSampleUs) continue;
                                if (t >= endUs) break;
                                int base = info.offset + frame * stride;
                                float mono = 0f;
                                for (int ch = 0; ch < channels; ch++) {
                                    int at = base + ch * bytesPerSample;
                                    mono += encoding == 4 ? pcm.getFloat(at) :
                                        pcm.getShort(at) / 32768.0f;
                                }
                                float value = mono / channels;
                                samples[count++] = Math.max(-1f, Math.min(1f, value));
                                nextSampleUs += 1000000.0 / SAMPLE_RATE;
                            }
                        }
                    }
                    outputFinished = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outId, false);
                    if (info.presentationTimeUs > endUs) outputFinished = true;
                }
            }
            return count < SAMPLE_RATE / 2 ? new float[0] : Arrays.copyOf(samples, count);
        } finally {
            if (codec != null) {
                try { codec.stop(); } catch (Exception ignored) {}
                codec.release();
            }
            extractor.release();
        }
    }
}
