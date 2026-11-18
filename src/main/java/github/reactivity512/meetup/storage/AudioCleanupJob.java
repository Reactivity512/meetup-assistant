package github.reactivity512.meetup.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import github.reactivity512.meetup.config.StorageProperties;
import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.repository.MeetupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes old audio files. Transcripts and LLM answers stay in the database forever.
 */
@Component
public class AudioCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AudioCleanupJob.class);

    private final StorageProperties properties;
    private final MeetupRepository meetupRepository;
    private final StorageService storageService;

    public AudioCleanupJob(StorageProperties properties,
                           MeetupRepository meetupRepository,
                           StorageService storageService) {
        this.properties = properties;
        this.meetupRepository = meetupRepository;
        this.storageService = storageService;
    }

    @Scheduled(cron = "${app.storage.cleanup-cron:0 15 * * * *}")
    @Transactional
    public void deleteExpiredAudio() {
        if (!properties.isCleanupEnabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(properties.getRetention());
        List<Meetup> expired = meetupRepository.findByCreatedAtBeforeAndAudioPathIsNotNull(cutoff);
        for (Meetup meetup : expired) {
            storageService.delete(meetup.getAudioPath());
            meetup.setAudioPath(null);
            log.info("Removed expired audio for meetup {} (retention {})",
                    meetup.getId(), properties.getRetention());
        }
        storageService.cleanupTempFiles(Duration.ofHours(24));
    }
}
