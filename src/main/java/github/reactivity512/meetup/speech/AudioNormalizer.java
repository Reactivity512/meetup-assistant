package github.reactivity512.meetup.speech;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import github.reactivity512.meetup.config.SpeechProperties;
import github.reactivity512.meetup.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Converts arbitrary audio (mp3, m4a, ogg, video, ...) to the format Vosk expects:
 * 16 kHz, mono, 16-bit PCM WAV. Requires ffmpeg on the PATH.
 */
@Component
public class AudioNormalizer {

    private static final Logger log = LoggerFactory.getLogger(AudioNormalizer.class);

    private final SpeechProperties properties;
    private final StorageService storageService;

    public AudioNormalizer(SpeechProperties properties, StorageService storageService) {
        this.properties = properties;
        this.storageService = storageService;
    }

    /**
     * @return path to a WAV file. May be the source file itself when conversion is disabled.
     */
    public Path normalize(Path source) {
        WavDataExtractor.requireReadable(source);
        if (!properties.isFfmpegEnabled()) {
            if (!WavDataExtractor.looksLikeWav(source)) {
                throw new TranscriptionException(
                        "Конвертация ffmpeg отключена, а загруженный файл не является WAV. "
                                + "Включите app.speech.ffmpeg-enabled или загрузите WAV 16 kHz mono.");
            }
            return source;
        }

        Path target = storageService.createTempFile(".wav");
        Path logFile = storageService.createTempFile(".ffmpeg.log");
        boolean succeeded = false;
        List<String> command = List.of(
                properties.getFfmpegCommand(),
                "-hide_banner",
                "-loglevel", "error",
                "-y",
                "-i", source.toAbsolutePath().toString(),
                "-vn",
                "-ac", "1",
                "-ar", "16000",
                "-c:a", "pcm_s16le",
                target.toAbsolutePath().toString());

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile());
            log.debug("Running ffmpeg: {}", String.join(" ", command));
            Process process = processBuilder.start();
            Duration timeout = properties.getTimeout();
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TranscriptionException("Превышено время конвертации аудио (" + timeout.toMinutes() + " мин)");
            }
            if (process.exitValue() != 0) {
                String log = readLog(logFile);
                throw new TranscriptionException("ffmpeg не смог обработать аудио: " + log);
            }
            if (!Files.isRegularFile(target) || Files.size(target) <= 44) {
                throw new TranscriptionException("ffmpeg вернул пустой WAV-файл");
            }
            log.info("Converted {} to 16 kHz mono WAV ({} bytes)", source.getFileName(), Files.size(target));
            succeeded = true;
            return target;
        } catch (IOException e) {
            throw new TranscriptionException(
                    "Не удалось запустить ffmpeg ('" + properties.getFfmpegCommand() + "'). "
                            + "Установите ffmpeg или укажите app.speech.ffmpeg-command. Причина: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Конвертация аудио прервана", e);
        } finally {
            try {
                Files.deleteIfExists(logFile);
                if (!succeeded) {
                    Files.deleteIfExists(target);
                }
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    public void deleteTemporary(Path original, Path converted) {
        if (converted != null && !converted.equals(original)) {
            try {
                Files.deleteIfExists(converted);
            } catch (IOException e) {
                log.warn("Could not delete temporary WAV {}: {}", converted, e.getMessage());
            }
        }
    }

    private static String readLog(Path logFile) {
        try {
            String log = Files.readString(logFile, StandardCharsets.UTF_8).trim();
            if (log.length() > 1000) {
                log = log.substring(log.length() - 1000);
            }
            return log.isBlank() ? "неизвестная ошибка" : log;
        } catch (IOException e) {
            return "не удалось прочитать лог ffmpeg";
        }
    }
}
