package com.kp.recapstudio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import androidx.media3.common.Effect;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.CanvasOverlay;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.Effects;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.Transformer;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Burns approximately 5-second-per-caption subtitles into a LOCAL video,
 * using AndroidX Media3 to decode, draw and re-encode the frames.
 *
 * This is a simple subtitle exporter, NOT automatic speech recognition.
 * As with all transcoding, hardware support, power and device storage matter.
 */
@UnstableApi
public final class OfflineSubtitleRenderer {
    private OfflineSubtitleRenderer() {}

    public interface ResultListener {
        void onComplete(File result);
        void onError(String message);
    }

    private static final class CaptionCanvas extends CanvasOverlay {
        private final List<String> captions;
        private final Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint foreground = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        CaptionCanvas(List<String> lines) {
            super(true);
            captions = Collections.unmodifiableList(new ArrayList<>(lines));
            background.setColor(Color.argb(208, 11, 12, 29));
            foreground.setColor(Color.WHITE);
            foreground.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            foreground.setShadowLayer(2.5f, 1f, 2f, Color.BLACK);
        }

        @Override public void onDraw(Canvas canvas, long presentationTimeUs) {
            int index = (int)(presentationTimeUs / 5000000L);
            if (index < 0 || index >= captions.size()) return;
            String content = captions.get(index);
            if (content == null || content.trim().isEmpty()) return;

            int width = canvas.getWidth(), height = canvas.getHeight();
            if (width <= 0 || height <= 0) return;
            float fontSize = Math.max(20f, Math.min(70f, width * 0.046f));
            foreground.setTextSize(fontSize);
            int maxTextWidth = Math.max(10, (int)(width * 0.84f));
            StaticLayout text = StaticLayout.Builder.obtain(content, 0, content.length(),
                        foreground, maxTextWidth)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(true)
                    .setMaxLines(4)
                    .build();
            float cx = width / 2f;
            float padding = Math.max(12f, fontSize * 0.45f);
            float left = cx - maxTextWidth / 2f - padding;
            float bottom = height * 0.92f;
            float top = bottom - text.getHeight() - 2 * padding;
            canvas.drawRoundRect(left, top, cx + maxTextWidth / 2f + padding,
                    bottom, padding, padding, background);
            canvas.save();
            canvas.translate(cx - maxTextWidth / 2f, top + padding);
            text.draw(canvas);
            canvas.restore();
        }
    }

    /**
     * Must be invoked on Android's main thread. It returns immediately.
     * Caller keeps the Transformer reference for lifecycle cancellation.
     */
    public static Transformer start(Context context, Uri videoUri,
                                    List<String> captions, File destination,
                                    ResultListener listener) {
        if (videoUri == null || destination == null || captions == null ||
                captions.isEmpty()) throw new IllegalArgumentException("Need video and captions");
        List<Effect> videoEffects = Collections.<Effect>singletonList(
            new OverlayEffect(Collections.singletonList(new CaptionCanvas(captions))));
        Effects effects = new Effects(Collections.emptyList(), videoEffects);
        EditedMediaItem item = new EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
                .setEffects(effects)
                .build();
        Transformer transformer = new Transformer.Builder(context)
                .addListener(new Transformer.Listener() {
                    @Override public void onCompleted(Composition composition, ExportResult result) {
                        listener.onComplete(destination);
                    }
                    @Override public void onError(Composition composition,
                                                  ExportResult result, ExportException error) {
                        listener.onError("Subtitle video processing failed: " + error.getMessage());
                    }
                })
                .build();
        transformer.start(item, destination.getAbsolutePath());
        return transformer;
    }
}
