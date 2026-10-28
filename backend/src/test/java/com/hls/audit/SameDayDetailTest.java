package com.hls.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.audit.api.SameDayDetail;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SameDayDetailTest {

    @Test
    void namesTheRecorderTheSubjectAndTheAutoAcceptance() {
        UUID by = UUID.randomUUID();
        UUID forId = UUID.randomUUID();
        assertThat(SameDayDetail.of(by, forId))
                .isEqualTo("sameDay=true; recordedBy=" + by + "; for=" + forId + "; auto-accepted");
    }

    @Test
    void ownWorkNamesTheRecorderAsTheSubject() {
        UUID by = UUID.randomUUID();
        assertThat(SameDayDetail.of(by, null)).contains("recordedBy=" + by).contains("for=" + by);
    }
}
