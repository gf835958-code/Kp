package com.kp.movierecap;

import android.content.Context;
import android.net.Uri;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Offline spoken-dialogue recognition. Provides direct quotes as evidence,
 * never invented plot. Whisper tiny multilingual is imperfect; a transcript
 * is NOT a translation, a full-movie summary, or a visual understanding model.
 */
public final class DialogueTranscriber {
    private DialogueTranscriber() {}

    public interface ProgressListener {
        void onProgress(int completed, int total);
    }

    public static JSONObject transcribe(Context context, Uri movie, double[] times)
            throws Exception {
        return transcribe(context, movie, times, null);
    }

    public static JSONObject transcribe(Context context, Uri movie, double[] times,
            ProgressListener progress) throws Exception {
        if (movie == null || times == null || times.length == 0 || times.length > 8)
            throw new IllegalArgumentException("Invalid movie dialogue windows");
        OfflineRecognizer recognizer = null;
        try {
            OfflineWhisperModelConfig whisper = new OfflineWhisperModelConfig();
            whisper.setEncoder("asr/tiny-encoder.int8.onnx");
            whisper.setDecoder("asr/tiny-decoder.int8.onnx");
            whisper.setLanguage(""); // auto-detect language per input, not forced English
            whisper.setTask("transcribe"); // do NOT translate/hallucinate a movie script
            OfflineModelConfig model = new OfflineModelConfig();
            model.setWhisper(whisper);
            model.setTokens("asr/tiny-tokens.txt");
            model.setNumThreads(2);
            model.setModelType("whisper");
            FeatureConfig features = new FeatureConfig();
            features.setSampleRate(16000);
            features.setFeatureDim(80);
            OfflineRecognizerConfig config = new OfflineRecognizerConfig();
            config.setFeatConfig(features);
            config.setModelConfig(model);
            recognizer = new OfflineRecognizer(context.getAssets(), config);
            JSONArray evidence = new JSONArray();
            Set<String> seenText = new HashSet<>();
            for (int i = 0; i < times.length; i++) {
                if (progress != null) progress.onProgress(i, times.length);
                if (!Double.isFinite(times[i]) || times[i] < 0)
                    throw new IllegalArgumentException("Invalid timestamp");
                long microseconds = Math.round(times[i] * 1000000.0);
                // Preserve the Android UI and the phone's memory by decoding
                // at most one 12-second window at a time.
                float[] speech = MovieAudioSampler.sample(context, movie, microseconds, 12);
                if (speech.length == 0) continue;
                // Whisper tiny can hallucinate words in complete silence.
                // Skip truly silent/near-silent decoded windows before inference.
                double power = 0;
                float peak = 0f;
                for (float sample : speech) {
                    power += sample * sample;
                    peak = Math.max(peak, Math.abs(sample));
                }
                double rms = Math.sqrt(power / Math.max(1, speech.length));
                if (rms < 0.003 && peak < 0.02f) continue;
                OfflineStream stream = recognizer.createStream();
                try {
                    stream.acceptWaveform(speech, MovieAudioSampler.SAMPLE_RATE);
                    recognizer.decode(stream);
                    OfflineRecognizerResult result = recognizer.getResult(stream);
                    String raw = result.getText();
                    String recognized = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
                    if (recognized.length() > 260)
                        recognized = recognized.substring(0, 260);
                    // Do not fabricate a line when no speech can be understood.
                    if (recognized.length() < 3) continue;
                    String normalized = recognized.toLowerCase(Locale.ROOT)
                        .replaceAll("[\\p{Punct}\\s]+", " ").trim();
                    if (normalized.length() < 3 || !seenText.add(normalized)) continue;
                    JSONObject item = new JSONObject();
                    item.put("time", times[i]);
                    item.put("lang", result.getLang());
                    item.put("text", recognized);
                    evidence.put(item);
                } finally {
                    stream.release();
                }
            }
            if (progress != null) progress.onProgress(times.length, times.length);
            JSONObject payload = new JSONObject();
            payload.put("dialogues", evidence);
            payload.put("method", "Whisper tiny multilingual, distributed 12-second excerpts");
            payload.put("windowsRequested", times.length);
            payload.put("windowsRecognized", evidence.length());
            return payload;
        } finally {
            if (recognizer != null) recognizer.release();
        }
    }
}
