package github.reactivity512.meetup.speech;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Minimal WAV parser used to skip service chunks and feed only raw PCM data to Vosk.
 */
public final class WavDataExtractor {

    private WavDataExtractor() {
    }

    public record DataChunk(long offset, long size) {
    }

    public static DataChunk findDataChunk(Path wavFile) {
        try (FileChannel channel = FileChannel.open(wavFile, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize < 44) {
                throw new TranscriptionException("WAV-файл повреждён или слишком короткий");
            }

            ByteBuffer header = read(channel, 0, 12);
            String riff = fourCc(header, 0);
            String wave = fourCc(header, 8);
            if (!"RIFF".equals(riff) || !"WAVE".equals(wave)) {
                throw new TranscriptionException("Ожидался WAV-файл в формате RIFF/WAVE");
            }

            long position = 12;
            while (position + 8 <= fileSize) {
                ByteBuffer chunkHeader = read(channel, position, 8);
                String chunkId = fourCc(chunkHeader, 0);
                long chunkSize = Integer.toUnsignedLong(chunkHeader.getInt(4));
                long dataStart = position + 8;

                if ("data".equals(chunkId)) {
                    long available = Math.min(chunkSize, fileSize - dataStart);
                    return new DataChunk(dataStart, available);
                }

                // Chunks are padded to an even number of bytes.
                position = dataStart + chunkSize + (chunkSize % 2);
            }
            throw new TranscriptionException("В WAV-файле не найден блок data");
        } catch (IOException e) {
            throw new TranscriptionException("Не удалось прочитать WAV-файл: " + e.getMessage(), e);
        }
    }

    public static boolean looksLikeWav(Path file) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            if (channel.size() < 12) {
                return false;
            }
            ByteBuffer header = read(channel, 0, 12);
            return "RIFF".equals(fourCc(header, 0)) && "WAVE".equals(fourCc(header, 8));
        } catch (IOException e) {
            return false;
        }
    }

    private static ByteBuffer read(FileChannel channel, long position, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
        int read = 0;
        while (read < length) {
            int count = channel.read(buffer, position + read);
            if (count < 0) {
                break;
            }
            read += count;
        }
        buffer.flip();
        return buffer;
    }

    private static String fourCc(ByteBuffer buffer, int offset) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = buffer.get(offset + i);
        }
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    public static void requireReadable(Path path) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new TranscriptionException("Аудиофайл не найден: " + path.getFileName());
        }
    }
}
