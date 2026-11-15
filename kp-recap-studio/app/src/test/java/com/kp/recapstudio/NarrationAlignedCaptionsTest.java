package com.kp.recapstudio;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/**
 * Subtitle regressions: cue timing should follow real synthesis durations,
 * preserving Burmese text and not clipping the last utterance.
 */
public class NarrationAlignedCaptionsTest {
    @Test public void followsMeasuredTtsUtteranceBoundaries() {
        List<CaptionTimeline.Cue> cues = NarrationAlignedCaptions.plan(
            Arrays.asList("မင်္ဂလာပါ မြန်မာစာ", "သင်ခန်းစာ ဒုတိယပိုင်း"),
            Arrays.asList(2_000_000L, 8_000_000L), 10_000_000L, 70);
        assertEquals(2, cues.size());
        assertEquals(0L, cues.get(0).startUs);
        assertEquals(2_000_000L, cues.get(0).endUs);
        assertEquals(2_000_000L, cues.get(1).startUs);
        assertEquals(10_000_000L, cues.get(1).endUs);
        assertTrue(cues.get(0).text.contains("မြန်မာစာ"));
        assertTrue(cues.get(1).text.contains("ဒုတိယ"));
    }

    @Test public void scalesActualTtsTimesToFinalMovieDuration() {
        List<CaptionTimeline.Cue> cues = NarrationAlignedCaptions.plan(
            Arrays.asList("First text.", "Second text."),
            Arrays.asList(2_000_000L, 8_000_000L), 5_000_000L, 80);
        assertEquals(1_000_000L, cues.get(0).endUs);
        assertEquals(1_000_000L, cues.get(1).startUs);
        assertEquals(5_000_000L, cues.get(1).endUs);
    }

    @Test public void emptyChunksCanBeSkippedWithoutChangingLaterOffsets() {
        List<CaptionTimeline.Cue> cues = NarrationAlignedCaptions.plan(
            Arrays.asList("", "နောက်ဆုံးအပိုင်း"),
            Arrays.asList(1_000_000L, 4_000_000L), 5_000_000L, 60);
        assertEquals(1, cues.size());
        assertEquals(1_000_000L, cues.get(0).startUs);
        assertEquals(5_000_000L, cues.get(0).endUs);
    }

    @Test(expected=IllegalArgumentException.class)
    public void neverMixesDifferentTextAndAudioCounts() {
        NarrationAlignedCaptions.plan(Arrays.asList("a", "b"),
            Collections.singletonList(1000000L), 1000000L, 60);
    }
}
