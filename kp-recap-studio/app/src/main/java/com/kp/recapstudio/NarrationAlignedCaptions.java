package com.kp.recapstudio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * More accurate offline captions from the ACTUAL lengths of individual
 * on-device TTS synthesis utterances. Start/end times at utterance boundaries
 * are measured, not guessed from character counts.
 *
 * Inside each utterance, timings are still estimates proportional to written
 * characters, NOT forced speech-to-word alignment.
 */
public final class NarrationAlignedCaptions {
    private NarrationAlignedCaptions() {}

    public static List<CaptionTimeline.Cue> plan(
            List<String> speechChunks, List<Long> wavDurationsUs,
            long playbackDurationUs, int maxCharactersPerCue) {
        if (speechChunks == null || wavDurationsUs == null ||
                speechChunks.size() != wavDurationsUs.size())
            throw new IllegalArgumentException("TTS text and audio chunk counts differ");
        if (speechChunks.isEmpty()) return Collections.emptyList();
        if (playbackDurationUs <= 0)
            throw new IllegalArgumentException("Playback duration is required");
        long totalRawDuration = 0;
        for (Long micros : wavDurationsUs) {
            if (micros == null || micros <= 0)
                throw new IllegalArgumentException("Invalid TTS utterance length");
            totalRawDuration = Math.addExact(totalRawDuration, micros);
        }
        if (totalRawDuration <= 0)
            throw new IllegalArgumentException("No synthesized audio");

        ArrayList<CaptionTimeline.Cue> output = new ArrayList<>();
        long runningRaw = 0;
        for (int i = 0; i < speechChunks.size(); i++) {
            long start = (long)((double)runningRaw / totalRawDuration *
                playbackDurationUs);
            runningRaw += wavDurationsUs.get(i);
            long end = i == speechChunks.size() - 1
                ? playbackDurationUs : (long)((double)runningRaw /
                    totalRawDuration * playbackDurationUs);
            end = Math.max(start + 1, end);
            String text = speechChunks.get(i);
            if (text == null || text.trim().isEmpty()) continue;

            for (CaptionTimeline.Cue cue :
                    CaptionTimeline.plan(text, end - start, maxCharactersPerCue)) {
                output.add(new CaptionTimeline.Cue(
                    cue.text, start + cue.startUs,
                    Math.min(playbackDurationUs, start + cue.endUs)));
            }
        }
        return output;
    }
}
