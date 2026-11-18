package github.reactivity512.meetup.web;

import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.service.MeetupService;
import github.reactivity512.meetup.service.TranscriptionCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * If the application was restarted during transcription, the DB may contain
 * meetups stuck in QUEUED/PROCESSING. Re-enqueue them if the audio still exists.
 */
@Component
public class StartupRecovery implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRecovery.class);

    private final MeetupService meetupService;
    private final TranscriptionCoordinator transcriptionCoordinator;

    public StartupRecovery(MeetupService meetupService,
                           TranscriptionCoordinator transcriptionCoordinator) {
        this.meetupService = meetupService;
        this.transcriptionCoordinator = transcriptionCoordinator;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Meetup meetup : meetupService.findUnfinished()) {
            if (meetupService.isAudioAvailable(meetup)) {
                log.info("Re-enqueueing unfinished meetup {}", meetup.getId());
                transcriptionCoordinator.processAsync(meetup.getId());
            } else {
                meetupService.fail(meetup.getId(),
                        "Обработка была прервана перезапуском приложения, а аудиофайл уже удалён.");
            }
        }
    }
}
