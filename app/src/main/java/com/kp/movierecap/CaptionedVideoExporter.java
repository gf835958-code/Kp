package com.kp.movierecap;

import android.content.Context;
import android.net.Uri;
import android.graphics.Color;
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
    }
    private Transformer transformer;
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
                SpannableString text = new SpannableString(caption);
                text.setSpan(new ForegroundColorSpan(Color.WHITE), 0,
                    text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new BackgroundColorSpan(0xC0000000), 0,
                    text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new RelativeSizeSpan(0.42f), 0,
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
                    transformer = null;
                    callback.onSuccess(output);
                }
                @Override public void onError(Composition composition, ExportResult result,
                        ExportException exception) {
                    transformer = null;
                    if (output.exists()) output.delete();
                    callback.onFailure(exception);
                }
            }).build();
        transformer.start(composition, output.getAbsolutePath());
    }
}
