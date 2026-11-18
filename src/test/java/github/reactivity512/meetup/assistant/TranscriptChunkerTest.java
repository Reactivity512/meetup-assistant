package github.reactivity512.meetup.assistant;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptChunkerTest {

    @Test
    void returnsSingleChunkForShortText() {
        List<String> chunks = TranscriptChunker.chunk("Короткий текст встречи.", 100, 10);

        assertThat(chunks).containsExactly("Короткий текст встречи.");
    }

    @Test
    void splitsLongTextAtSentenceBoundaries() {
        String sentence = "Обсудили важный вопрос и приняли решение. ";
        String text = sentence.repeat(30);

        List<String> chunks = TranscriptChunker.chunk(text, 120, 100);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(120));
        assertThat(String.join(" ", chunks).replaceAll("\\s+", " "))
                .isEqualTo(text.trim().replaceAll("\\s+", " "));
    }

    @Test
    void respectsMaximumChunkCount() {
        String text = "Слово ".repeat(1000);

        List<String> chunks = TranscriptChunker.chunk(text, 100, 3);

        assertThat(chunks).hasSize(3);
    }

    @Test
    void handlesBlankText() {
        assertThat(TranscriptChunker.chunk("  ", 100, 10)).isEmpty();
        assertThat(TranscriptChunker.chunk(null, 100, 10)).isEmpty();
    }

    @Test
    void rejectsTooSmallChunkSize() {
        assertThatThrownBy(() -> TranscriptChunker.chunk("a".repeat(500), 50, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
