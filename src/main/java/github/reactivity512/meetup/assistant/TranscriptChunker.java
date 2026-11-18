package github.reactivity512.meetup.assistant;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits long transcripts into model-sized chunks at sentence or word boundaries.
 */
public final class TranscriptChunker {

    private TranscriptChunker() {
    }

    public static List<String> chunk(String text, int maxChars, int maxChunks) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return List.of(normalized);
        }
        if (maxChars < 100) {
            throw new IllegalArgumentException("chunk size is too small");
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length() && chunks.size() < maxChunks) {
            int end = Math.min(start + maxChars, normalized.length());
            if (end < normalized.length()) {
                end = findBreak(normalized, start, end);
            }
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start = end;
        }
        return List.copyOf(chunks);
    }

    private static int findBreak(String text, int start, int limit) {
        int min = start + Math.max(50, (limit - start) / 3);
        for (int i = limit - 1; i >= min; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }
        int space = text.lastIndexOf(' ', limit);
        return space > min ? space : limit;
    }
}
