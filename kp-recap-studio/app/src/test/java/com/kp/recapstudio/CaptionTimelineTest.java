package com.kp.recapstudio;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

/** Pure-JVM tests; no Android emulator required. */
public class CaptionTimelineTest {
    @Test public void emptyScriptProducesNoCues() {
        assertTrue(CaptionTimeline.plan("  ", 10000000L, 60).isEmpty());
        assertEquals("", CaptionTimeline.toSrt(CaptionTimeline.plan("", 0, 60)));
    }

    @Test public void burmeseIsPreservedAndTimestampsMonotonic() {
        String script = "မင်္ဂလာပါ။ ဒီဇာတ်လမ်းကို အစကနေ အဆုံးအထိ ပြောပြပါမယ်။ " +
            "နောက်ဆုံး အဆုံးသတ်ပါမယ်။";
        List<CaptionTimeline.Cue> cues = CaptionTimeline.plan(script, 45000000L, 35);
        assertTrue(cues.size() > 1);
        long cursor = 0;
        StringBuilder restored = new StringBuilder();
        for (CaptionTimeline.Cue cue : cues) {
            assertTrue(cue.startUs >= cursor);
            assertTrue(cue.endUs > cue.startUs);
            assertTrue(cue.endUs <= 45000000L);
            cursor = cue.endUs;
            restored.append(cue.text);
        }
        assertEquals(45000000L, cursor);
        assertTrue(restored.toString().contains("မင်္ဂလာပါ"));
        assertTrue(CaptionTimeline.toSrt(cues).contains("00:00:45,000"));
    }

    @Test public void proportionalDurationUsesFullAudioSpan() {
        List<CaptionTimeline.Cue> cues =
            CaptionTimeline.plan("short. This is a substantially longer sentence.", 12000000L, 12);
        assertTrue(cues.size() >= 2);
        assertEquals(0L, cues.get(0).startUs);
        assertEquals(12000000L, cues.get(cues.size() - 1).endUs);
        for (int i = 1; i < cues.size(); i++) {
            assertEquals(cues.get(i - 1).endUs, cues.get(i).startUs);
        }
    }

    @Test public void staysOnSurrogateBoundary() {
        String script = "😊😊😊😊😊😊😊😊😊😊😊😊😊😊😊😊😊😊😊";
        List<String> chunks = CaptionTimeline.split(script, 13);
        for (String chunk : chunks) {
            assertFalse(Character.isLowSurrogate(chunk.charAt(0)));
            assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)));
        }
    }

    @Test public void fallsBackToFiveSecondsOnlyWithoutDuration() {
        List<CaptionTimeline.Cue> cues = CaptionTimeline.plan("Alpha beta gamma delta.", -1, 15);
        assertFalse(cues.isEmpty());
        assertEquals(cues.size() * 5000000L, cues.get(cues.size()-1).endUs);
    }
}
