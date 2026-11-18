package github.reactivity512.meetup.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "meetups")
public class Meetup {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MeetupStatus status = MeetupStatus.QUEUED;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 500)
    private String audioPath;

    @Column(length = 255)
    private String audioOriginalName;

    @Column(length = 100)
    private String audioContentType;

    private Long audioSizeBytes;

    /** Large enough for many hours of speech; stored as VARCHAR so length() works in queries. */
    @Column(name = "transcript", length = 10_000_000)
    private String transcript;

    private Instant transcribedAt;

    @Column(length = 2000)
    private String processingError;

    @OneToMany(mappedBy = "meetup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LlmAnswer> answers = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = MeetupStatus.QUEUED;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public MeetupStatus getStatus() {
        return status;
    }

    public void setStatus(MeetupStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedAtFormatted() {
        return createdAt == null ? "" : DATE_FORMAT.format(createdAt);
    }

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }

    public String getAudioOriginalName() {
        return audioOriginalName;
    }

    public void setAudioOriginalName(String audioOriginalName) {
        this.audioOriginalName = audioOriginalName;
    }

    public String getAudioContentType() {
        return audioContentType;
    }

    public void setAudioContentType(String audioContentType) {
        this.audioContentType = audioContentType;
    }

    public Long getAudioSizeBytes() {
        return audioSizeBytes;
    }

    public String getAudioSizeFormatted() {
        if (audioSizeBytes == null) {
            return "—";
        }
        double megabytes = audioSizeBytes / (1024.0 * 1024.0);
        if (megabytes < 0.1) {
            return String.format(java.util.Locale.ROOT, "%.0f КБ", audioSizeBytes / 1024.0);
        }
        return String.format(java.util.Locale.ROOT, "%.1f МБ", megabytes);
    }

    public void setAudioSizeBytes(Long audioSizeBytes) {
        this.audioSizeBytes = audioSizeBytes;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public Instant getTranscribedAt() {
        return transcribedAt;
    }

    public void setTranscribedAt(Instant transcribedAt) {
        this.transcribedAt = transcribedAt;
    }

    public String getProcessingError() {
        return processingError;
    }

    public void setProcessingError(String processingError) {
        this.processingError = processingError;
    }

    public List<LlmAnswer> getAnswers() {
        return answers;
    }

    public int getTranscriptChars() {
        return transcript == null ? 0 : transcript.length();
    }

    public String getTranscribedAtFormatted() {
        return transcribedAt == null ? "" : DATE_FORMAT.format(transcribedAt);
    }
}
