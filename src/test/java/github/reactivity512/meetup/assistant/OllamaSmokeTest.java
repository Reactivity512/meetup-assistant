package github.reactivity512.meetup.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Optional smoke test against a real local Ollama instance. Skipped by default so the
 * regular build does not depend on Ollama; enable it with:
 *
 * <pre>RUN_OLLAMA_SMOKE=true mvn -Dtest=OllamaSmokeTest test</pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.ai.ollama.chat.options.model=qwen3.5:4b")
@EnabledIfEnvironmentVariable(named = "RUN_OLLAMA_SMOKE", matches = "true")
class OllamaSmokeTest {

    @Autowired
    private LlmClient llmClient;

    @Test
    void answersThroughSpringAi() {
        String answer = llmClient.complete(
                "Отвечай максимально кратко, одним словом.",
                "Столица Франции?");

        assertThat(llmClient.modelName()).isEqualTo("qwen3.5:4b");
        assertThat(answer).isNotBlank();
        assertThat(answer.toLowerCase()).contains("париж");
    }
}
