package com.kp.recapstudio;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Selects representative spoken-dialogue lines from beginning, middle and
 * end of an existing transcript. It does NOT understand the film plot,
 * generate new text, translate dialogue or identify speakers.
 *
 * Pure Java; local/zero-network and independently testable.
 */
public final class OfflineDialogueDigest {
    private OfflineDialogueDigest() {}

    public static String selectHighlights(String transcript,
                                          int maxLines, int maxChars) {
        if (transcript == null || transcript.trim().isEmpty()) return "";
        if (maxLines < 1 || maxChars < 40)
            throw new IllegalArgumentException("Invalid summary limits");

        String[] raw = transcript.replace('\r', '\n').split("[။.!?\\n]+");
        List<String> lines = new ArrayList<>();
        for (String part : raw) {
            String line = part.replaceAll("\\s+", " ").trim();
            if (line.length() < 2) continue;
            if (line.length() > 190) {
                int end = 190;
                if (Character.isHighSurrogate(line.charAt(end - 1))) end--;
                line = line.substring(0, end).trim();
            }
            if (!line.isEmpty()) lines.add(line);
        }
        if (lines.isEmpty()) return "";
        int wanted = Math.min(maxLines, lines.size());
        LinkedHashSet<Integer> selected = new LinkedHashSet<>();
        for (int i = 0; i < wanted; i++) {
            int index = wanted == 1 ? 0 :
                (int)Math.round((double)i * (lines.size() - 1) / (wanted - 1));
            selected.add(index);
        }
        StringBuilder result = new StringBuilder();
        for (int index : selected) {
            String piece = lines.get(index);
            if (result.length() > 0 && result.length() + piece.length() + 3 > maxChars)
                break;
            if (piece.length() > maxChars - result.length()) {
                if (result.length() > 0) break;
                int end = maxChars;
                if (end < piece.length() && Character.isHighSurrogate(piece.charAt(end - 1)))
                    end--;
                piece = piece.substring(0, end).trim();
            }
            if (result.length() > 0) result.append("။\n");
            result.append(piece);
        }
        return result.toString();
    }
}
