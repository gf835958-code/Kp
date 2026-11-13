package com.kp.recapstudio;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * Converts Android TTS generated 16-bit PCM WAV into AAC-LC in an M4A container.
 * Audio is synthesized on device beforehand; this stage only encodes it.
 * Accepts normal mono/stereo PCM 8-48 kHz WAV (not Float or ADPCM).
 */
public final class OfflineWavToAac {
    private OfflineWavToAac() {}

    private static class Wav {
        int channels, sampleRate, bits, format;
        long position, dataLength;
    }

    private static int u16(RandomAccessFile f) throws IOException {
        return f.readUnsignedByte() | (f.readUnsignedByte() << 8);
    }
    private static long u32(RandomAccessFile f) throws IOException {
        return ((long) f.readUnsignedByte()) | ((long) f.readUnsignedByte() << 8)
             | ((long) f.readUnsignedByte() << 16) | ((long) f.readUnsignedByte() << 24);
    }
    private static String tag(RandomAccessFile f) throws IOException {
        byte[] bytes = new byte[4]; f.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static Wav readHeader(RandomAccessFile file) throws IOException {
        if (!"RIFF".equals(tag(file))) throw new IOException("Not RIFF WAV");
        u32(file);
        if (!"WAVE".equals(tag(file))) throw new IOException("Not WAVE");
        Wav wav = new Wav();
        boolean gotFmt = false, gotData = false;
        while (file.getFilePointer() + 8 <= file.length()) {
            String chunk = tag(file);
            long size = u32(file), start = file.getFilePointer();
            if (size < 0 || size > file.length() - start) throw new IOException("Invalid WAV chunk");
            if ("fmt ".equals(chunk)) {
                if (size < 16) throw new IOException("Invalid WAV format");
                wav.format = u16(file);
                wav.channels = u16(file);
                wav.sampleRate = (int)u32(file);
                u32(file); u16(file);
                wav.bits = u16(file);
                gotFmt = true;
            } else if ("data".equals(chunk)) {
                wav.position = start;
                wav.dataLength = size;
                gotData = true;
            }
            file.seek(start + size + (size & 1L));
        }
        if (!gotFmt || !gotData) throw new IOException("Missing WAV format/audio");
        if (wav.format != 1 || wav.bits != 16 ||
                (wav.channels != 1 && wav.channels != 2))
            throw new IOException("Only 16-bit mono/stereo PCM WAV supported");
        switch (wav.sampleRate) {
            case 8000: case 11025: case 12000: case 16000: case 22050:
            case 24000: case 32000: case 44100: case 48000: break;
            default: throw new IOException("Unsupported AAC sampling rate " + wav.sampleRate);
        }
        if (wav.dataLength < wav.channels * 2) throw new IOException("WAV has no PCM frames");
        return wav;
    }

    public static void convert(File input, File output) throws IOException {
        if (input == null || !input.isFile()) throw new IOException("Generated WAV not found.");
        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        boolean startedMuxer = false;
        try (RandomAccessFile file = new RandomAccessFile(input, "r")) {
            Wav wav = readHeader(file);
            MediaFormat format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC, wav.sampleRate, wav.channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, wav.channels == 1 ? 96000 : 160000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192);

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            muxer = new MediaMuxer(output.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            final int frameBytes = wav.channels * 2;
            long remaining = wav.dataLength;
            long framesFed = 0;
            boolean inputFinished = false, outputFinished = false;
            file.seek(wav.position);
            byte[] cache = new byte[8192];
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int muxTrack = -1;

            while (!outputFinished) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Encoding cancelled");
                if (!inputFinished) {
                    int inputIndex = encoder.dequeueInputBuffer(10000L);
                    if (inputIndex >= 0) {
                        ByteBuffer inBuffer = encoder.getInputBuffer(inputIndex);
                        if (inBuffer == null) throw new IOException("No encoder input buffer");
                        inBuffer.clear();
                        int max = Math.min(cache.length, inBuffer.remaining());
                        int n = (int)Math.min(remaining, (long)(max / frameBytes) * frameBytes);
                        long presentation = framesFed * 1000000L / wav.sampleRate;
                        if (n > 0) {
                            file.readFully(cache, 0, n);
                            inBuffer.put(cache, 0, n);
                            encoder.queueInputBuffer(inputIndex, 0, n, presentation, 0);
                            framesFed += n / frameBytes;
                            remaining -= n;
                        } else {
                            encoder.queueInputBuffer(inputIndex, 0, 0, presentation,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputFinished = true;
                        }
                    }
                }

                int outIndex = encoder.dequeueOutputBuffer(info, 10000L);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (startedMuxer) throw new IOException("Encoder changed output format twice");
                    muxTrack = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    startedMuxer = true;
                } else if (outIndex >= 0) {
                    ByteBuffer bytes = encoder.getOutputBuffer(outIndex);
                    if (bytes == null) throw new IOException("No encoder output buffer");
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0;
                    }
                    if (info.size > 0) {
                        if (!startedMuxer) throw new IOException("AAC output format unavailable");
                        bytes.position(info.offset);
                        bytes.limit(info.offset + info.size);
                        muxer.writeSampleData(muxTrack, bytes, info);
                    }
                    outputFinished = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(outIndex, false);
                }
            }
            if (startedMuxer) { muxer.stop(); startedMuxer = false; }
        } catch (RuntimeException e) {
            throw new IOException("Offline AAC encoding failed: " + e.getMessage(), e);
        } finally {
            if (muxer != null) {
                if (startedMuxer) try { muxer.stop(); } catch (Exception ignored) {}
                try { muxer.release(); } catch (Exception ignored) {}
            }
            if (encoder != null) {
                try { encoder.stop(); } catch (Exception ignored) {}
                encoder.release();
            }
        }
    }
}
