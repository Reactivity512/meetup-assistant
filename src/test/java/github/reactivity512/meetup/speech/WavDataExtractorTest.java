package github.reactivity512.meetup.speech;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class WavDataExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void findsDataChunkAfterServiceChunks() throws Exception {
        byte[] pcm = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        byte[] wav = wavWithExtraChunk(pcm);
        Path file = tempDir.resolve("test.wav");
        Files.write(file, wav);

        WavDataExtractor.DataChunk chunk = WavDataExtractor.findDataChunk(file);

        assertThat(chunk.offset()).isEqualTo(wav.length - pcm.length);
        assertThat(chunk.size()).isEqualTo(pcm.length);
        assertThat(WavDataExtractor.looksLikeWav(file)).isTrue();
    }

    @Test
    void rejectsNonWavFile() throws Exception {
        Path file = tempDir.resolve("broken.wav");
        Files.writeString(file, "not a wav file at all");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> WavDataExtractor.findDataChunk(file))
                .isInstanceOf(TranscriptionException.class);
    }

    private static byte[] wavWithExtraChunk(byte[] pcm) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] fmt = new byte[16];
        ByteBuffer fmtBuffer = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN);
        fmtBuffer.putShort((short) 1);       // PCM
        fmtBuffer.putShort((short) 1);       // mono
        fmtBuffer.putInt(16000);             // sample rate
        fmtBuffer.putInt(32000);             // byte rate
        fmtBuffer.putShort((short) 2);       // block align
        fmtBuffer.putShort((short) 16);      // bits per sample

        byte[] listPayload = "INFOtest".getBytes(StandardCharsets.US_ASCII);
        int riffSize = 4 + (8 + fmt.length) + (8 + listPayload.length) + (8 + pcm.length);

        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(leInt(riffSize));
        out.writeBytes("WAVE".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes("fmt ".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(leInt(fmt.length));
        out.writeBytes(fmt);
        out.writeBytes("LIST".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(leInt(listPayload.length));
        out.writeBytes(listPayload);
        out.writeBytes("data".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(leInt(pcm.length));
        out.writeBytes(pcm);
        return out.toByteArray();
    }

    private static byte[] leInt(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }
}
