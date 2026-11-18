package github.reactivity512.meetup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for transcript processing before it is sent to the LLM.
 *
 * <p>Ollama models have a limited context window, so long transcripts are processed
 * in chunks: the model extracts question-relevant fragments from every chunk, and
 * a final call produces the answer.</p>
 */
@ConfigurationProperties(prefix = "app.assistant")
public class AssistantProperties {

    /** Maximum number of transcript characters sent to the model in one request. */
    private int chunkSize = 6000;

    /** Safety cap for very long recordings. */
    private int maxChunks = 20;

    /** Maximum length of a user question. */
    private int maxQuestionLength = 1000;

    /** Number of answers shown on the meetup page. */
    private int historyLimit = 50;

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getMaxChunks() {
        return maxChunks;
    }

    public void setMaxChunks(int maxChunks) {
        this.maxChunks = maxChunks;
    }

    public int getMaxQuestionLength() {
        return maxQuestionLength;
    }

    public void setMaxQuestionLength(int maxQuestionLength) {
        this.maxQuestionLength = maxQuestionLength;
    }

    public int getHistoryLimit() {
        return historyLimit;
    }

    public void setHistoryLimit(int historyLimit) {
        this.historyLimit = historyLimit;
    }
}
