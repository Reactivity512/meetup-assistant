package github.reactivity512.meetup.service;

import java.nio.file.Path;

import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.speech.TranscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs transcription outside the request thread. The UI polls the status endpoint
 * and updates the table without a full page reload.
 */
@Service
public class TranscriptionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionCoordinator.class);

    private final MeetupService meetupService;
    private final TranscriptionService transcriptionService;

    public TranscriptionCoordinator(MeetupService meetupService,
                                    TranscriptionService transcriptionService) {
        this.meetupService = meetupService;
        this.transcriptionService = transcriptionService;
    }

    @Async("transcriptionExecutor")
    public void processAsync(Long meetupId) {
        process(meetupId);
    }

    /**
     * Synchronous implementation, also used by tests and by startup recovery.
     */
    public void process(Long meetupId) {
        log.info("Starting transcription for meetup {}", meetupId);
        try {
            Meetup meetup = meetupService.markProcessing(meetupId);
            Path audio = meetupService.audioPath(meetup);
            String transcript = transcriptionService.transcribe(audio);
            meetupService.complete(meetupId, transcript);
        } catch (Exception e) {
            log.error("Transcription of meetup {} failed", meetupId, e);
            try {
                meetupService.fail(meetupId, rootMessage(e));
            } catch (Exception persistenceError) {
                log.error("Could not save failure status for meetup {}", meetupId, persistenceError);
            }
        }
    }

    static String rootMessage(Throwable throwable) {
        // Top-level exceptions in this app carry user-facing explanations
        // (e.g. ffmpeg output); use them and only fall back to the root cause.
        if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
            return throwable.getMessage();
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
        }
        return current.getClass().getSimpleName();
    }
}
