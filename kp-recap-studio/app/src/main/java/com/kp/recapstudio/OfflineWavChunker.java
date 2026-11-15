package com.kp.recapstudio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Pure-Java bounded-memory WAV slicer for Whisper inference on long videos.
 * Accepts only the canonical 16kHz / mono / PCM16 / 44-byte WAV produced by
 * VideoAudioToWav. A chunk is <= 27 seconds and remains 16kHz/PCM16.
 *
 * Cuts are sample-accurate; speech near chunk boundaries can still be split.
 * This is not voice activity detection or linguistic alignment.
 */
public final class OfflineWavChunker {
    private static final long BYTES_PER_SECOND = 16000L * 2L;
    private static final int HEADER_SIZE = 44;
    public static final int DEFAULT_SECONDS = 27;
    private OfflineWavChunker() {}

    private static String fourCC(RandomAccessFile f) throws IOException {
        byte[] b = new byte[4];
        f.readFully(b);
        return new String(b, StandardCharsets.US_ASCII);
    }
    private static int le16(RandomAccessFile f) throws IOException {
        return f.readUnsignedByte() | (f.readUnsignedByte() << 8);
    }
    private static long le32(RandomAccessFile f) throws IOException {
        return (long)f.readUnsignedByte() | ((long)f.readUnsignedByte() << 8) |
            ((long)f.readUnsignedByte() << 16) | ((long)f.readUnsignedByte() << 24);
    }
    private static void put16(RandomAccessFile f, int value) throws IOException {
        f.write(value & 255); f.write((value >>> 8) & 255);
    }
    private static void put32(RandomAccessFile f, long value) throws IOException {
        for (int i=0;i<4;i++) f.write((int)(value >>> (8*i)) & 255);
    }

    /** Returns the number of 16-bit mono PCM samples, validating the header. */
    public static long frames(File wav) throws IOException {
        if (wav == null || !wav.isFile() || wav.length() < HEADER_SIZE + 2L)
            throw new IOException("Missing extracted PCM WAV");
        try (RandomAccessFile file = new RandomAccessFile(wav, "r")) {
            if (!"RIFF".equals(fourCC(file)))
                throw new IOException("Audio is not RIFF");
            long riffSize = le32(file);
            if (!"WAVE".equals(fourCC(file)) ||
                !"fmt ".equals(fourCC(file)) ||
                le32(file) != 16L ||
                le16(file) != 1 ||
                le16(file) != 1 ||
                le32(file) != 16000L ||
                le32(file) != BYTES_PER_SECOND ||
                le16(file) != 2 ||
                le16(file) != 16 ||
                !"data".equals(fourCC(file))) {
                throw new IOException("Whisper needs canonical 16kHz mono PCM16 WAV");
            }
            long dataSize = le32(file);
            if ((dataSize & 1L) != 0 ||
                dataSize != file.length() - HEADER_SIZE ||
                riffSize != dataSize + 36L)
                throw new IOException("PCM WAV is incomplete or corrupt");
            return dataSize / 2L;
        }
    }

    public static int count(File wav, int seconds) throws IOException {
        if (seconds < 1 || seconds > 29)
            throw new IOException("Chunk duration must be 1-29 seconds");
        long total = frames(wav);
        long framesPerChunk = seconds * 16000L;
        long chunks = (total + framesPerChunk - 1L) / framesPerChunk;
        if (chunks > 2000L) throw new IOException("Video soundtrack is too long");
        return (int) chunks;
    }

    public static void writeChunk(File wav, File output,
                                  int chunkIndex, int seconds) throws IOException {
        int chunks = count(wav, seconds);
        if (chunkIndex < 0 || chunkIndex >= chunks)
            throw new IOException("Invalid audio chunk index " + chunkIndex);
        if (output == null) throw new IOException("Missing chunk destination");
        long chunkStart = (long)chunkIndex * seconds * BYTES_PER_SECOND;
        long bytes = Math.min((long)seconds * BYTES_PER_SECOND,
            wav.length() - HEADER_SIZE - chunkStart);
        if (bytes <= 0 || bytes > Integer.MAX_VALUE)
            throw new IOException("Bad PCM chunk length");

        try (RandomAccessFile in = new RandomAccessFile(wav, "r");
             RandomAccessFile out = new RandomAccessFile(output, "rw")) {
            out.setLength(0);
            out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
            put32(out, bytes + 36L);
            out.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
            put32(out, 16);
            put16(out, 1); put16(out, 1);
            put32(out, 16000); put32(out, BYTES_PER_SECOND);
            put16(out, 2); put16(out, 16);
            out.write("data".getBytes(StandardCharsets.US_ASCII));
            put32(out, bytes);

            in.seek(HEADER_SIZE + chunkStart);
            byte[] buffer = new byte[65536];
            long remaining = bytes;
            while (remaining > 0) {
                if (Thread.currentThread().isInterrupted())
                    throw new IOException("Chunk writing cancelled");
                int n = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (n < 0) throw new IOException("Unexpected end of PCM source");
                out.write(buffer, 0, n);
                remaining -= n;
            }
        }
    }
}
