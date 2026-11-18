package github.reactivity512.meetup.web;

import java.time.Instant;

import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.domain.MeetupStatus;
import github.reactivity512.meetup.service.MeetupNotFoundException;
import github.reactivity512.meetup.service.MeetupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeetupApiController.class)
class MeetupApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MeetupService meetupService;

    @Test
    void returnsProcessingStatus() throws Exception {
        Meetup meetup = new Meetup();
        meetup.setId(1L);
        meetup.setTitle("Встреча");
        meetup.setStatus(MeetupStatus.PROCESSING);
        meetup.setCreatedAt(Instant.now());
        when(meetupService.get(1L)).thenReturn(meetup);

        mockMvc.perform(get("/api/meetups/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.busy").value(true))
                .andExpect(jsonPath("$.transcriptAvailable").value(false));
    }

    @Test
    void returns404ForMissingMeetup() throws Exception {
        when(meetupService.get(42L)).thenThrow(new MeetupNotFoundException(42L));

        mockMvc.perform(get("/api/meetups/42/status"))
                .andExpect(status().isNotFound());
    }
}
