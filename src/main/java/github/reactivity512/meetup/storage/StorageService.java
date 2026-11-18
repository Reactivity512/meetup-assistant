package github.reactivity512.meetup.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import github.reactivity512.meetup.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded audio on the local disk.
 *
 * <p>Only a generated UUID-based file name is used on disk, so the original
 * file name can never escape the storage directory (path traversal).</p>
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "wav", "mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "webm", "mp4", "wma");

    private final StorageProperties properties;
    private final Path audioDir;

    public StorageService(StorageProperties properties) {
        this.properties = properties;
        this.audioDir = properties.getDir().toAbsolutePath().normalize().resolve("audio");
        createDirectories(audioDir);
    }

    public Path getAudioDir() {
        return audioDir;
    }

    public Path getRootDir() {
        return properties.getDir().toAbsolutePath().normalize();
    }

    /**
     * Saves the uploaded file and returns its path relative to the audio directory.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не выбран или пуст");
        }
        String extension = extensionOf(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + "." + extension;
        Path target = audioDir.resolve(storedName).normalize();
        if (!target.startsWith(audioDir)) {
            throw new IllegalArgumentException("Некорректное имя файла");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Не удалось сохранить файл: " + e.getMessage(), e);
        }
        log.info("Stored audio {} ({} bytes) as {}", file.getOriginalFilename(), file.getSize(), storedName);
        return storedName;
    }

    public Path resolveAudio(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new StorageException("Аудиофайл для этого митинга уже удалён");
        }
        Path target = audioDir.resolve(relativePath).normalize();
        if (!target.startsWith(audioDir)) {
            throw new StorageException("Некорректный путь к аудиофайлу");
        }
        return target;
    }

    public boolean exists(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        try {
            return Files.isRegularFile(resolveAudio(relativePath));
        } catch (StorageException e) {
            return false;
        }
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Path file = resolveAudio(relativePath);
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("Deleted audio file {}", file.getFileName());
            }
        } catch (IOException | StorageException e) {
            log.warn("Could not delete audio file '{}': {}", relativePath, e.getMessage());
        }
    }

    /**
     * Returns a temporary file inside the application data directory.
     * The caller is responsible for deleting it.
     */
    public Path createTempFile(String suffix) {
        Path tempDir = getRootDir().resolve("tmp");
        createDirectories(tempDir);
        try {
            return Files.createTempFile(tempDir, "transcription-", suffix);
        } catch (IOException e) {
            throw new StorageException("Не удалось создать временный файл: " + e.getMessage(), e);
        }
    }

    public void cleanupTempFiles(Duration olderThan) {
        Path tempDir = getRootDir().resolve("tmp");
        if (!Files.isDirectory(tempDir)) {
            return;
        }
        Instant cutoff = Instant.now().minus(olderThan);
        try (var files = Files.list(tempDir)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                try {
                    if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException e) {
                    log.warn("Could not clean temporary file {}: {}", file, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not list temporary directory {}: {}", tempDir, e.getMessage());
        }
    }

    private static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new StorageException("Не удалось создать каталог " + path + ": " + e.getMessage(), e);
        }
    }

    private static String extensionOf(String originalName) {
        if (originalName == null) {
            return "wav";
        }
        String name = Path.of(originalName).getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "wav";
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Неподдерживаемый формат файла: ." + extension);
        }
        return extension;
    }
}
