package github.reactivity512.meetup.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Speech-to-text settings (Vosk).
 */
@ConfigurationProperties(prefix = "app.speech")
public class SpeechProperties {

    /** Directory with an unpacked Vosk model, e.g. models/vosk-model-small-ru-0.22. */
    private Path modelPath = Path.of("./models/vosk-model-small-ru-0.22");

    /** Sample rate expected by the model. Vosk small models use 16000 Hz. */
    private float sampleRate = 16000f;

    /** ffmpeg executable (used to convert any audio to 16 kHz mono PCM WAV). */
    private String ffmpegCommand = "ffmpeg";

    /** Disable only if uploads are already 16 kHz mono PCM WAV files. */
    private boolean ffmpegEnabled = true;

    /** Hard limit for one transcription run. */
    private Duration timeout = Duration.ofMinutes(30);

    public Path getModelPath() {
        return modelPath;
    }

    public void setModelPath(Path modelPath) {
        this.modelPath = modelPath;
    }

    public float getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(float sampleRate) {
        this.sampleRate = sampleRate;
    }

    public String getFfmpegCommand() {
        return ffmpegCommand;
    }

    public void setFfmpegCommand(String ffmpegCommand) {
        this.ffmpegCommand = ffmpegCommand;
    }

    public boolean isFfmpegEnabled() {
        return ffmpegEnabled;
    }

    public void setFfmpegEnabled(boolean ffmpegEnabled) {
        this.ffmpegEnabled = ffmpegEnabled;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
