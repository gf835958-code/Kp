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
        VideoHighlightsExporter.Range clip =
            new VideoHighlightsExporter.Range(0L, 10000000L);
        java.util.List<VideoHighlightsExporter.Range> clips =
            Collections.singletonList(clip);
        java.util.List<String> captions =
            Collections.singletonList("မင်္ဂလာပါ။");
        File speech = BurmeseNarration.generate(app, captions, clips);
        assertTrue("Offline Burmese TTS created no WAV", speech.length() > 10000);
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
            speech.delete();
            source.delete();
            out.delete();
        }
    }
}
