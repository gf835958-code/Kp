package com.kp.movierecap;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/**
 * Real Android emulator integration test (not a synthetic JS smoke test).
 * 1) Decode an actual H.264 MP4.
 * 2) Run the bundled ONNX Burmese TTS native engine and check a playable WAV.
 * 3) Export video, text overlay and Burmese audio with Media3.
 * 4) Reopen the MP4 and verify playable video+audio tracks.
 *
 * This does not verify story understanding, real-world voice quality,
 * Burmese pronunciation, caption appearance, or physical vivo V27 compatibility.
 */
@RunWith(AndroidJUnit4.class)
public final class NarratedRecapInstrumentedTest {
    /**
     * Regression test for the exact class of failure reported by the user:
     * WebView seeks time out when scanning a 30-minute MP4. This test exercises
     * real Android native frame extraction on an offline 1802-second H264 file.
     */
    @Test public void speechRecognizerDoesNotInventWordsFromSilence() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context tester = InstrumentationRegistry.getInstrumentation().getContext();
        File silent = new File(target.getCacheDir(), "kp_ci_silence.mp4");
        try (InputStream in = tester.getAssets().open("kp-silence.mp4");
             FileOutputStream out = new FileOutputStream(silent)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) if (n > 0) out.write(buffer, 0, n);
        }
        try {
            assertTrue("Missing silent MP4 fixture", silent.length() > 1024);
            org.json.JSONObject dialogue = DialogueTranscriber.transcribe(
                target, Uri.fromFile(silent), new double[]{3.0, 7.0});
            assertEquals("Whisper must not hallucinate dialogue on silent soundtrack", 0,
                dialogue.getJSONArray("dialogues").length());
            assertEquals("Silence windows still need correct audit count", 2,
                dialogue.getInt("windowsRequested"));
        } finally {
            silent.delete();
        }
    }

    @Test public void scansThirtyMinuteVideoWithoutWebViewSeeking() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context runner = InstrumentationRegistry.getInstrumentation().getContext();
        File movie = new File(target.getCacheDir(), "kp_ci_30min.mp4");
        try (InputStream source = runner.getAssets().open("kp-long.mp4");
             FileOutputStream out = new FileOutputStream(movie)) {
            byte[] block = new byte[8192];
            int read;
            while ((read = source.read(block)) >= 0) if (read > 0) out.write(block, 0, read);
        }
        assertTrue("30-min MP4 fixture is empty", movie.length() > 1024);
        try {
            long scanStart = android.os.SystemClock.elapsedRealtime();
            org.json.JSONObject result =
                NativeSceneScanner.sample(target, Uri.fromFile(movie), 10);
            long scanElapsed = android.os.SystemClock.elapsedRealtime() - scanStart;
            assertTrue("Native long-video scan exceeds 120-second UI timeout: " + scanElapsed + "ms",
                scanElapsed < 115000L);
            android.util.Log.i("KP_RECAPP_TEST", "30-minute native video scan took " + scanElapsed + "ms");
            assertTrue("Native scanner must see full 30-minute duration",
                result.getDouble("duration") > 1790);
            org.json.JSONArray frames = result.getJSONArray("frames");
            assertTrue("30-min scan returned no timestamps", frames.length() >= 8);
            assertTrue("30-min scan created too many memory-heavy frames", frames.length() <= 28);
            double previousTime = -1;
            for (int i = 0; i < frames.length(); i++) {
                org.json.JSONObject frame = frames.getJSONObject(i);
                double t = frame.getDouble("time");
                assertTrue("Scene timestamp outside full movie", t >= 0 && t < 1803);
                assertTrue("Native scanner frames out of order", t > previousTime);
                previousTime = t;
                String thumb = frame.optString("thumb", "");
                assertTrue("Invalid thumbnail from native decoder",
                    thumb.length() == 0 || thumb.startsWith("data:image/jpeg;base64,"));
            }
        } finally {
            movie.delete();
        }
    }

    @Test public void generatesRealAudioVideoMp4OnAndroid() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context instrumentation = InstrumentationRegistry.getInstrumentation().getContext();
        File source = new File(app.getCacheDir(), "kp_ci_input.mp4");
        try (InputStream in = instrumentation.getAssets().open("kp-smoke.mp4");
             FileOutputStream out = new FileOutputStream(source)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) if (n > 0) out.write(buffer, 0, n);
        }
        assertTrue("Synthetic source video is empty", source.length() > 1024);
        // The source now contains original synthetic spoken English dialogue.
        // Test the existing on-device Whisper library and bounded MediaCodec
        // decode path, without using an external transcription API.
        org.json.JSONObject dialogue = DialogueTranscriber.transcribe(
            app, Uri.fromFile(source), new double[]{3.0});
        org.json.JSONArray evidence = dialogue.getJSONArray("dialogues");
        assertTrue("Whisper recognizer produced no dialogue evidence", evidence.length() > 0);
        String heard = evidence.getJSONObject(0).getString("text");
        assertTrue("Empty actual speech transcript", heard.trim().length() >= 4);
        String recognized = heard.toLowerCase(java.util.Locale.ROOT);
        assertTrue("Speech recognizer missed the actual movie dialogue (map / house)",
            recognized.contains("map") || recognized.contains("house"));

        System.out.println("KP OFFLINE ASR evidence: " + heard);

        VideoHighlightsExporter.Range clip =
            new VideoHighlightsExporter.Range(0L, 10000000L);
        java.util.List<VideoHighlightsExporter.Range> clips =
            Collections.singletonList(clip);
        java.util.List<String> captions =
            Collections.singletonList("မင်္ဂလာပါ။");
        File speech = BurmeseNarration.generate(app, captions, clips);
        assertTrue("Offline Burmese TTS created no WAV", speech.length() > 10000);
        // A nonempty WAV file could still contain only silence; verify PCM energy.
        int peak = 0;
        try (java.io.RandomAccessFile pcm = new java.io.RandomAccessFile(speech, "r")) {
            pcm.seek(44);
            long count = Math.min(320000, (pcm.length() - 44) / 2);
            for (int i = 0; i < count; i++) {
                int lo = pcm.readUnsignedByte(), hi = pcm.readUnsignedByte();
                short sample = (short) (lo | (hi << 8));
                peak = Math.max(peak, Math.abs((int) sample));
            }
        }
        assertTrue("Burmese narration is silent", peak > 300);

        try (java.io.RandomAccessFile riff = new java.io.RandomAccessFile(speech, "r")) {
            byte[] header = new byte[4];
            riff.readFully(header);
            assertEquals("RIFF", new String(header, java.nio.charset.StandardCharsets.US_ASCII));
        }

        File out = new File(app.getCacheDir(), "kp_ci_narrated.mp4");
        if (out.exists()) out.delete();
        AtomicReference<Exception> problem = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        // Media3 transformer must be created and started on the main thread.
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                CaptionedVideoExporter encoder = new CaptionedVideoExporter();
                encoder.start(app, Uri.fromFile(source), clips, captions, speech, out,
                    new CaptionedVideoExporter.Callback() {
                        @Override public void onSuccess(File file) {
                            finished.countDown();
                        }
                        @Override public void onFailure(Exception error) {
                            problem.set(error);
                            finished.countDown();
                        }
                    });
            } catch (Exception error) {
                problem.set(error);
                finished.countDown();
            }
        });
        assertTrue("Media3 MP4 export timed out", finished.await(180, TimeUnit.SECONDS));
        assertNull("Media3 export failed", problem.get());
        assertTrue("Output MP4 empty", out.length() > 3000);

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(out.getAbsolutePath());
            boolean foundVideo = false, foundAudio = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) foundVideo = true;
                if (mime != null && mime.startsWith("audio/")) foundAudio = true;
            }
            assertTrue("Rendered MP4 missing video stream", foundVideo);
            assertTrue("Rendered MP4 missing Burmese voice stream", foundAudio);
        } finally {
            extractor.release();
            // Keep small generated evidence in the app's cache for CI extraction.
            // This output is a 10-second synthetic test clip, NOT a full recap.
            source.delete();
        }
    }
}
