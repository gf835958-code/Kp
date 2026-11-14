package com.kp.movierecap;

import android.content.Context;
import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;

/**
 * Noncommercial Burmese MMS-VITS ONNX runtime. Entirely on-device, no internet.
 * Generates one caption utterance per clip and pads/truncates only to enforce
 * media-timeline sync. Labels ARE NOT factual movie plot or dialogue.
 *
 * Requires bundled, license-verified model and sherpa-onnx Android AAR.
 */
public final class BurmeseNarration {
    private BurmeseNarration() {}

    public static File generate(Context context, List<String> captions,
            List<VideoHighlightsExporter.Range> ranges) throws Exception {
        if (captions == null || ranges == null || captions.size() != ranges.size()
                || ranges.isEmpty() || ranges.size() > 16) {
            throw new IllegalArgumentException("Unmatched voice scene count");
        }
        OfflineTts tts = null;
        File wav = new File(context.getCacheDir(), "kp_burmese_voice.wav");
        if (wav.exists() && !wav.delete())
            throw new IllegalStateException("Cannot replace voice file");
        try {
            OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig();
            vits.setModel("mya/model.onnx");
            vits.setTokens("mya/tokens.txt");
            OfflineTtsModelConfig model = new OfflineTtsModelConfig();
            model.setVits(vits);
            model.setNumThreads(2);
            OfflineTtsConfig cfg = new OfflineTtsConfig();
            cfg.setModel(model);
            cfg.setMaxNumSentences(1);
            tts = new OfflineTts(context.getAssets(), cfg);
            final int rate = tts.sampleRate();
            if (rate < 8000 || rate > 96000)
                throw new IllegalStateException("Invalid speech sample rate: " + rate);
            long totalFrames = 0;
            try (RandomAccessFile out = new RandomAccessFile(wav, "rw")) {
                out.setLength(0);
                out.write(new byte[44]); // Correct WAV header written last.
                byte[] block = new byte[16384];
                for (int i = 0; i < ranges.size(); i++) {
                    VideoHighlightsExporter.Range r = ranges.get(i);
                    long frames = Math.round((r.endUs - r.startUs) * rate / 1000000.0);
                    if (frames <= 0 || frames > rate * 16L)
                        throw new IllegalArgumentException("Invalid scene narration length");
                    String text = captions.get(i) == null ? "" : captions.get(i).trim();
                    if (text.length() > 130) text = text.substring(0, 130);
                    float[] samples = new float[0];
                    if (!text.isEmpty()) {
                        GeneratedAudio audio = tts.generate(text, 0, 1.0f);
                        if (audio.getSampleRate() != rate)
                            throw new IllegalStateException("Inconsistent sample rate");
                        samples = audio.getSamples();
                        if (samples == null) samples = new float[0];
                    }
                    int offset = 0;
                    while (offset < frames) {
                        int count = (int) Math.min(block.length / 2L, frames - offset);
                        for (int j = 0; j < count; j++) {
                            long index = (long) offset + j;
                            float signal = index < samples.length ? samples[(int) index] : 0f;
                            int sample = Math.round(Math.max(-1f, Math.min(1f, signal)) * 32767);
                            block[2 * j] = (byte) sample;
                            block[2 * j + 1] = (byte) (sample >>> 8);
                        }
                        out.write(block, 0, count * 2);
                        offset += count;
                    }
                    totalFrames += frames;
                }
                long dataBytes = totalFrames * 2;
                if (dataBytes < rate || dataBytes > Integer.MAX_VALUE)
                    throw new IllegalStateException("Narration too long");
                out.seek(0);
                out.writeBytes("RIFF");
                writeIntLE(out, (int) (36 + dataBytes));
                out.writeBytes("WAVEfmt ");
                writeIntLE(out, 16);
                writeShortLE(out, 1); // Linear PCM
                writeShortLE(out, 1); // mono
                writeIntLE(out, rate);
                writeIntLE(out, rate * 2);
                writeShortLE(out, 2);
                writeShortLE(out, 16);
                out.writeBytes("data");
                writeIntLE(out, (int) dataBytes);
            }
            return wav;
        } catch (Exception | UnsatisfiedLinkError err) {
            wav.delete();
            throw new IllegalStateException("Myanmar offline TTS failed: " + err.getMessage(), err);
        } finally {
            if (tts != null) tts.release();
        }
    }
    private static void writeShortLE(RandomAccessFile out, int n) throws Exception {
        out.write(n & 255); out.write((n >> 8) & 255);
    }
    private static void writeIntLE(RandomAccessFile out, int n) throws Exception {
        for (int i = 0; i < 4; i++) out.write((n >>> (8 * i)) & 255);
    }
}
