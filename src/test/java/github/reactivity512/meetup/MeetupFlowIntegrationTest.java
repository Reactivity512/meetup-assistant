package github.reactivity512.meetup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import github.reactivity512.meetup.assistant.AssistantService;
import github.reactivity512.meetup.assistant.LlmClient;
import github.reactivity512.meetup.domain.LlmAnswer;
import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.domain.MeetupStatus;
import github.reactivity512.meetup.repository.LlmAnswerRepository;
import github.reactivity512.meetup.repository.MeetupRepository;
import github.reactivity512.meetup.service.MeetupService;
import github.reactivity512.meetup.service.TranscriptionCoordinator;
import github.reactivity512.meetup.speech.TranscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeetupFlowIntegrationTest {

    private static final Path STORAGE_DIR = createTempDirectory();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.dir", () -> STORAGE_DIR.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeetupService meetupService;

    @Autowired
    private TranscriptionCoordinator transcriptionCoordinator;

    @Autowired
    private AssistantService assistantService;

    @Autowired
    private MeetupRepository meetupRepository;

    @Autowired
    private LlmAnswerRepository answerRepository;

    @MockitoBean
    private TranscriptionService transcriptionService;

    @MockitoBean
    private LlmClient llmClient;

    @BeforeEach
    void cleanDatabase() {
        answerRepository.deleteAll();
        meetupRepository.deleteAll();
    }

    @Test
    void transcriptIsStoredAndUsedForQuestionAnswering() throws Exception {
        when(transcriptionService.transcribe(any()))
                .thenReturn("На встрече решили перенести релиз на пятницу. Петров подготовит план миграции.");
        when(llmClient.modelName()).thenReturn("test-model");
        when(llmClient.complete(anyString(), anyString()))
                .thenReturn("Релиз перенесли на пятницу. Петров готовит план миграции.");

        MockMultipartFile file = new MockMultipartFile(
                "file", "meeting.wav", "audio/wav", new byte[]{1, 2, 3, 4});
        Meetup meetup = meetupService.create("Планирование релиза", file);
        assertThat(meetup.getStatus()).isEqualTo(MeetupStatus.QUEUED);
        assertThat(meetupService.isAudioAvailable(meetup)).isTrue();

        transcriptionCoordinator.process(meetup.getId());

        Meetup completed = meetupService.get(meetup.getId());
        assertThat(completed.getStatus()).isEqualTo(MeetupStatus.READY);
        assertThat(completed.getTranscript()).contains("перенести релиз");
        assertThat(completed.getTranscribedAt()).isNotNull();

        LlmAnswer answer = assistantService.ask(meetup.getId(), "Какие решения приняли?");
        assertThat(answer.getId()).isNotNull();
        assertThat(answer.getAnswer()).contains("пятницу");
        assertThat(answer.getModel()).isEqualTo("test-model");

        mockMvc.perform(get("/meetups"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Планирование релиза")));

        mockMvc.perform(get("/meetups/{id}", meetup.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("перенести релиз")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Релиз перенесли на пятницу")));

        mockMvc.perform(get("/api/meetups/{id}/status", meetup.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"READY\"")));

        mockMvc.perform(get("/meetups/{id}/audio", meetup.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("audio/wav"));
    }

    @Test
    void uploadEndpointQueuesTranscriptionAsynchronously() throws Exception {
        when(transcriptionService.transcribe(any())).thenReturn("Короткий транскрипт встречи.");

        MockMultipartFile file = new MockMultipartFile(
                "file", "meeting.mp3", "audio/mpeg", new byte[]{9, 8, 7});

        mockMvc.perform(multipart("/meetups").file(file).param("title", "Асинхронная встреча"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/meetups/*"));

        List<Meetup> meetups = meetupRepository.findAll();
        assertThat(meetups).hasSize(1);
        Long id = meetups.get(0).getId();

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(meetupService.get(id).getStatus())
                        .isEqualTo(MeetupStatus.READY));

        assertThat(meetupService.get(id).getTranscript()).isEqualTo("Короткий транскрипт встречи.");
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("meetup-assistant-it-");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create temp directory", e);
        }
    }
}
