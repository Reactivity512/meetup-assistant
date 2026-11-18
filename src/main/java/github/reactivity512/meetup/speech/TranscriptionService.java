package github.reactivity512.meetup.speech;

import java.nio.file.Path;

/**
 * Converts a speech recording to text.
 */
public interface TranscriptionService {

    String transcribe(Path audioFile);
}
