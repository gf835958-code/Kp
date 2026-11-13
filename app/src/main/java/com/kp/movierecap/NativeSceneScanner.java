package com.kp.movierecap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;

/**
 * Native seek-free sampling for user-selected videos. Uses keyframes instead of
 * repeatedly seeking the WebView <video> element (which timed out on long movies).
 * This detects visible changes only and MUST NOT be described as plot understanding.
 */
public final class NativeSceneScanner {
    private NativeSceneScanner() {}

    public static JSONObject sample(Context context, Uri input, int scenes) throws Exception {
        if (scenes < 1 || scenes > 16) throw new IllegalArgumentException("Invalid scene count");
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, input);
            String durationValue = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationValue == null) throw new IllegalArgumentException("Video duration unavailable");
            long durationMs = Long.parseLong(durationValue);
            if (durationMs < 500) throw new IllegalArgumentException("Video too short");
            int sampleCount = Math.min(28, Math.max(12, scenes * 2));
            JSONArray frames = new JSONArray();
            double[] last = null;
            for (int i = 0; i < sampleCount; i++) {
                long ms = Math.min(durationMs - 50,
                    Math.max(0, Math.round((i + 0.35) * (double)durationMs / sampleCount)));
                Bitmap bitmap = null;
                try {
                    if (Build.VERSION.SDK_INT >= 27) {
                        bitmap = mmr.getScaledFrameAtTime(ms * 1000L,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 176, 99);
                    }
                    if (bitmap == null) bitmap = mmr.getFrameAtTime(ms * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (bitmap == null) continue;
                    Bitmap resized = Bitmap.createScaledBitmap(bitmap, 176, 99, true);
                    if (resized != bitmap) { bitmap.recycle(); bitmap = resized; }
                    Bitmap grid = Bitmap.createScaledBitmap(bitmap, 16, 9, true);
                    int[] pixels = new int[144];
                    grid.getPixels(pixels, 0, 16, 0, 0, 16, 9);
                    grid.recycle();
                    double[] luminance = new double[pixels.length];
                    double diff = last == null ? 1.0 : 0.0;
                    for (int k = 0; k < pixels.length; k++) {
                        luminance[k] = (0.299 * Color.red(pixels[k])
                                + 0.587 * Color.green(pixels[k])
                                + 0.114 * Color.blue(pixels[k])) / 255.0;
                        if (last != null) diff += Math.abs(luminance[k] - last[k]);
                    }
                    if (last != null) diff /= luminance.length;
                    last = luminance;
                    ByteArrayOutputStream jpeg = new ByteArrayOutputStream(10000);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 62, jpeg);
                    JSONObject frame = new JSONObject();
                    frame.put("time", ms / 1000.0);
                    frame.put("diff", diff);
                    frame.put("thumb", "data:image/jpeg;base64," +
                        Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP));
                    frames.put(frame);
                } catch (RuntimeException badFrame) {
                    // Corrupt or undecodable frames should not abort the scan.
                } finally {
                    if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                }
            }
            if (frames.length() == 0) {
                // Fallback produces *timestamps only*, not invented visual labels.
                for (int i = 0; i < scenes; i++) {
                    JSONObject frame = new JSONObject();
                    frame.put("time", Math.min(durationMs - 50,
                        Math.round((i + .5) * (double)durationMs / scenes)) / 1000.0);
                    frame.put("diff", i == 0 ? 1.0 : 0.0);
                    frame.put("thumb", "");
                    frames.put(frame);
                }
            }
            JSONObject result = new JSONObject();
            result.put("duration", durationMs / 1000.0);
            result.put("frames", frames);
            return result;
        } finally {
            mmr.release();
        }
    }
}
