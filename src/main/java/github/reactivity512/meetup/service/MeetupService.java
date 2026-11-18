package github.reactivity512.meetup.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import github.reactivity512.meetup.domain.LlmAnswer;
import github.reactivity512.meetup.domain.Meetup;
import github.reactivity512.meetup.domain.MeetupStatus;
import github.reactivity512.meetup.domain.MeetupSummary;
import github.reactivity512.meetup.repository.LlmAnswerRepository;
import github.reactivity512.meetup.repository.MeetupRepository;
import github.reactivity512.meetup.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MeetupService {

    private static final Logger log = LoggerFactory.getLogger(MeetupService.class);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final MeetupRepository meetupRepository;
    private final LlmAnswerRepository answerRepository;
    private final StorageService storageService;

    public MeetupService(MeetupRepository meetupRepository,
                         LlmAnswerRepository answerRepository,
                         StorageService storageService) {
        this.meetupRepository = meetupRepository;
        this.answerRepository = answerRepository;
        this.storageService = storageService;
    }

    @Transactional
    public Meetup create(String title, MultipartFile file) {
        String path = storageService.store(file);
        try {
            Meetup meetup = new Meetup();
            meetup.setTitle(title.trim());
            meetup.setStatus(MeetupStatus.QUEUED);
            meetup.setAudioPath(path);
            meetup.setAudioOriginalName(file.getOriginalFilename());
            meetup.setAudioContentType(file.getContentType());
            meetup.setAudioSizeBytes(file.getSize());
            Meetup saved = meetupRepository.save(meetup);
            log.info("Created meetup {} ('{}')", saved.getId(), saved.getTitle());
            return saved;
        } catch (RuntimeException e) {
            // Do not leave an orphan file if the DB insert failed.
            storageService.delete(path);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Meetup get(Long id) {
        return meetupRepository.findById(id).orElseThrow(() -> new MeetupNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<MeetupSummary> listSummaries() {
        return meetupRepository.findAllSummaries();
    }

    @Transactional(readOnly = true)
    public List<LlmAnswer> answers(Long meetupId, int limit) {
        return answerRepository.findByMeetupIdOrderByCreatedAtDesc(meetupId, PageRequest.of(0, limit));
    }

    @Transactional
    public Meetup markProcessing(Long id) {
        Meetup meetup = get(id);
        meetup.setStatus(MeetupStatus.PROCESSING);
        meetup.setProcessingError(null);
        return meetup;
    }

    @Transactional
    public void complete(Long id, String transcript) {
        Meetup meetup = get(id);
        meetup.setTranscript(transcript);
        meetup.setTranscribedAt(Instant.now());
        meetup.setProcessingError(null);
        meetup.setStatus(MeetupStatus.READY);
        log.info("Meetup {} transcribed ({} chars)", id, transcript.length());
    }

    @Transactional
    public void fail(Long id, String error) {
        Meetup meetup = get(id);
        meetup.setStatus(MeetupStatus.FAILED);
        meetup.setProcessingError(truncate(error));
        log.warn("Meetup {} transcription failed: {}", id, error);
    }

    @Transactional
    public void queueForRetry(Long id) {
        Meetup meetup = get(id);
        meetup.setStatus(MeetupStatus.QUEUED);
        meetup.setTranscript(null);
        meetup.setTranscribedAt(null);
        meetup.setProcessingError(null);
    }

    @Transactional
    public void delete(Long id) {
        Meetup meetup = get(id);
        answerRepository.deleteByMeetupId(id);
        meetupRepository.delete(meetup);
        storageService.delete(meetup.getAudioPath());
        log.info("Deleted meetup {}", id);
    }

    @Transactional(readOnly = true)
    public boolean isAudioAvailable(Meetup meetup) {
        return storageService.exists(meetup.getAudioPath());
    }

    @Transactional(readOnly = true)
    public Path audioPath(Meetup meetup) {
        return storageService.resolveAudio(meetup.getAudioPath());
    }

    @Transactional(readOnly = true)
    public List<Meetup> findUnfinished() {
        return meetupRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(MeetupStatus.QUEUED, MeetupStatus.PROCESSING));
    }

    private static String truncate(String error) {
        if (error == null) {
            return "Неизвестная ошибка";
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
