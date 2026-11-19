package com.kp.movierecap;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * Exercises the actual user journey: launch WebView, provide a short real MP4,
 * tap exactly ONE Generate button, and verify the app reports an exported MP4.
 * A synthetic clip and coarse visual captions cannot prove movie plot understanding.
 */
@RunWith(AndroidJUnit4.class)
public final class OneClickRecapUiInstrumentedTest {
    private static final int START_TIMEOUT_SECONDS = 35;
    private static final int EXPORT_TIMEOUT_SECONDS = 210;

    private static String eval(ActivityScenario<MainActivity> scenario, String js)
            throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        scenario.onActivity(activity -> {
            try {
                Field web = MainActivity.class.getDeclaredField("webView");
                web.setAccessible(true);
                WebView view = (WebView) web.get(activity);
                view.evaluateJavascript(js, value -> {
                    result.set(value);
                    done.countDown();
                });
            } catch (Exception e) {
                result.set("EVAL_ERROR: " + e.getMessage());
                done.countDown();
            }
        });
        assertTrue("JS evaluation hung", done.await(12, TimeUnit.SECONDS));
        return result.get();
    }

    private static void waitForWebView(ActivityScenario<MainActivity> scenario)
            throws Exception {
        long deadline = System.currentTimeMillis() + START_TIMEOUT_SECONDS * 1000;
        while (System.currentTimeMillis() < deadline) {
            String result = eval(scenario,
                "Boolean(document.readyState==='complete' && " +
                "typeof window.onAutoExportResult==='function' && " +
                "document.getElementById('autoBtn'))");
            if ("true".equals(result)) return;
            Thread.sleep(250);
        }
        fail("Movie Recap WebView never became ready");
    }

    @Test public void oneTapGenerateCallsRealVideoExportAndSavesMp4() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context tests = InstrumentationRegistry.getInstrumentation().getContext();
        File movie = new File(app.getCacheDir(), "kp_ci_oneclick_source.mp4");
        try (InputStream in = tests.getAssets().open("kp-smoke.mp4");
             FileOutputStream out = new FileOutputStream(movie)) {
            byte[] bytes = new byte[8192];
            int read;
            while ((read = in.read(bytes)) >= 0) if (read > 0) out.write(bytes, 0, read);
        }
        assertTrue("Input MP4 fixture missing", movie.length() > 1024);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    Field selected = MainActivity.class.getDeclaredField("selectedVideo");
                    selected.setAccessible(true);
                    selected.set(activity, Uri.fromFile(movie));
                } catch (Exception e) { throw new AssertionError(e); }
            });
            waitForWebView(scenario);
            // Set an HTML input stub (only for WebView metadata). The native
            // selectedVideo still points to the real H264/AAC fixture.
            // The real one-click event handler, native scan, Whisper, ONNX
            // Burmese TTS, and Media3 export run unchanged.
            String invoke = String.raw`(() => {
              window.__kpUiE2e={stage:'started'};
              const original=window.onAutoExportResult;
              window.onAutoExportResult=(ok,message)=>{
                original(ok,message);
                window.__kpUiE2e={stage:'finished',ok:Boolean(ok),message:String(message)};
              };
              loadFile(new File(['test fixture'],'kp-ui.mp4',{type:'video/mp4'}));
              $('maxScenes').value='1';
              $('target').value='12';
              $('autoBtn').click();
              return 'one-click-started';
            })()`;
            String initiated = eval(scenario, invoke);
            assertTrue("Generate was not invoked: " + initiated,
                initiated != null && initiated.contains("one-click-started"));

            long deadline = System.currentTimeMillis() + EXPORT_TIMEOUT_SECONDS * 1000;
            String result = "";
            while (System.currentTimeMillis() < deadline) {
                result = eval(scenario, "JSON.stringify(window.__kpUiE2e||{})");
                if (result != null && result.contains("finished")) break;
                Thread.sleep(1000);
            }
            assertTrue("Generate did not finish: " + result,
                result != null && result.contains("finished"));
            assertTrue("One click reported failed MP4 export: " + result,
                result.contains("\\\"ok\\\":true"));

            // Success in JS means the app's MediaStore copy finished. Confirm
            // that the produced file exists rather than trusting callback alone.
            String path = "Movies/KP Movie Recap/%";
            boolean found = false;
            try (Cursor cursor = app.getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Video.Media._ID, MediaStore.Video.Media.SIZE},
                    MediaStore.Video.Media.RELATIVE_PATH + " LIKE ?",
                    new String[]{path}, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        if (cursor.getLong(1) > 3000) {
                            found = true;
                            // Remove test media from emulator, not user devices.
                            Uri output = Uri.withAppendedPath(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                String.valueOf(cursor.getLong(0)));
                            try { app.getContentResolver().delete(output, null, null); }
                            catch (Exception ignored) { }
                        }
                    }
                }
            }
            assertTrue("One-click export missing in Movies/KP Movie Recap", found);
        } finally {
            movie.delete();
        }
    }
}
