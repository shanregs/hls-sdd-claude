package com.hls.recruitment.marketing;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class MarketingAuditTest extends MarketingTestBase {

    @Test
    void eachWriteHasAnAuditEntryWithActorAndValues() {
        String admin = admin();
        UUID zone = zone(admin);
        UUID prospect = prospect(admin, zone);
        UUID visit = activity(admin, prospect, today.plusDays(1));
        UUID file = UUID.fromString((String) upload(admin, visit, "p.png", PNG).map().get("fileId"));
        post(M + "/activities/" + visit + "/reschedule", admin, Map.of("date", today.plusDays(5).toString()));
        complete(admin, visit, "Done", null);
        post(M + "/prospects/" + prospect + "/owner", directorToken(), Map.of("ownerUserId", signInAs(Role.MANAGER).userId()));
        assertThat(file).isNotNull();

        assertChangeRecorded(admin, "PROSPECT", prospect, "created");
        assertChangeRecorded(admin, "PROSPECT", prospect, "owner");
        assertChangeRecorded(admin, "MARKETING_ACTIVITY", visit, "planned");
        assertChangeRecorded(admin, "MARKETING_ACTIVITY", visit, "date");
        assertChangeRecorded(admin, "MARKETING_ACTIVITY", visit, "status");
        assertChangeRecorded(admin, "ACTIVITY_ATTACHMENT", visit, "added");
    }

    @Test
    void aProspectPhoneNumberIsNeverWrittenToTheLogOrTheAuditValues() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            String admin = admin();
            UUID zone = zone(admin);
            Map<String, Object> body = prospectBody(zone, uniqueName("Phone School"));
            body.put("phone", "9876501234");
            UUID prospect = UUID.fromString((String) ((Map<?, ?>) post(M + "/prospects", admin, body).map().get("row")).get("id"));
            body.put("phone", "9876505678");
            body.put("version", 0);
            put(M + "/prospects/" + prospect, admin, body);

            List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(lines).noneMatch(l -> l.contains("9876501234") || l.contains("9876505678"));
            Integer inAudit = jdbc.queryForObject(
                    "select count(*) from change_history_entry where entity_id = ? and (before_value like '%98765%' or after_value like '%98765%')",
                    Integer.class,
                    prospect.toString());
            assertThat(inAudit).isZero();
        } finally {
            root.detachAppender(appender);
        }
        assertThat(LocalDate.now()).isNotNull();
    }
}
