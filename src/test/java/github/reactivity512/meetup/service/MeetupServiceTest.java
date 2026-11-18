package github.reactivity512.meetup.service;

import java.util.List;
import java.util.Optional;

import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.domain.MeetupStatus;
import github.reactivity512.meetup.repository.LlmAnswerRepository;
import github.reactivity512.meetup.repository.MeetupRepository;
import github.reactivity512.meetup.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetupServiceTest {

    @Mock
    private MeetupRepository meetupRepository;

    @Mock
    private LlmAnswerRepository answerRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private MeetupService meetupService;

    @Test
    void createStoresAudioAndSavesQueuedMeetup() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "meeting.mp3", "audio/mpeg", new byte[]{1, 2, 3});
        when(storageService.store(file)).thenReturn("stored.mp3");
        when(meetupRepository.save(any(Meetup.class))).thenAnswer(invocation -> {
            Meetup meetup = invocation.getArgument(0);
            meetup.setId(42L);
            return meetup;
        });

        Meetup created = meetupService.create("  Планирование  ", file);

        assertThat(created.getId()).isEqualTo(42L);
        assertThat(created.getTitle()).isEqualTo("Планирование");
        assertThat(created.getStatus()).isEqualTo(MeetupStatus.QUEUED);
        assertThat(created.getAudioPath()).isEqualTo("stored.mp3");
        assertThat(created.getAudioOriginalName()).isEqualTo("meeting.mp3");
    }

    @Test
    void createDeletesOrphanFileWhenDatabaseFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "meeting.wav", "audio/wav", new byte[]{1});
        when(storageService.store(file)).thenReturn("stored.wav");
        when(meetupRepository.save(any(Meetup.class))).thenThrow(new RuntimeException("db down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> meetupService.create("Встреча", file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db down");

        verify(storageService).delete("stored.wav");
    }

    @Test
    void completeStoresTranscriptAndMarksReady() {
        Meetup meetup = meetup(7L, MeetupStatus.PROCESSING);
        when(meetupRepository.findById(7L)).thenReturn(Optional.of(meetup));

        meetupService.complete(7L, "Текст встречи");

        assertThat(meetup.getStatus()).isEqualTo(MeetupStatus.READY);
        assertThat(meetup.getTranscript()).isEqualTo("Текст встречи");
        assertThat(meetup.getTranscribedAt()).isNotNull();
    }

    @Test
    void failStoresErrorMessage() {
        Meetup meetup = meetup(8L, MeetupStatus.PROCESSING);
        when(meetupRepository.findById(8L)).thenReturn(Optional.of(meetup));

        meetupService.fail(8L, "ffmpeg недоступен");

        assertThat(meetup.getStatus()).isEqualTo(MeetupStatus.FAILED);
        assertThat(meetup.getProcessingError()).isEqualTo("ffmpeg недоступен");
    }

    @Test
    void deleteRemovesAnswersDatabaseRowAndAudio() {
        Meetup meetup = meetup(9L, MeetupStatus.READY);
        meetup.setAudioPath("stored.wav");
        when(meetupRepository.findById(9L)).thenReturn(Optional.of(meetup));

        meetupService.delete(9L);

        verify(answerRepository).deleteByMeetupId(9L);
        verify(meetupRepository).delete(meetup);
        verify(storageService).delete("stored.wav");
    }

    @Test
    void answersAreLimitedByHistorySize() {
        when(answerRepository.findByMeetupIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());

        assertThat(meetupService.answers(1L, 10)).isEmpty();

        verify(answerRepository).findByMeetupIdOrderByCreatedAtDesc(any(), any());
        verify(meetupRepository, never()).findById(any());
    }

    private static Meetup meetup(Long id, MeetupStatus status) {
        Meetup meetup = new Meetup();
        meetup.setId(id);
        meetup.setTitle("Встреча");
        meetup.setStatus(status);
        return meetup;
    }
}
