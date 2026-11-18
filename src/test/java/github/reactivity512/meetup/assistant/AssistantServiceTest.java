package github.reactivity512.meetup.assistant;

import java.util.Optional;

import github.reactivity512.meetup.config.AssistantProperties;
import github.reactivity512.meetup.domain.LlmAnswer;
import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.domain.MeetupStatus;
import github.reactivity512.meetup.repository.LlmAnswerRepository;
import github.reactivity512.meetup.repository.MeetupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock
    private MeetupRepository meetupRepository;

    @Mock
    private LlmAnswerRepository answerRepository;

    @Mock
    private LlmClient llmClient;

    private AssistantProperties properties;
    private AssistantService assistantService;

    @BeforeEach
    void setUp() {
        properties = new AssistantProperties();
        properties.setChunkSize(10_000);
        properties.setMaxChunks(5);
        properties.setMaxQuestionLength(100);
        assistantService = new AssistantService(meetupRepository, answerRepository, llmClient, properties);
    }

    @Test
    void answersQuestionUsingTranscriptAndSavesAnswer() {
        Meetup meetup = readyMeetup("На встрече решили перейти на новый релизный цикл.");
        when(meetupRepository.findById(1L)).thenReturn(Optional.of(meetup));
        when(llmClient.complete(anyString(), anyString())).thenReturn("Перешли на новый релизный цикл.");
        when(llmClient.modelName()).thenReturn("test-model");
        when(answerRepository.save(any(LlmAnswer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LlmAnswer answer = assistantService.ask(1L, "  Какие решения приняли?  ");

        assertThat(answer.getQuestion()).isEqualTo("Какие решения приняли?");
        assertThat(answer.getAnswer()).isEqualTo("Перешли на новый релизный цикл.");
        assertThat(answer.getModel()).isEqualTo("test-model");
        assertThat(answer.getLatencyMs()).isNotNull();
        assertThat(answer.getMeetup()).isSameAs(meetup);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(anyString(), prompt.capture());
        assertThat(prompt.getValue())
                .contains("Какие решения приняли?")
                .contains("перейти на новый релизный цикл");
    }

    @Test
    void longTranscriptIsProcessedInChunksThenCombined() {
        properties.setChunkSize(100);
        String transcript = ("Обсуждали релиз. Приняли решение перенести запуск. "
                + "Петров подготовит план. ").repeat(10);
        Meetup meetup = readyMeetup(transcript);
        meetup.setId(2L);
        when(meetupRepository.findById(2L)).thenReturn(Optional.of(meetup));
        when(llmClient.modelName()).thenReturn("test-model");
        when(llmClient.complete(anyString(), anyString())).thenAnswer(invocation -> {
            String system = invocation.getArgument(0);
            return system.contains("фрагмент транскрипта")
                    ? "Перенесли запуск, Петров готовит план."
                    : "Запуск перенесён, план готовит Петров.";
        });
        when(answerRepository.save(any(LlmAnswer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LlmAnswer answer = assistantService.ask(2L, "Что решили?");

        assertThat(answer.getAnswer()).contains("Запуск перенесён");
        verify(llmClient, atLeast(2)).complete(anyString(), anyString());
        verify(llmClient, atLeast(1)).complete(contains("фрагмент транскрипта"), anyString());
    }

    @Test
    void rejectsMeetupWithoutTranscript() {
        Meetup meetup = new Meetup();
        meetup.setId(3L);
        meetup.setTitle("Встреча");
        meetup.setStatus(MeetupStatus.PROCESSING);
        when(meetupRepository.findById(3L)).thenReturn(Optional.of(meetup));

        assertThatThrownBy(() -> assistantService.ask(3L, "Что решили?"))
                .isInstanceOf(AssistantException.class)
                .hasMessageContaining("Транскрипт ещё не готов");
    }

    @Test
    void rejectsBlankQuestion() {
        assertThatThrownBy(() -> assistantService.ask(1L, "   "))
                .isInstanceOf(AssistantException.class)
                .hasMessageContaining("Введите вопрос");
    }

    @Test
    void rejectsTooLongQuestion() {
        assertThatThrownBy(() -> assistantService.ask(1L, "очень длинный вопрос ".repeat(5)))
                .isInstanceOf(AssistantException.class)
                .hasMessageContaining("слишком длинный");
    }

    @Test
    void wrapsLlmFailuresWithActionableMessage() {
        Meetup meetup = readyMeetup("Текст встречи");
        when(meetupRepository.findById(1L)).thenReturn(Optional.of(meetup));
        when(llmClient.modelName()).thenReturn("test-model");
        when(llmClient.complete(anyString(), anyString()))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> assistantService.ask(1L, "Что решили?"))
                .isInstanceOf(AssistantException.class)
                .hasMessageContaining("Ollama")
                .hasMessageContaining("Connection refused");
    }

    private static Meetup readyMeetup(String transcript) {
        Meetup meetup = new Meetup();
        meetup.setId(1L);
        meetup.setTitle("Встреча");
        meetup.setStatus(MeetupStatus.READY);
        meetup.setTranscript(transcript);
        return meetup;
    }
}
