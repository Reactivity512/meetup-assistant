package github.reactivity512.meetup.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Spring AI implementation backed by a local Ollama instance
 * (configured with {@code spring.ai.ollama.*}).
 */
@Service
public class SpringAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmClient.class);

    private final ChatClient chatClient;
    private final String modelName;

    public SpringAiLlmClient(ChatClient.Builder chatClientBuilder,
                             @Value("${spring.ai.ollama.chat.options.model:unknown}") String modelName) {
        this.chatClient = chatClientBuilder.build();
        this.modelName = modelName;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        log.debug("Calling Ollama model {} ({} chars in prompt)", modelName, userPrompt.length());
        String content = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
        if (content == null || content.isBlank()) {
            throw new AssistantException("Ollama вернула пустой ответ");
        }
        return content.trim();
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
