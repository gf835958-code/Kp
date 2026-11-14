package com.kp.movierecap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.content.Intent;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.os.Environment;
import android.os.Build;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.label.ImageLabeling;
import com.google.mlkit.vision.label.ImageLabeler;
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions;
import com.google.mlkit.vision.label.ImageLabel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQ_VIDEO = 501;
    private static final int REQ_TEXT = 502;
    private static final int REQ_MP4 = 503;
    private static final int REQ_AUDIO = 504;
    private static final String AUDIO_UTTERANCE_ID = "kp-export-audio";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private WebView webView;
    private android.webkit.ValueCallback<Uri[]> filePickerCallback;
    private volatile Uri selectedVideo;
    private String pendingText;
    private List<VideoHighlightsExporter.Range> pendingRanges;
    private File pendingAudio;
    private final ImageLabeler imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS);
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private volatile boolean exporting = false;
    private CaptionedVideoExporter captionedExporter;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF080B18);
        getWindow().setNavigationBarColor(0xFF080B18);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF080B18);
        webView.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // The app has no external navigation; this also keeps the JS bridge local.
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view,
                    android.webkit.ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePickerCallback != null) filePickerCallback.onReceiveValue(null);
                filePickerCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("video/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try { startActivityForResult(intent, REQ_VIDEO); return true; }
                catch (Exception e) { filePickerCallback = null; callback.onReceiveValue(null); feedback("Video picker ဖွင့်မရပါ။"); return false; }
            }
        });
        webView.addJavascriptInterface(new NativeBridge(), "AndroidRecap");
        webView.loadUrl("file:///android_asset/index.html");

        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (!ttsReady) feedback("TTS Engine မရပါ။ ဖုန်း Settings မှာ Text-to-Speech စစ်ပါ။");
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }
            @Override public void onDone(String utteranceId) {
                if (AUDIO_UTTERANCE_ID.equals(utteranceId)) {
                    runOnUiThread(() -> {
                        if (pendingAudio != null && pendingAudio.isFile() && pendingAudio.length() > 0) {
                            startSaveDialog("KP-Recap-Voice.wav", "audio/wav", REQ_AUDIO);
                        } else feedback("အသံဖိုင် မဖန်တီးနိုင်ပါ။");
                    });
                }
            }
            @Override public void onError(String utteranceId) {
                if (AUDIO_UTTERANCE_ID.equals(utteranceId)) feedback("TTS အသံဖိုင်ထုတ်ရာမှာ အမှားရှိပါတယ်။");
            }
        });
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void feedback(String message) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript("window.nativeTtsFeedback(" + JSONObject.quote(message) + ")", null);
        });
    }

    private void startSaveDialog(String name, String mime, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, name);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { startActivityForResult(intent, requestCode); }
        catch (Exception e) { feedback("Save As dialog ဖွင့်မရပါ။"); }
    }

    @Override @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (requestCode == REQ_VIDEO) {
            android.webkit.ValueCallback<Uri[]> callback = filePickerCallback;
            filePickerCallback = null;
            if (uri != null) {
                selectedVideo = uri;
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (Exception ignored) { /* Selected content access is still valid this session. */ }
            }
            if (callback != null) callback.onReceiveValue(uri == null ? null : new Uri[]{uri});
            return;
        }
        if (uri == null) {
            feedback("ဖိုင်သိမ်းတာ ပယ်ဖျက်လိုက်ပါတယ်။");
            return;
        }
        if (requestCode == REQ_TEXT) {
            String copy = pendingText;
            pendingText = null;
            if (copy == null) return;
            io.execute(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IllegalStateException("Cannot open file");
                    out.write(copy.getBytes(StandardCharsets.UTF_8));
                    feedback("TXT / SRT ဖိုင်သိမ်းပြီးပါပြီ။");
                } catch (Exception e) { feedback("စာဖိုင်သိမ်းမရပါ: " + e.getMessage()); }
            });
        } else if (requestCode == REQ_AUDIO) {
            File audio = pendingAudio;
            pendingAudio = null;
            if (audio == null) return;
            io.execute(() -> {
                try (InputStream in = new FileInputStream(audio);
                     OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IllegalStateException("Cannot open audio destination");
                    byte[] buffer = new byte[65536];
                    int length;
                    while ((length = in.read(buffer)) != -1) out.write(buffer, 0, length);
                    feedback("Voice File သိမ်းပြီးပါပြီ။");
                } catch (Exception e) { feedback("Voice File သိမ်းမရပါ: " + e.getMessage()); }
                finally { audio.delete(); }
            });
        } else if (requestCode == REQ_MP4) {
            List<VideoHighlightsExporter.Range> ranges = pendingRanges;
            pendingRanges = null;
            Uri input = selectedVideo;
            if (ranges == null || input == null) { exporting = false; return; }
            io.execute(() -> {
                try (ParcelFileDescriptor out = getContentResolver().openFileDescriptor(uri, "w")) {
                    if (out == null) throw new IllegalStateException("Cannot open MP4 destination");
                    VideoHighlightsExporter.export(this, input, out.getFileDescriptor(), ranges);
                    feedback("🎞️ Scene Highlight MP4 သိမ်းပြီးပါပြီ။");
                } catch (Exception e) {
                    try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) { }
                    feedback("MP4 Export မအောင်မြင်ပါ: " + e.getMessage());
                } finally { exporting = false; }
            });
        }
    }

    private void reportImageLabels(int index, JSONArray labels, String error) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(
                "window.onFrameLabels(" + index + "," + labels.toString() + "," + JSONObject.quote(error == null ? "" : error) + ")", null);
        });
    }

    /** Save the one-tap rough cut to Movies/KP Movie Recap without asking for a second tap. */
    private void exportToMovies(List<VideoHighlightsExporter.Range> ranges) {
        if (Build.VERSION.SDK_INT < 29) {
            pendingRanges = ranges;
            startSaveDialog("KP-Scene-Highlights.mp4", "video/mp4", REQ_MP4);
            return;
        }
        final Uri input = selectedVideo;
        if (input == null) {
            exporting = false;
            autoExportFeedback(false, "Video ကို အရင်ရွေးပါ။");
            return;
        }
        io.execute(() -> {
            Uri output = null;
            boolean completed = false;
            try {
                ContentValues values = new ContentValues();
                final String filename = "KP-Highlights-" + System.currentTimeMillis() + ".mp4";
                values.put(MediaStore.Video.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/KP Movie Recap");
                values.put(MediaStore.Video.Media.IS_PENDING, 1);
                output = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (output == null) throw new IllegalStateException("Cannot create movie file");
                try (ParcelFileDescriptor out = getContentResolver().openFileDescriptor(output, "w")) {
                    if (out == null) throw new IllegalStateException("Cannot open movie file");
                    VideoHighlightsExporter.export(this, input, out.getFileDescriptor(), ranges);
                }
                ContentValues published = new ContentValues();
                published.put(MediaStore.Video.Media.IS_PENDING, 0);
                getContentResolver().update(output, published, null, null);
                completed = true;
                autoExportFeedback(true, "MP4 ကို Movies/KP Movie Recap/ ထဲ အလိုအလျောက် သိမ်းပြီးပါပြီ။");
            } catch (Exception e) {
                if (output != null) {
                    try { getContentResolver().delete(output, null, null); } catch (Exception ignored) { }
                }
                autoExportFeedback(false, "MP4 Export မအောင်မြင်ပါ: " + e.getMessage());
            } finally {
                exporting = false;
            }
        });
    }


    /** Media3 transcodes scene clips with the auto-generated visible-text overlays.
     * No Android device TTS, transcript, or speech alignment is implied here.
     * Media3 must start on the main thread; its callback is also on that thread. */
    private void exportCaptionedToMovies(List<VideoHighlightsExporter.Range> ranges,
            List<String> captions) {
        if (Build.VERSION.SDK_INT < 29) {
            exporting = false;
            autoExportFeedback(false, "Auto subtitle export requires Android 10 or newer.");
            return;
        }
        final Uri source = selectedVideo;
        if (source == null) {
            exporting = false;
            autoExportFeedback(false, "Video file ကို အရင်ရွေးပါ။");
            return;
        }
        File temporary = new File(getCacheDir(), "kp_captioned_working.mp4");
        try {
            captionedExporter = new CaptionedVideoExporter();
            captionedExporter.start(this, source, ranges, captions, temporary,
                new CaptionedVideoExporter.Callback() {
                    @Override public void onSuccess(File encoded) {
                        captionedExporter = null;
                        saveCaptionedToMovies(encoded);
                    }
                    @Override public void onFailure(Exception error) {
                        captionedExporter = null;
                        exporting = false;
                        autoExportFeedback(false, "Captioned video export error: " + error.getMessage());
                    }
                });
        } catch (Exception e) {
            captionedExporter = null;
            exporting = false;
            temporary.delete();
            autoExportFeedback(false, "Cannot start caption rendering: " + e.getMessage());
        }
    }

    /** Publish the completed MP4 only when export has finished successfully. */
    private void saveCaptionedToMovies(File encoded) {
        io.execute(() -> {
            Uri output = null;
            try {
                if (!encoded.exists() || encoded.length() < 1024)
                    throw new IllegalStateException("No MP4 created");
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME,
                    "KP-Visual-Captions-" + System.currentTimeMillis() + ".mp4");
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/KP Movie Recap");
                values.put(MediaStore.Video.Media.IS_PENDING, 1);
                output = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (output == null) throw new IllegalStateException("Cannot create MP4 output");
                try (InputStream in = new FileInputStream(encoded);
                     OutputStream out = getContentResolver().openOutputStream(output, "w")) {
                    if (out == null) throw new IllegalStateException("Cannot open MP4 stream");
                    byte[] buffer = new byte[65536];
                    int read;
                    while ((read = in.read(buffer)) >= 0)
                        if (read > 0) out.write(buffer, 0, read);
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.Video.Media.IS_PENDING, 0);
                getContentResolver().update(output, done, null, null);
                autoExportFeedback(true, "Scene visual subtitles ပါတဲ့ MP4 ကို Movies/KP Movie Recap/ ထဲ သိမ်းပြီးပါပြီ။");
            } catch (Exception e) {
                if (output != null) {
                    try { getContentResolver().delete(output, null, null); }
                    catch (Exception ignored) {}
                }
                autoExportFeedback(false, "Captioned MP4 save failed: " + e.getMessage());
            } finally {
                encoded.delete();
                exporting = false;
            }
        });
    }

    private void autoExportFeedback(boolean success, String message) {
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(
                "window.onAutoExportResult(" + success + "," + JSONObject.quote(message) + ")", null);
        });
    }

    private List<VideoHighlightsExporter.Range> parseRanges(String rangesJson) throws Exception {
        JSONArray arr = new JSONArray(rangesJson);
        if (arr.length() == 0 || arr.length() > 16) throw new IllegalArgumentException("Invalid scene count");
        List<VideoHighlightsExporter.Range> ranges = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONArray item = arr.getJSONArray(i);
            double start = item.getDouble(0), end = item.getDouble(1);
            if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end <= start || end - start > 15) {
                throw new IllegalArgumentException("Invalid clip time");
            }
            ranges.add(new VideoHighlightsExporter.Range((long)(start * 1_000_000), (long)(end * 1_000_000)));
        }
        ranges.sort(Comparator.comparingLong(r -> r.startUs));
        return ranges;
    }

    private void reportNativeScan(JSONObject payload, String error) {
        runOnUiThread(() -> {
            if (webView == null) return;
            String json = payload == null ? "null" : payload.toString();
            webView.evaluateJavascript("window.onNativeScanResult(" + json + "," +
                JSONObject.quote(error == null ? "" : error) + ")", null);
        });
    }

    private int configureTts(String tag) {
        if (!ttsReady || tts == null) { feedback("ဖုန်းမှာ TTS Engine မရသေးပါ။"); return TextToSpeech.ERROR; }
        int status = tts.setLanguage(Locale.forLanguageTag(tag));
        if (status < 0) feedback("ဒီဘာသာစကားအတွက် TTS voice မရှိပါ: " + tag);
        return status;
    }

    private final class NativeBridge {
        /** Offline ML Kit image labels describe visible objects, NOT plot or character intent. */
        @JavascriptInterface public void labelFrame(int index, String dataUrl) {
            if (index < 0 || index > 30 || dataUrl == null || dataUrl.length() > 300000) {
                reportImageLabels(index, new JSONArray(), "invalid frame"); return;
            }
            try {
                int delimiter = dataUrl.indexOf(',');
                if (delimiter < 0 || !dataUrl.startsWith("data:image/")) throw new IllegalArgumentException("bad image");
                byte[] bytes = Base64.decode(dataUrl.substring(delimiter+1), Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp == null) throw new IllegalArgumentException("image decode failed");
                imageLabeler.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener(labels -> {
                        JSONArray array = new JSONArray();
                        for (ImageLabel label : labels) {
                            if (label.getConfidence() < .45f) continue;
                            JSONObject item = new JSONObject();
                            try {
                                item.put("label", label.getText());
                                item.put("confidence", label.getConfidence());
                                array.put(item);
                            } catch (Exception ignored) { }
                        }
                        reportImageLabels(index, array, "");
                        bmp.recycle();
                    })
                    .addOnFailureListener(e -> {
                        reportImageLabels(index, new JSONArray(), e.getMessage());
                        bmp.recycle();
                    });
            } catch (Exception e) {
                reportImageLabels(index, new JSONArray(), e.getMessage());
            }
        }
        /** Off-main-thread native video scan; avoids WebView seek timeouts on long MP4s. */
        @JavascriptInterface public void scanVideoNative(int sceneCount) {
            Uri source = selectedVideo;
            if (source == null) { reportNativeScan(null, "No video selected"); return; }
            if (sceneCount < 1 || sceneCount > 16) { reportNativeScan(null, "Invalid scene count"); return; }
            io.execute(() -> {
                try { reportNativeScan(NativeSceneScanner.sample(MainActivity.this, source, sceneCount), ""); }
                catch (Exception e) {
                    reportNativeScan(null, e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });
        }

        @JavascriptInterface public void speak(String text, String tag) {
            runOnUiThread(() -> {
                if (text == null || text.trim().isEmpty()) return;
                if (configureTts(tag) < 0) return;
                tts.stop();
                final int max = Math.min(TextToSpeech.getMaxSpeechInputLength() - 100, 3200);
                String remaining = text.trim();
                boolean first = true;
                while (!remaining.isEmpty()) {
                    int n = Math.min(max, remaining.length());
                    if (n < remaining.length()) {
                        int split = remaining.lastIndexOf(' ', n);
                        if (split > max / 2) n = split;
                    }
                    String chunk = remaining.substring(0, n).trim();
                    if (!chunk.isEmpty()) tts.speak(chunk,
                        first ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD,
                        new Bundle(), "kp-preview-" + System.nanoTime());
                    remaining = remaining.substring(n).trim();
                    first = false;
                }
                feedback("Voice Preview စတင်ပါပြီ။");
            });
        }
        @JavascriptInterface public void stopSpeech() {
            runOnUiThread(() -> { if (tts != null) tts.stop(); });
        }
        @JavascriptInterface public void saveText(String name, String body) {
            runOnUiThread(() -> {
                if (body == null || body.isEmpty() || body.length() > 2_000_000) {
                    feedback("Export စာသား အရွယ်အစား မမှန်ပါ။"); return;
                }
                pendingText = body;
                String safe = (name == null ? "KP-Movie-Recap.txt" : name)
                    .replaceAll("[^a-zA-Z0-9._-]", "_");
                startSaveDialog(safe, "text/plain", REQ_TEXT);
            });
        }
        @JavascriptInterface public void saveNarration(String text, String tag) {
            runOnUiThread(() -> {
                if (text == null || text.trim().isEmpty()) { feedback("Script အရင်ရေးပါ။"); return; }
                if (text.length() > 3200) { feedback("Voice File အတွက် Script ကို စာလုံး 3200 အောက် ချုံ့ပေးပါ။"); return; }
                if (configureTts(tag) < 0) return;
                if (pendingAudio != null) pendingAudio.delete();
                pendingAudio = new File(getCacheDir(), "kp_voice_export.wav");
                tts.stop();
                int result = tts.synthesizeToFile(text, new Bundle(), pendingAudio, AUDIO_UTTERANCE_ID);
                if (result != TextToSpeech.SUCCESS) feedback("TTS အသံထုတ်လို့ မရပါ။");
            });
        }
        @JavascriptInterface public void exportCaptionedAuto(String rangesJson,
                String captionsJson) {
            runOnUiThread(() -> {
                if (selectedVideo == null) {
                    autoExportFeedback(false, "Video ကို အရင်ရွေးပါ။"); return;
                }
                if (exporting) {
                    autoExportFeedback(false, "MP4 Export တစ်ခု လုပ်နေဆဲဖြစ်ပါတယ်။"); return;
                }
                try {
                    List<VideoHighlightsExporter.Range> ranges = parseRanges(rangesJson);
                    JSONArray array = new JSONArray(captionsJson);
                    if (array.length() != ranges.size())
                        throw new IllegalArgumentException("Scene caption count mismatch");
                    List<String> captions = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        String caption = array.optString(i, "");
                        if (caption.length() > 240)
                            throw new IllegalArgumentException("Caption too long");
                        captions.add(caption);
                    }
                    exporting = true;
                    exportCaptionedToMovies(ranges, captions);
                } catch (Exception e) {
                    exporting = false;
                    autoExportFeedback(false, "Caption settings invalid: " + e.getMessage());
                }
            });
        }

        @JavascriptInterface public void exportHighlightsAuto(String rangesJson) {
            runOnUiThread(() -> {
                if (selectedVideo == null) { autoExportFeedback(false, "Video ကို အရင်ရွေးပါ။"); return; }
                if (exporting) { autoExportFeedback(false, "MP4 Export တစ်ခု လုပ်နေဆဲဖြစ်ပါတယ်။"); return; }
                try {
                    List<VideoHighlightsExporter.Range> ranges = parseRanges(rangesJson);
                    exporting = true;
                    exportToMovies(ranges);
                } catch (Exception e) {
                    exporting = false;
                    autoExportFeedback(false, "Scene MP4 Settings မမှန်ပါ။");
                }
            });
        }
        @JavascriptInterface public void exportHighlights(String rangesJson) {
            runOnUiThread(() -> {
                if (selectedVideo == null) { feedback("Video ဖိုင်ကို အရင်ရွေးပါ။"); return; }
                if (exporting) { feedback("MP4 Export တစ်ခု လုပ်နေဆဲဖြစ်ပါတယ်။"); return; }
                try {
                    List<VideoHighlightsExporter.Range> ranges = parseRanges(rangesJson);
                    pendingRanges = ranges;
                    exporting = true;
                    startSaveDialog("KP-Scene-Highlights.mp4", "video/mp4", REQ_MP4);
                } catch (Exception e) { exporting = false; feedback("Scene MP4 Settings မမှန်ပါ။"); }
            });
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
    @Override public void onDestroy() {
        if (filePickerCallback != null) filePickerCallback.onReceiveValue(null);
        if (tts != null) { tts.stop(); tts.shutdown(); }
        imageLabeler.close();
        if (webView != null) { webView.removeJavascriptInterface("AndroidRecap"); webView.destroy(); }
        io.shutdownNow();
        super.onDestroy();
    }
}
