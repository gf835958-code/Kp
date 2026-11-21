package com.kp.movierecap;

import android.content.Context;
import android.net.Uri;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import java.text.BreakIterator;
import java.util.Locale;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import androidx.media3.transformer.Effects;
import androidx.media3.common.MediaItem;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.StaticOverlaySettings;
import androidx.media3.effect.TextOverlay;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.EditedMediaItemSequence;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** On-device subtitle burn-in for scene clips. Captions are visual notes, NOT movie dialog. */
public final class CaptionedVideoExporter {
    public interface Callback {
        void onSuccess(File output);
        void onFailure(Exception exception);
        default void onProgress(int percentage) { }
    }
    private Transformer transformer;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressPoller;
    /** Video-scaled Burmese caption rendering with grapheme-aware line wraps. */
    private static int[] sourceSize(Context context, Uri input) {
        MediaMetadataRetriever reader = new MediaMetadataRetriever();
        try {
            reader.setDataSource(context, input);
            String width = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            int w = width == null ? 720 : Integer.parseInt(width);
            int h = height == null ? 480 : Integer.parseInt(height);
            return new int[]{Math.max(96, w), Math.max(72, h)};
        } catch (Exception ignored) {
            return new int[]{720, 480};
        } finally {
            try { reader.release(); } catch (Exception ignored) { }
        }
    }

    private static String wrapMyanmarCaption(String caption, int maxClusters) {
        BreakIterator chars = BreakIterator.getCharacterInstance(Locale.forLanguageTag("my"));
        chars.setText(caption);
        StringBuilder out = new StringBuilder();
        int start = chars.first();
        int end;
        int inLine = 0, line = 1;
        final int maxLines = 4;
        boolean truncated = false;
        while ((end = chars.next()) != BreakIterator.DONE) {
            String part = caption.substring(start, end);
            start = end;
            if ("\n".equals(part) || "\r".equals(part)) part = " ";
            if (inLine >= maxClusters) {
                if (line >= maxLines) { truncated = true; break; }
                out.append('\n');
                line++;
                inLine = 0;
                if (part.trim().isEmpty()) continue;
            }
            out.append(part);
            inLine++;
        }
        String result = out.toString().trim();
        if (truncated && !result.endsWith("…")) result += "…";
        return result;
    }

    public void start(Context context, Uri input,
            List<VideoHighlightsExporter.Range> ranges, List<String> captions,
            File narration, File output, Callback callback) {
        if (input == null || ranges == null || captions == null || narration == null || !narration.isFile() || ranges.isEmpty()
                || ranges.size() > 16 || ranges.size() != captions.size()) {
            throw new IllegalArgumentException("Invalid captioned export input");
        }
        if (output.exists() && !output.delete()) {
            throw new IllegalStateException("Cannot overwrite temporary export");
        }
        int[] dimensions = sourceSize(context, input);
        float scale = Math.min(0.42f, Math.max(0.09f,
            Math.min(dimensions[0] / 700.0f, dimensions[1] / 500.0f) * 0.42f));
        int clustersPerLine = Math.max(12, Math.min(34,
            Math.round(dimensions[0] / (scale * 90f))));
        List<EditedMediaItem> edited = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) {
            VideoHighlightsExporter.Range r = ranges.get(i);
            if (r.startUs < 0 || r.endUs <= r.startUs) {
                throw new IllegalArgumentException("Invalid clip range");
            }
            MediaItem media = new MediaItem.Builder()
                .setUri(input)
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(r.startUs / 1000)
                    .setEndPositionMs(r.endUs / 1000)
                    .build())
                .build();
            String caption = captions.get(i) == null ? "" : captions.get(i).trim();
            EditedMediaItem.Builder builder = new EditedMediaItem.Builder(media);
            if (!caption.isEmpty()) {
                caption = caption.replaceAll("[\\r\\n\\t]+", " ");
                if (caption.length() > 130) caption = caption.substring(0, 130) + "…";
                SpannableString text = new SpannableString(wrapMyanmarCaption(caption, clustersPerLine));
                text.setSpan(new ForegroundColorSpan(Color.WHITE), 0,
                    text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new BackgroundColorSpan(0xC0000000), 0,
                    text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new RelativeSizeSpan(scale), 0,
                    text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                StaticOverlaySettings settings = new StaticOverlaySettings.Builder()
                    .setOverlayFrameAnchor(0f, -1f)
                    .setBackgroundFrameAnchor(0f, -0.84f)
                    .build();
                TextOverlay overlay = TextOverlay.createStaticTextOverlay(text, settings);
                builder.setEffects(new Effects(Collections.emptyList(),
                    Collections.singletonList(new OverlayEffect(Collections.singletonList(overlay)))));
            }
            edited.add(builder.build());
        }
        // A separate audio-only sequence is the speech narration track.
        // Source audio is excluded to keep speech intelligible.
        EditedMediaItemSequence video = EditedMediaItemSequence.withVideoFrom(edited);
        EditedMediaItem narrated = new EditedMediaItem.Builder(
            MediaItem.fromUri(Uri.fromFile(narration))).build();
        EditedMediaItemSequence voice = EditedMediaItemSequence.withAudioFrom(
            Collections.singletonList(narrated));
        Composition composition = new Composition.Builder(video, voice).build();
        transformer = new Transformer.Builder(context)
            .addListener(new Transformer.Listener() {
                @Override public void onCompleted(Composition composition, ExportResult result) {
                    if (progressPoller != null) progressHandler.removeCallbacks(progressPoller);
                    transformer = null;
                    callback.onSuccess(output);
                }
                @Override public void onError(Composition composition, ExportResult result,
                        ExportException exception) {
                    if (progressPoller != null) progressHandler.removeCallbacks(progressPoller);
                    transformer = null;
                    if (output.exists()) output.delete();
                    callback.onFailure(exception);
                }
            }).build();
        transformer.start(composition, output.getAbsolutePath());
        // Query REAL Media3 render percentage; never show fake completion
        // just because scanning / transcription has finished.
        final ProgressHolder progress = new ProgressHolder();
        progressPoller = new Runnable() {
            @Override public void run() {
                Transformer current = transformer;
                if (current == null) return;
                try {
                    int state = current.getProgress(progress);
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE)
                        callback.onProgress(Math.max(0, Math.min(99, progress.progress)));
                } catch (RuntimeException ignored) { /* some codecs don't expose progress */ }
                if (transformer == current)
                    progressHandler.postDelayed(this, 800);
            }
        };
        progressHandler.post(progressPoller);
    }
}
