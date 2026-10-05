package com.hls.recruitment;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Every college, drive, candidate, outcome, assessment and import is audited, and phones never reach the log. */
class RecruitmentAuditTest extends RecruitmentTestBase {

    @Test
    void eachWriteHasAnAuditEntry() {
        String admin = admin();
        UUID college = college(admin);
        UUID drive = drive(admin, college, List.of(), today.plusDays(3));
        UUID candidate = candidate(admin, drive);
        assertThat(outcome(admin, candidate, "SELECTED").status()).isEqualTo(200);
        assertThat(post(BASE + "/candidates/" + candidate + "/assessment", admin, Map.of("scores", Map.of("SPEAKING", 4))).status())
                .isEqualTo(200);
        assertThat(upload(admin, drive, "name,phone\nAsha," + phone() + "\n").status()).isEqualTo(200);
        assertThat(post(BASE + "/drives/" + drive + "/status", admin, Map.of("status", "HELD")).status()).isEqualTo(200);

        assertChangeRecorded(admin, "COLLEGE", college, "created");
        assertChangeRecorded(admin, "COLLEGE", college, "principal");
        assertChangeRecorded(admin, "CAMPUS_DRIVE", drive, "created");
        assertChangeRecorded(admin, "CAMPUS_DRIVE", drive, "status");
        assertChangeRecorded(admin, "CANDIDATE", candidate, "created");
        assertChangeRecorded(admin, "CANDIDATE", candidate, "outcome");
        assertChangeRecorded(admin, "CANDIDATE", drive, "imported");
        assertChangeRecorded(admin, "ASSESSMENT", candidate, "assessment 1");
    }

    @Test
    void aCandidatePhoneNumberIsNeverWrittenToTheLog() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            String admin = admin();
            UUID drive = drive(admin);
            String phone = phone();
            assertThat(addCandidate(admin, drive, "Logged Out", phone).status()).isEqualTo(201);
            assertThat(upload(admin, drive, "name,phone\nCsv Person," + phone() + "\nBad Row,5551\n").status()).isEqualTo(200);
            String csvPhone = jdbc.queryForObject("select phone from candidate where name = 'Csv Person' order by created_at desc limit 1", String.class);

            List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(lines).noneMatch(l -> l.contains(phone));
            assertThat(lines).noneMatch(l -> csvPhone != null && l.contains(csvPhone));
        } finally {
            root.detachAppender(appender);
        }
    }
}
