package github.reactivity512.meetup.assistant;

import java.util.List;

import github.reactivity512.meetup.config.AssistantProperties;
import github.reactivity512.meetup.domain.LlmAnswer;
import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.domain.MeetupStatus;
import github.reactivity512.meetup.repository.LlmAnswerRepository;
import github.reactivity512.meetup.repository.MeetupRepository;
import github.reactivity512.meetup.service.MeetupNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Answers questions about a meetup transcript.
 *
 * <p>Short transcripts are sent to the LLM in one request. Long transcripts are split
 * into chunks; for every chunk the LLM extracts question-relevant facts, and a final
 * call composes the answer. This keeps each request inside the model context window.</p>
 *
 * <p>Deliberately not wrapped in one long transaction: LLM calls can take minutes and
 * must not hold a database connection.</p>
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final String EXTRACT_SYSTEM = """
            Ты анализируешь фрагмент транскрипта рабочей встречи.
            Найди во фрагменте только ту информацию, которая относится к вопросу пользователя:
            решения, договорённости, задачи, сроки, ответственных, цифры и факты.
            Не отвечай на вопрос целиком и не делай выводов. Выпиши найденные факты короткими пунктами.
            Если по теме во фрагменте ничего нет, ответь ровно: НЕТ_ДАННЫХ.
            Пиши по-русски.
            """;

    private static final String ANSWER_SYSTEM = """
            Ты — корпоративный ассистент, который отвечает на вопросы по транскрипту рабочей встречи.
            Правила:
            1. Опирайся только на предоставленный текст встречи и выдержки из него.
            2. Если информации недостаточно, прямо скажи, что это не обсуждалось или не зафиксировано
               в записи. Не выдумывай имена, решения, сроки и цифры.
            3. Учитывай, что транскрипт получен автоматическим распознаванием и может содержать
               ошибки, повторы и неверные имена.
            4. Отвечай по-русски: сначала короткий вывод, затем структурированные пункты.
            5. Не упоминай процесс анализа фрагментов и не пересказывай весь транскрипт.
            """;

    private final MeetupRepository meetupRepository;
    private final LlmAnswerRepository answerRepository;
    private final LlmClient llmClient;
    private final AssistantProperties properties;

    public AssistantService(MeetupRepository meetupRepository,
                            LlmAnswerRepository answerRepository,
                            LlmClient llmClient,
                            AssistantProperties properties) {
        this.meetupRepository = meetupRepository;
        this.answerRepository = answerRepository;
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public LlmAnswer ask(Long meetupId, String rawQuestion) {
        String question = validateQuestion(rawQuestion);
        Meetup meetup = meetupRepository.findById(meetupId)
                .orElseThrow(() -> new MeetupNotFoundException(meetupId));

        if (meetup.getStatus() != MeetupStatus.READY || meetup.getTranscript() == null
                || meetup.getTranscript().isBlank()) {
            throw new AssistantException("Транскрипт ещё не готов. Дождитесь окончания распознавания.");
        }

        long startedAt = System.currentTimeMillis();
        String context = buildContext(meetup.getTranscript(), question);

        String answer;
        try {
            answer = llmClient.complete(ANSWER_SYSTEM, buildAnswerPrompt(question, context));
        } catch (AssistantException e) {
            throw e;
        } catch (Exception e) {
            throw new AssistantException(
                    "Не удалось получить ответ от LLM (модель " + llmClient.modelName() + "). "
                            + "Проверьте, что Ollama запущена и модель загружена (ollama list). "
                            + "Причина: " + describe(e), e);
        }

        LlmAnswer entity = new LlmAnswer();
        entity.setMeetup(meetup);
        entity.setQuestion(question);
        entity.setAnswer(answer);
        entity.setModel(llmClient.modelName());
        entity.setLatencyMs(System.currentTimeMillis() - startedAt);
        LlmAnswer saved = answerRepository.save(entity);
        log.info("Answered question for meetup {} in {} ms using {}",
                meetupId, entity.getLatencyMs(), llmClient.modelName());
        return saved;
    }

    private String buildContext(String transcript, String question) {
        List<String> chunks = TranscriptChunker.chunk(
                transcript, properties.getChunkSize(), properties.getMaxChunks());
        if (chunks.isEmpty()) {
            throw new AssistantException("Транскрипт встречи пуст");
        }
        if (chunks.size() == 1) {
            return chunks.get(0);
        }

        log.info("Transcript is split into {} chunks; extracting relevant facts", chunks.size());
        int maxExtractLength = Math.max(600, properties.getChunkSize() / 4);
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            String extract = llmClient.complete(EXTRACT_SYSTEM,
                    "Вопрос: " + question + "\n\nФрагмент транскрипта №" + (i + 1) + ":\n\"\"\""
                            + chunks.get(i) + "\"\"\"");
            if (extract.isBlank() || extract.toUpperCase().contains("НЕТ_ДАННЫХ")) {
                continue;
            }
            if (extract.length() > maxExtractLength) {
                extract = extract.substring(0, maxExtractLength) + "…";
            }
            if (context.length() + extract.length() > properties.getChunkSize()) {
                break;
            }
            if (!context.isEmpty()) {
                context.append("\n\n");
            }
            context.append("Фрагмент ").append(i + 1).append(":\n").append(extract);
        }

        if (context.isEmpty()) {
            return "По вопросу не найдено релевантных фрагментов в стенограмме.";
        }
        return context.toString();
    }

    private static String buildAnswerPrompt(String question, String context) {
        return """
                Вопрос: %s

                Материалы встречи:
                \"\"\"
                %s
                \"\"\"
                """.formatted(question, context);
    }

    private String validateQuestion(String rawQuestion) {
        if (rawQuestion == null || rawQuestion.isBlank()) {
            throw new AssistantException("Введите вопрос");
        }
        String question = rawQuestion.trim();
        if (question.length() > properties.getMaxQuestionLength()) {
            throw new AssistantException("Вопрос слишком длинный (максимум "
                    + properties.getMaxQuestionLength() + " символов)");
        }
        return question;
    }

    private static String describe(Throwable throwable) {
        Throwable current = throwable;
        String message = current.getMessage();
        while ((message == null || message.isBlank()) && current.getCause() != null) {
            current = current.getCause();
            message = current.getMessage();
        }
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
