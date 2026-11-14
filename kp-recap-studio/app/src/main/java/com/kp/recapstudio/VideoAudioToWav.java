package com.kp.recapstudio;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Offline MediaExtractor + MediaCodec audio extraction for Whisper.
 * Extracts supported video soundtrack into 16 kHz mono signed 16-bit WAV.
 * This is audio transcription preparation, NOT visual movie understanding.
 */
public final class VideoAudioToWav {
    public interface Progress {
        void update(String message);
    }

    private static final int OUTPUT_RATE = 16000;
    private VideoAudioToWav() {}

    private static void fourcc(RandomAccessFile f, String name) throws IOException {
        f.write(name.getBytes(StandardCharsets.US_ASCII));
    }
    private static void le16(RandomAccessFile f, int value) throws IOException {
        f.write(value & 255);
        f.write((value >>> 8) & 255);
    }
    private static void le32(RandomAccessFile f, long value) throws IOException {
        for (int i = 0; i < 4; i++) f.write((int)(value >>> (8 * i)) & 255);
    }
    private static void wavHeader(RandomAccessFile f, long pcmBytes) throws IOException {
        if (pcmBytes > 0xffffffffL - 36L) throw new IOException("WAV exceeds 4 GB RIFF format limit");
        f.seek(0);
        fourcc(f, "RIFF"); le32(f, pcmBytes + 36L);
        fourcc(f, "WAVEfmt "); le32(f, 16); le16(f, 1); le16(f, 1);
        le32(f, OUTPUT_RATE); le32(f, OUTPUT_RATE * 2L); le16(f, 2); le16(f, 16);
        fourcc(f, "data"); le32(f, pcmBytes);
    }

    private static final class Resample {
        private final RandomAccessFile out;
        private final int inputRate;
        private long inputFrame = 0, outputFrame = 0;
        private float previous = 0;
        private boolean initialized = false;

        Resample(RandomAccessFile out, int sampleRate) {
            this.out = out;
            this.inputRate = sampleRate;
        }
        void accept(float current) throws IOException {
            if (!initialized) {
                previous = current;
                initialized = true;
                inputFrame = 1;
                write(current);
                outputFrame = 1;
                return;
            }
            long currentIndex = inputFrame++;
            while (outputFrame * (double)inputRate <= currentIndex * (double)OUTPUT_RATE) {
                double position = outputFrame * (double)inputRate / OUTPUT_RATE;
                double fraction = Math.max(0, Math.min(1, position - (currentIndex - 1)));
                write((float)(previous * (1 - fraction) + current * fraction));
                outputFrame++;
            }
            previous = current;
        }
        private void write(float sample) throws IOException {
            int pcm = (int)Math.round(Math.max(-1.0, Math.min(1.0, sample)) * 32767.0);
            le16(out, pcm & 0xffff);
        }
        long samples() { return outputFrame; }
    }

    public static File extract(Context context, Uri input, File destination,
                               Progress progress) throws IOException {
        if (context == null || input == null || destination == null)
            throw new IOException("Video input or WAV output is missing");
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        try {
            extractor.setDataSource(context, input, null);
            int audioTrack = -1;
            MediaFormat inputFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    inputFormat = fmt;
                    audioTrack = i;
                    break;
                }
            }
            if (audioTrack < 0 || inputFormat == null) {
                throw new IOException("This video has no supported soundtrack");
            }
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);
            extractor.selectTrack(audioTrack);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            try (RandomAccessFile wav = new RandomAccessFile(destination, "rw")) {
                wav.setLength(0);
                wavHeader(wav, 0);
                wav.seek(44);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean sentEOS = false, receivedEOS = false;
                long decodedFrames = 0;
                int rate = -1, channels = -1;
                Resample resampler = null;

                while (!receivedEOS) {
                    if (Thread.currentThread().isInterrupted())
                        throw new IOException("Audio extraction cancelled");
                    if (!sentEOS) {
                        int inIndex = decoder.dequeueInputBuffer(10_000);
                        if (inIndex >= 0) {
                            ByteBuffer buffer = decoder.getInputBuffer(inIndex);
                            if (buffer == null) throw new IOException("Missing decoder input buffer");
                            buffer.clear();
                            int size = extractor.readSampleData(buffer, 0);
                            if (size < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                sentEOS = true;
                            } else {
                                long pts = Math.max(0L, extractor.getSampleTime());
                                decoder.queueInputBuffer(inIndex, 0, size, pts, 0);
                                extractor.advance();
                            }
                        }
                    }

                    int outIndex = decoder.dequeueOutputBuffer(info, 10_000);
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat fmt = decoder.getOutputFormat();
                        int newRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        int newChannels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        int pcmEncoding = fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            ? fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            : AudioFormat.ENCODING_PCM_16BIT;
                        if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                            throw new IOException("Decoder produced unsupported non-16-bit PCM");
                        }
                        if (newRate < 8000 || newRate > 192000 || newChannels < 1 ||
                                newChannels > 8) throw new IOException("Unsupported source audio rate/channels");
                        if (resampler != null && (newRate != rate || newChannels != channels))
                            throw new IOException("Video soundtrack format changed midstream");
                        rate = newRate;
                        channels = newChannels;
                        if (resampler == null) resampler = new Resample(wav, rate);
                    } else if (outIndex >= 0) {
                        ByteBuffer pcm = decoder.getOutputBuffer(outIndex);
                        if (pcm == null) throw new IOException("Missing decoder output PCM buffer");
                        if (resampler == null) {
                            MediaFormat fmt = decoder.getOutputFormat(outIndex);
                            rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                            channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                            int encoding = fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)
                                ? fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                : AudioFormat.ENCODING_PCM_16BIT;
                            if (encoding != AudioFormat.ENCODING_PCM_16BIT || channels < 1 ||
                                  channels > 8 || rate < 8000 || rate > 192000)
                                throw new IOException("Unsupported decoder PCM format");
                            resampler = new Resample(wav, rate);
                        }
                        if (info.size > 0) {
                            int bytesPerFrame = channels * 2;
                            if (info.size % bytesPerFrame != 0)
                                throw new IOException("Unaligned PCM frame boundary");
                            pcm.order(ByteOrder.LITTLE_ENDIAN);
                            pcm.position(info.offset);
                            pcm.limit(info.offset + info.size);
                            while (pcm.remaining() >= bytesPerFrame) {
                                double mono = 0;
                                for (int c = 0; c < channels; c++) mono += pcm.getShort() / 32768.0;
                                resampler.accept((float)(mono / channels));
                                decodedFrames++;
                            }
                            if (progress != null && decodedFrames % (rate * 10L) < (info.size / bytesPerFrame))
                                progress.update("Offline audio decoded: " +
                                    (decodedFrames / Math.max(1, rate)) + " seconds");
                        }
                        receivedEOS = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        decoder.releaseOutputBuffer(outIndex, false);
                    }
                }
                if (resampler == null || resampler.samples() < OUTPUT_RATE / 2)
                    throw new IOException("Soundtrack shorter than 0.5 seconds or no audio");
                long audioSize = resampler.samples() * 2L;
                wavHeader(wav, audioSize);
                wav.setLength(44L + audioSize);
                if (progress != null) progress.update("Prepared 16kHz WAV for offline Whisper");
            }
            return destination;
        } catch (RuntimeException e) {
            throw new IOException("Unsupported video soundtrack/decoder: " + e.getMessage(), e);
        } finally {
            if (decoder != null) {
                try { decoder.stop(); } catch (RuntimeException ignored) {}
                try { decoder.release(); } catch (RuntimeException ignored) {}
            }
            extractor.release();
        }
    }
}
