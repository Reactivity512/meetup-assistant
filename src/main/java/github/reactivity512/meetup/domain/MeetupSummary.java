package github.reactivity512.meetup.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight row for the meetups table: does not load the (potentially huge) transcript.
 */
public record MeetupSummary(
        Long id,
        String title,
        MeetupStatus status,
        Instant createdAt,
        Integer transcriptChars,
        String processingError) {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    public String getCreatedAtFormatted() {
        return createdAt == null ? "" : DATE_FORMAT.format(createdAt);
    }

    public long getTranscriptCharsSafe() {
        return transcriptChars == null ? 0 : transcriptChars;
    }
}
