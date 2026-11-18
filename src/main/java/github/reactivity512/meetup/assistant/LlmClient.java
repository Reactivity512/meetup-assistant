package github.reactivity512.meetup.assistant;

/**
 * Thin abstraction over Spring AI, so business logic stays testable without an LLM.
 */
public interface LlmClient {

    String complete(String systemPrompt, String userPrompt);

    String modelName();
}
