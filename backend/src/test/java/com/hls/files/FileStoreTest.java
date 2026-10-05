package com.hls.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.files.api.FileStore;
import com.hls.files.api.FileStore.FileRef;
import com.hls.school.api.InvalidInputException;
import com.hls.support.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Spec 023 contract C7: the shared file store keeps files once, checks them, and only ever removes them. */
class FileStoreTest extends IntegrationTestBase {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final byte[] PDF = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    FileStore store;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID actor = UUID.randomUUID();

    @Test
    void aStoredFileRoundTripsWithItsBytesAndChecksum() throws Exception {
        UUID owner = UUID.randomUUID();

        FileRef ref = store.store(actor, "TEST", owner, "visit photo.png", PNG);

        assertThat(ref.contentType()).isEqualTo("image/png");
        assertThat(ref.sizeBytes()).isEqualTo(PNG.length);
        assertThat(store.open(ref.id()).orElseThrow().bytes()).isEqualTo(PNG);
        String sha = jdbc.queryForObject("select sha256 from stored_file where id = ?", String.class, ref.id());
        assertThat(sha).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PNG)));
        assertThat(store.list("TEST", owner)).extracting(FileRef::id).containsExactly(ref.id());
    }

    @Test
    void theStoredNameIsGeneratedNeverTheClientsPath() {
        FileRef ref = store.store(actor, "TEST", UUID.randomUUID(), "..\\..\\evil/../report.pdf", PDF);

        String path = jdbc.queryForObject("select storage_path from stored_file where id = ?", String.class, ref.id());
        assertThat(ref.name()).isEqualTo("report.pdf");
        assertThat(path).contains(ref.id().toString()).doesNotContain("evil").doesNotContain("..");
    }

    @Test
    void aTypeOutsideTheListAMismatchedContentAndAnOversizeFileAreRefused() {
        UUID owner = UUID.randomUUID();

        assertThatThrownBy(() -> store.store(actor, "TEST", owner, "run.exe", PDF)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> store.store(actor, "TEST", owner, "page.html", "<html>".getBytes())).isInstanceOf(InvalidInputException.class);
        // a program renamed to .png
        assertThatThrownBy(() -> store.store(actor, "TEST", owner, "photo.png", "MZ program".getBytes()))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("does not match");
        assertThatThrownBy(() -> store.store(actor, "TEST", owner, "binary.txt", new byte[] {1, 0, 2}))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> store.store(actor, "TEST", owner, "empty.txt", new byte[0])).isInstanceOf(InvalidInputException.class);
        byte[] big = new byte[(int) FileStore.MAX_BYTES + 1];
        big[0] = '%';
        big[1] = 'P';
        big[2] = 'D';
        big[3] = 'F';
        big[4] = '-';
        assertThatThrownBy(() -> store.store(actor, "TEST", owner, "big.pdf", big))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("10 MB");
        assertThat(store.list("TEST", owner)).isEmpty();
    }

    @Test
    void textZipAndJpegFilesAreAccepted() {
        UUID owner = UUID.randomUUID();

        assertThat(store.store(actor, "TEST", owner, "notes.txt", "Visit notes, Rs. 5".getBytes(StandardCharsets.UTF_8)).contentType())
                .isEqualTo("text/plain");
        assertThat(store.store(actor, "TEST", owner, "a.docx", new byte[] {'P', 'K', 3, 4, 9}).contentType()).contains("wordprocessingml");
        assertThat(store.store(actor, "TEST", owner, "a.xlsx", new byte[] {'P', 'K', 3, 4, 9}).contentType()).contains("spreadsheetml");
        assertThat(store.store(actor, "TEST", owner, "p.JPG", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void aRemovalRecordsWhoWhenAndWhyHidesTheFileAndKeepsTheBytes() {
        UUID owner = UUID.randomUUID();
        FileRef ref = store.store(actor, "TEST", owner, "visit.png", PNG);
        UUID remover = UUID.randomUUID();

        assertThatThrownBy(() -> store.remove(remover, ref.id(), " ")).isInstanceOf(InvalidInputException.class);
        store.remove(remover, ref.id(), "Wrong visit");

        assertThat(store.find(ref.id())).isEmpty();
        assertThat(store.open(ref.id())).isEmpty();
        assertThat(store.list("TEST", owner)).isEmpty();
        var row = jdbc.queryForMap("select removed_by, removal_reason, storage_path from stored_file where id = ?", ref.id());
        assertThat(row.get("removed_by")).isEqualTo(remover);
        assertThat(row.get("removal_reason")).isEqualTo("Wrong visit");
        assertThat(java.nio.file.Files.exists(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "hls-test-files", (String) row.get("storage_path"))))
                .isTrue();
    }

    @Test
    void aFileRowCanNeverBeUpdatedOrDeletedBySql() {
        FileRef ref = store.store(actor, "TEST", UUID.randomUUID(), "visit.png", PNG);

        assertThatThrownBy(() -> jdbc.update("update stored_file set original_name = 'x.png' where id = ?", ref.id()))
                .hasMessageContaining("never changed");
        assertThatThrownBy(() -> jdbc.update("delete from stored_file where id = ?", ref.id())).hasMessageContaining("never deleted");
    }
}
