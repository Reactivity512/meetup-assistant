package github.reactivity512.meetup.config;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local file storage settings.
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** Root directory for uploaded audio (and other runtime data). */
    private Path dir = Path.of("./data");

    /** Audio files older than this are removed by the cleanup job. Transcripts stay in the DB. */
    private Duration retention = Duration.ofDays(30);

    /** Set to false to keep audio forever. */
    private boolean cleanupEnabled = true;

    public Path getDir() {
        return dir;
    }

    public void setDir(Path dir) {
        this.dir = dir;
    }

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }
}
