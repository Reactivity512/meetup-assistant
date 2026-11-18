package github.reactivity512.meetup.web;

import java.time.Instant;
import java.util.List;

import github.reactivity512.meetup.assistant.AssistantService;
import github.reactivity512.meetup.config.AssistantProperties;
import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.domain.MeetupStatus;
import github.reactivity512.meetup.domain.MeetupSummary;
import github.reactivity512.meetup.service.MeetupNotFoundException;
import github.reactivity512.meetup.service.MeetupService;
import github.reactivity512.meetup.service.TranscriptionCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MeetupController.class)
class MeetupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeetupService meetupService;

    @MockitoBean
    private AssistantService assistantService;

    @MockitoBean
    private TranscriptionCoordinator transcriptionCoordinator;

    @MockitoBean
    private AssistantProperties assistantProperties;

    @BeforeEach
    void setUp() {
        when(assistantProperties.getHistoryLimit()).thenReturn(50);
        when(assistantProperties.getMaxQuestionLength()).thenReturn(1000);
    }

    @Test
    void listShowsMeetupsTable() throws Exception {
        when(meetupService.listSummaries()).thenReturn(List.of(
                new MeetupSummary(1L, "Планирование", MeetupStatus.READY, Instant.now(), 1234, null)));

        mockMvc.perform(get("/meetups"))
                .andExpect(status().isOk())
                .andExpect(view().name("meetups/list"))
                .andExpect(model().attributeExists("meetups"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Планирование")));
    }

    @Test
    void detailShowsMeetup() throws Exception {
        Meetup meetup = meetup(5L, MeetupStatus.READY);
        meetup.setTranscript("Текст встречи");
        when(meetupService.get(5L)).thenReturn(meetup);
        when(meetupService.answers(5L, 50)).thenReturn(List.of());
        when(meetupService.isAudioAvailable(meetup)).thenReturn(true);

        mockMvc.perform(get("/meetups/5"))
                .andExpect(status().isOk())
                .andExpect(view().name("meetups/detail"))
                .andExpect(model().attribute("meetup", meetup))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Текст встречи")));
    }

    @Test
    void missingMeetupReturns404() throws Exception {
        when(meetupService.get(99L)).thenThrow(new MeetupNotFoundException(99L));

        mockMvc.perform(get("/meetups/99"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"));
    }

    @Test
    void uploadWithoutFileShowsValidationError() throws Exception {
        mockMvc.perform(multipart("/meetups").param("title", "Встреча"))
                .andExpect(status().isOk())
                .andExpect(view().name("meetups/new"))
                .andExpect(model().attributeHasFieldErrors("form", "file"));

        verifyNoInteractions(meetupService);
    }

    @Test
    void uploadWithoutTitleShowsValidationError() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.wav", "audio/wav", new byte[]{1});

        mockMvc.perform(multipart("/meetups").file(file))
                .andExpect(status().isOk())
                .andExpect(view().name("meetups/new"))
                .andExpect(model().attributeHasFieldErrors("form", "title"));
    }

    @Test
    void validUploadSavesMeetupAndStartsTranscription() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.wav", "audio/wav", new byte[]{1, 2});
        Meetup meetup = meetup(7L, MeetupStatus.QUEUED);
        when(meetupService.create(anyString(), any())).thenReturn(meetup);

        mockMvc.perform(multipart("/meetups").file(file).param("title", "Новая встреча"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meetups/7"))
                .andExpect(flash().attributeExists("success"));

        verify(transcriptionCoordinator).processAsync(7L);
    }

    @Test
    void assistantQuestionValidationRedirectsWithError() throws Exception {
        mockMvc.perform(post("/meetups/7/assistant").param("question", "  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meetups/7"))
                .andExpect(flash().attributeExists("assistantError"));

        verifyNoInteractions(assistantService);
    }

    @Test
    void assistantQuestionIsAnswered() throws Exception {
        mockMvc.perform(post("/meetups/7/assistant").param("question", "Какие решения?"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meetups/7"))
                .andExpect(flash().attributeExists("assistantSuccess"));

        verify(assistantService).ask(7L, "Какие решения?");
    }

    @Test
    void retryWithoutAudioDoesNotStartTranscription() throws Exception {
        Meetup meetup = meetup(8L, MeetupStatus.FAILED);
        when(meetupService.get(8L)).thenReturn(meetup);
        when(meetupService.isAudioAvailable(meetup)).thenReturn(false);

        mockMvc.perform(post("/meetups/8/retry"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("assistantError"));

        verify(transcriptionCoordinator, never()).processAsync(anyLong());
    }

    @Test
    void deleteRedirectsToList() throws Exception {
        mockMvc.perform(post("/meetups/9/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/meetups"))
                .andExpect(flash().attributeExists("success"));

        verify(meetupService).delete(9L);
    }

    private static Meetup meetup(Long id, MeetupStatus status) {
        Meetup meetup = new Meetup();
        meetup.setId(id);
        meetup.setTitle("Встреча " + id);
        meetup.setStatus(status);
        meetup.setCreatedAt(Instant.now());
        return meetup;
    }
}
