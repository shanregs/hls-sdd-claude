package com.hls.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.files.api.FileLimits;
import com.hls.files.api.FileStore;
import com.hls.files.api.FileStore.FileRef;
import com.hls.files.api.FileStore.FileStream;
import com.hls.school.api.InvalidInputException;
import com.hls.support.IntegrationTestBase;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import com.hls.identity.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 029 T014: streamed storage, the new media types, limits per type, and the 10 MB default for older callers. */
class FileStoreStreamingTest extends IntegrationTestBase {

    private static final long MB = 1024L * 1024;
    private static final FileLimits CONTENT = new FileLimits(Map.of(
            "mp4", 500 * MB, "mp3", 100 * MB, "m4a", 100 * MB, "wav", 100 * MB, "pdf", 25 * MB, "docx", 25 * MB, "xlsx", 25 * MB));

    @Autowired
    FileStore store;

    private final UUID actor = UUID.randomUUID();

    private static byte[] mp4() {
        return new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
    }

    private static byte[] m4a() {
        return new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'M', '4', 'A', ' ', 0, 0, 0, 0};
    }

    private static byte[] mp3Id3() {
        return new byte[] {'I', 'D', '3', 3, 0, 0, 0, 0, 0, 0x21, 1, 2, 3, 4, 5, 6};
    }

    private static byte[] mp3Frame() {
        return new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
    }

    private static byte[] wav() {
        return new byte[] {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't', ' '};
    }

    private static byte[] pdf() {
        return "%PDF-1.4 test content".getBytes();
    }

    private static byte[] zip() {
        return new byte[] {'P', 'K', 3, 4, 20, 0, 0, 0, 8, 0, 1, 2, 3, 4, 5, 6};
    }

    private FileRef put(String name, byte[] bytes, FileLimits limits) {
        return store.store(actor, "TEST", UUID.randomUUID(), name, new ByteArrayInputStream(bytes), bytes.length, limits);
    }

    @Test
    void eachContentTypeIsAcceptedWithMatchingBytesAndGetsItsContentType() throws Exception {
        assertThat(put("a.mp4", mp4(), CONTENT).contentType()).isEqualTo("video/mp4");
        assertThat(put("a.m4a", m4a(), CONTENT).contentType()).isEqualTo("audio/mp4");
        assertThat(put("a.mp3", mp3Id3(), CONTENT).contentType()).isEqualTo("audio/mpeg");
        assertThat(put("b.mp3", mp3Frame(), CONTENT).contentType()).isEqualTo("audio/mpeg");
        assertThat(put("a.wav", wav(), CONTENT).contentType()).isEqualTo("audio/wav");
        assertThat(put("a.pdf", pdf(), CONTENT).contentType()).isEqualTo("application/pdf");
        assertThat(put("a.docx", zip(), CONTENT).contentType()).contains("wordprocessingml");
        assertThat(put("a.xlsx", zip(), CONTENT).contentType()).contains("spreadsheetml");
    }

    @Test
    void aStreamedFileRoundTripsThroughOpenStream() throws Exception {
        byte[] bytes = new byte[300_000];
        System.arraycopy(mp4(), 0, bytes, 0, 16);
        FileRef ref = put("big.mp4", bytes, CONTENT);
        assertThat(ref.sizeBytes()).isEqualTo(bytes.length);
        FileStream opened = store.openStream(ref.id()).orElseThrow();
        try (InputStream in = opened.bytes()) {
            assertThat(in.readAllBytes()).isEqualTo(bytes);
        }
    }

    @Test
    void wrongBytesForTheTypeAreRefused() {
        assertThatThrownBy(() -> put("fake.mp4", pdf(), CONTENT)).isInstanceOf(InvalidInputException.class).hasMessageContaining("does not match");
        assertThatThrownBy(() -> put("fake.wav", mp4(), CONTENT)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> put("fake.mp3", pdf(), CONTENT)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> put("fake.pdf", mp4(), CONTENT)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void typesOutsideTheTableAreRefused() {
        assertThatThrownBy(() -> put("run.exe", pdf(), CONTENT)).isInstanceOf(InvalidInputException.class).hasMessageContaining("not accepted");
        assertThatThrownBy(() -> put("old.doc", zip(), CONTENT)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> put("movie.mov", mp4(), CONTENT)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> put("note.txt", "hello".getBytes(), CONTENT)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void theLimitIsPerTypeJustUnderPassesJustOverIsRefused() {
        FileLimits small = new FileLimits(Map.of("pdf", 1024L, "mp4", 4096L));
        byte[] justUnder = Arrays.copyOf(pdf(), 1024);
        assertThat(put("ok.pdf", justUnder, small).sizeBytes()).isEqualTo(1024);
        byte[] over = Arrays.copyOf(pdf(), 1025);
        assertThatThrownBy(() -> put("big.pdf", over, small))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining(".pdf");
        byte[] mp4Ok = Arrays.copyOf(mp4(), 4096);
        assertThat(put("ok.mp4", mp4Ok, small).sizeBytes()).isEqualTo(4096);
    }

    @Test
    void anUnknownLengthStreamIsStillStoppedAtTheLimit() {
        FileLimits small = new FileLimits(Map.of("pdf", 2048L));
        byte[] over = Arrays.copyOf(pdf(), 5000);
        assertThatThrownBy(() -> store.store(actor, "TEST", UUID.randomUUID(), "x.pdf", new ByteArrayInputStream(over), -1, small))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void aDeclaredOversizeIsRefusedBeforeReading() {
        FileLimits small = new FileLimits(Map.of("pdf", 2048L));
        InputStream mustNotBeRead = new InputStream() {
            @Override
            public int read() {
                throw new AssertionError("the stream should not be read");
            }
        };
        assertThatThrownBy(() -> store.store(actor, "TEST", UUID.randomUUID(), "x.pdf", mustNotBeRead, 10_000, small))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void anEmptyStreamIsRefused() {
        assertThatThrownBy(() -> put("x.pdf", new byte[0], CONTENT)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void olderCallersKeepTheTenMegabyteDefaultAndTheOldTypes() {
        assertThat(FileLimits.defaults().limitFor("pdf")).isEqualTo(10 * MB);
        assertThat(FileLimits.defaults().limitFor("mp4")).isEqualTo(-1);
        assertThatThrownBy(() -> store.store(actor, "TEST", UUID.randomUUID(), "a.mp4", mp4()))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> store.store(actor, "TEST", UUID.randomUUID(), "a.pdf", new byte[(int) (10 * MB) + 1]))
                .isInstanceOf(InvalidInputException.class);
        assertThat(store.store(actor, "TEST", UUID.randomUUID(), "a.pdf", pdf()).contentType()).isEqualTo("application/pdf");
    }

    private int postMultipart(String uri, String token, int size) throws Exception {
        String boundary = "----hls" + UUID.randomUUID();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"x.pdf\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes();
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes();
        byte[] body = new byte[head.length + size + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(tail, 0, body, head.length + size, tail.length);
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + uri))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            return java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode();
        } catch (java.io.IOException e) {
            // the server may close the connection after refusing a large body
            return 413;
        }
    }

    @Test
    void aLargeMultipartBodyOutsideContentIsRefusedEarlyAndASmallOneIsNot() throws Exception {
        String token = signInAs(Role.MANAGER).token();
        String uri = "/api/v1/tasks/" + UUID.randomUUID() + "/evidence";
        assertThat(postMultipart(uri, token, 13 * 1024 * 1024)).isEqualTo(413);
        assertThat(postMultipart(uri, token, 1024)).isNotEqualTo(413);
    }
}
