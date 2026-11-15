package com.kp.recapstudio;

import org.junit.Test;
import static org.junit.Assert.*;

/** Checks that transcript summaries cover the full source, not just the opening. */
public class OfflineDialogueDigestTest {
    @Test public void picksBeginningMiddleEndChronologically() {
        StringBuilder transcript = new StringBuilder();
        for (int i = 0; i < 30; i++) transcript.append("အပိုင်း").append(i)
            .append(" မှာ စကားပြောတယ်။");
        String highlights = OfflineDialogueDigest.selectHighlights(
            transcript.toString(), 6, 1800);
        assertTrue(highlights.contains("အပိုင်း0"));
        assertTrue(highlights.contains("အပိုင်း29"));
        assertTrue(highlights.contains("အပိုင်း12") ||
                   highlights.contains("အပိုင်း11") ||
                   highlights.contains("အပိုင်း17"));
        assertTrue(highlights.indexOf("အပိုင်း0") < highlights.indexOf("အပိုင်း29"));
    }

    @Test public void keepsBurmeseUnicodeAndCharacterBudget() {
        String out = OfflineDialogueDigest.selectHighlights(
            "မင်္ဂလာပါ။ မောင်လေး နေကောင်းလား။ ပြီးဆုံးပါပြီ။", 2, 45);
        assertFalse(out.isEmpty());
        assertTrue(out.length() <= 45);
    }

    @Test public void handlesEmptyAudioTranscript() {
        assertEquals("", OfflineDialogueDigest.selectHighlights(null, 14, 2800));
        assertEquals("", OfflineDialogueDigest.selectHighlights("    ", 14, 2800));
    }

    @Test(expected = IllegalArgumentException.class)
    public void refusesInvalidBudget() {
        OfflineDialogueDigest.selectHighlights("Hello world.", 0, 20);
    }
}
