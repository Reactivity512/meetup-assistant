package github.reactivity512.meetup.speech;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import github.reactivity512.meetup.config.SpeechProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

/**
 * In-process Vosk speech recognition. The native Vosk library is bundled in the
 * {@code com.alphacephei:vosk} jar; only the language model has to be downloaded separately.
 */
@Service
public class VoskTranscriptionService implements TranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(VoskTranscriptionService.class);
    private static final int BUFFER_SIZE = 8192;

    private final SpeechProperties properties;
    private final AudioNormalizer audioNormalizer;
    private final ObjectMapper objectMapper;

    private volatile Model model;

    public VoskTranscriptionService(SpeechProperties properties,
                                    AudioNormalizer audioNormalizer,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.audioNormalizer = audioNormalizer;
        this.objectMapper = objectMapper;
    }

    @Override
    public String transcribe(Path audioFile) {
        Path wav = null;
        try {
            wav = audioNormalizer.normalize(audioFile);
            WavDataExtractor.DataChunk data = WavDataExtractor.findDataChunk(wav);

            List<String> parts = new ArrayList<>();
            long deadline = System.nanoTime() + properties.getTimeout().toNanos();

            try (InputStream input = Files.newInputStream(wav);
                 Recognizer recognizer = new Recognizer(getModel(), properties.getSampleRate())) {

                input.skipNBytes(data.offset());
                byte[] buffer = new byte[BUFFER_SIZE];
                long remaining = data.size();
                while (remaining > 0) {
                    if (System.nanoTime() > deadline) {
                        throw new TranscriptionException("Превышено время распознавания ("
                                + properties.getTimeout().toMinutes() + " мин)");
                    }
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = input.read(buffer, 0, toRead);
                    if (read < 0) {
                        break;
                    }
                    remaining -= read;
                    if (recognizer.acceptWaveForm(buffer, read)) {
                        addText(parts, recognizer.getResult());
                    }
                }
                addText(parts, recognizer.getFinalResult());
            }

            String transcript = String.join(" ", parts).replaceAll("\\s+", " ").trim();
            if (transcript.isBlank()) {
                throw new TranscriptionException(
                        "Vosk не распознал речь. Проверьте, что язык модели совпадает с языком записи "
                                + "и что аудио не пустое.");
            }
            log.info("Transcribed {}: {} characters", audioFile.getFileName(), transcript.length());
            return transcript;
        } catch (TranscriptionException e) {
            throw e;
        } catch (IOException e) {
            throw new TranscriptionException("Ошибка чтения аудио: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new TranscriptionException("Ошибка распознавания речи: " + e.getMessage(), e);
        } finally {
            audioNormalizer.deleteTemporary(audioFile, wav);
        }
    }

    private void addText(List<String> parts, String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String text = node.path("text").asText("").trim();
            if (!text.isEmpty()) {
                parts.add(text);
            }
        } catch (IOException e) {
            log.warn("Could not parse Vosk JSON result: {}", e.getMessage());
        }
    }

    private Model getModel() {
        Model local = model;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (model == null) {
                model = loadModel();
            }
            return model;
        }
    }

    private Model loadModel() {
        Path path = properties.getModelPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new TranscriptionException(
                    "Модель Vosk не найдена: " + path + ". "
                            + "Скачайте модель (например, vosk-model-small-ru-0.22) и распакуйте в "
                            + properties.getModelPath() + ". Помощник: scripts/download-vosk-model.ps1");
        }
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS);
            log.info("Loading Vosk model from {}", path);
            Model loaded = new Model(path.toString());
            log.info("Vosk model loaded");
            return loaded;
        } catch (IOException | LinkageError e) {
            throw new TranscriptionException(
                    "Не удалось загрузить модель или нативную библиотеку Vosk. "
                            + "Проверьте целостность модели и права на временный каталог. Причина: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void close() {
        Model local = model;
        if (local != null) {
            local.close();
            log.info("Vosk model closed");
        }
    }
}
