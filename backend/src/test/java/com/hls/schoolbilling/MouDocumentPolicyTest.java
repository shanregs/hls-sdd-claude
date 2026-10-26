package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.files.api.FileStore;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Spec 031 FR-003 and FR-004: pdf, jpg, jpeg and png up to 10 MB; anything else is refused and nothing is stored. */
class MouDocumentPolicyTest extends MouTestBase {

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    @Autowired
    private JdbcTemplate db;

    private long storedFiles() {
        return db.queryForObject("select count(*) from stored_file where owner_type = 'MOU_DOCUMENT'", Long.class);
    }

    private long documentRows(UUID contract) {
        return db.queryForObject("select count(*) from mou_document where contract_id = ?", Long.class, contract);
    }

    @Test
    void pdfJpgJpegAndPngAreAcceptedEvenInUpperCase() {
        Mou m = mou();
        assertThat(upload(m.admin(), m.contractId(), "a.pdf", PDF).status()).isEqualTo(201);
        assertThat(upload(m.admin(), m.contractId(), "b.JPG", JPG).status()).isEqualTo(201);
        assertThat(upload(m.admin(), m.contractId(), "c.jpeg", JPG).status()).isEqualTo(201);
        assertThat(upload(m.admin(), m.contractId(), "d.PNG", PNG).status()).isEqualTo(201);
        assertThat(documentRows(m.contractId())).isEqualTo(4);
    }

    @Test
    void otherTypesTheStoreWouldAllowAreRefusedByExtensionBeforeTheStoreIsCalled() {
        Mou m = mou();
        long files = storedFiles();
        for (String name : new String[] {"a.docx", "a.xlsx", "a.txt", "a.exe", "a", "a."}) {
            Resp resp = upload(m.admin(), m.contractId(), name, "PK data".getBytes(StandardCharsets.ISO_8859_1));
            assertThat(resp.status()).as(name).isEqualTo(400);
            assertThat(resp.body()).as(name).contains("Only PDF, JPG or PNG files can be uploaded.");
        }
        assertThat(storedFiles()).isEqualTo(files);
        assertThat(documentRows(m.contractId())).isZero();
    }

    @Test
    void aProgramRenamedToPdfAnEmptyFileAndAnOversizeFileAreRefusedAndNothingIsStored() {
        Mou m = mou();
        long files = storedFiles();

        Resp renamed = upload(m.admin(), m.contractId(), "run.pdf", "MZ this is a program".getBytes(StandardCharsets.UTF_8));
        Resp empty = upload(m.admin(), m.contractId(), "empty.pdf", new byte[0]);
        byte[] big = new byte[(int) FileStore.MAX_BYTES + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        Resp oversize = upload(m.admin(), m.contractId(), "big.pdf", big);

        assertThat(renamed.status()).as(renamed.body()).isEqualTo(400);
        assertThat(renamed.body()).contains("does not match");
        assertThat(empty.status()).as(empty.body()).isEqualTo(400);
        assertThat(oversize.status()).as(oversize.body()).isEqualTo(400);
        assertThat(oversize.body()).contains("10 MB");
        assertThat(storedFiles()).isEqualTo(files);
        assertThat(documentRows(m.contractId())).isZero();
        assertThat(stateOf(m.admin(), m.contractId())).isEqualTo("DRAFT");
    }
}
