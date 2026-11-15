package com.kp.recapstudio;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Private, resumable transcript progress for long on-device Whisper sessions.
 * Exact SHA-256 of extracted PCM WAV prevents accidentally reusing a transcript
 * from another video. A checkpoint is written atomically per completed 27s
 * chunk, including silent chunks (empty text).
 *
 * Only store transcripts from files the user imported; no network access.
 */
public final class OfflineTranscriptCheckpoints {
    private static final String ENGINE_VERSION = "whisper-tiny-myanmar-v1";
    private static final int MAX_CHUNK_BYTES = 200_000;
    private OfflineTranscriptCheckpoints() {}

    public static File jobDirectory(Context context, File wav) throws IOException {
        if (context == null || wav == null || !wav.isFile())
            throw new IOException("Missing WAV for transcript resume");
        final MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
        byte[] buffer = new byte[1024 * 1024];
        try (FileInputStream in = new FileInputStream(wav)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Transcription cancelled");
                md.update(buffer, 0, n);
            }
        }
        StringBuilder sha = new StringBuilder();
        for (byte b : md.digest()) sha.append(String.format(Locale.US, "%02x", b & 255));
        File root = new File(context.getFilesDir(), "kp-offline-asr-checkpoints");
        File job = new File(root, ENGINE_VERSION + "-" + sha);
        if (!job.isDirectory() && !job.mkdirs())
            throw new IOException("Cannot create private ASR resume directory");
        return job;
    }

    private static File chunkFile(File dir, int index) throws IOException {
        if (dir == null || !dir.isDirectory() || index < 0 || index > 2000)
            throw new IOException("Invalid ASR chunk");
        return new File(dir, String.format(Locale.US, "chunk-%04d.txt", index));
    }

    /** null = not computed, empty string = computed but no recognizable words. */
    public static String read(File job, int index) throws IOException {
        File file = chunkFile(job, index);
        if (!file.isFile()) return null;
        if (file.length() > MAX_CHUNK_BYTES)
            throw new IOException("Corrupted ASR checkpoint exceeds maximum");
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) {
                if (out.size() + n > MAX_CHUNK_BYTES)
                    throw new IOException("ASR checkpoint too large");
                out.write(b, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    public static void write(File job, int index, String text) throws IOException {
        File target = chunkFile(job, index);
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CHUNK_BYTES)
            throw new IOException("ASR chunk exceeds permitted length");
        File partial = new File(job, target.getName() + ".part");
        try {
            try (FileOutputStream out = new FileOutputStream(partial)) {
                out.write(bytes);
                out.getFD().sync();
            }
            if (!partial.renameTo(target))
                throw new IOException("Cannot commit completed transcript checkpoint");
        } finally {
            if (partial.exists()) partial.delete();
        }
    }
}
