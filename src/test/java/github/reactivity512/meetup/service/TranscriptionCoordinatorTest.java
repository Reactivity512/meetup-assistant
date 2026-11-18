package github.reactivity512.meetup.service;

import java.nio.file.Path;

import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.speech.TranscriptionException;
import github.reactivity512.meetup.speech.TranscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptionCoordinatorTest {

    @Mock
    private MeetupService meetupService;

    @Mock
    private TranscriptionService transcriptionService;

    @InjectMocks
    private TranscriptionCoordinator coordinator;

    @Test
    void successfulRunStoresTranscript() {
        Meetup meetup = new Meetup();
        meetup.setId(1L);
        meetup.setAudioPath("audio.wav");
        when(meetupService.markProcessing(1L)).thenReturn(meetup);
        when(meetupService.audioPath(meetup)).thenReturn(Path.of("audio.wav"));
        when(transcriptionService.transcribe(Path.of("audio.wav"))).thenReturn("Готовый текст");

        coordinator.process(1L);

        verify(meetupService).complete(1L, "Готовый текст");
    }

    @Test
    void failedRunStoresReadableError() {
        Meetup meetup = new Meetup();
        meetup.setId(2L);
        when(meetupService.markProcessing(2L)).thenReturn(meetup);
        when(meetupService.audioPath(meetup)).thenReturn(Path.of("audio.wav"));
        when(transcriptionService.transcribe(Path.of("audio.wav")))
                .thenThrow(new TranscriptionException("ffmpeg не найден"));

        coordinator.process(2L);

        verify(meetupService).fail(2L, "ffmpeg не найден");
    }

    @Test
    void rootMessageFallsBackToCauseWhenTopMessageIsEmpty() {
        RuntimeException error = new RuntimeException("", new IllegalStateException("disk full"));

        assertThat(TranscriptionCoordinator.rootMessage(error)).isEqualTo("disk full");
    }
}
