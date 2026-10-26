package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.school.api.InvalidInputException;
import com.hls.training.calendar.internal.SessionRules;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/** Spec 024 T015: status transitions, what may be edited, moved or deleted, time order, https venue, text caps. */
class SessionRulesTest {

    @Test
    void transitionsFollowTheStatusDiagram() {
        assertThat(SessionRules.canTransition("DRAFT", "PUBLISHED")).isTrue();
        assertThat(SessionRules.canTransition("PUBLISHED", "HELD")).isTrue();
        assertThat(SessionRules.canTransition("PUBLISHED", "CANCELLED")).isTrue();
        assertThat(SessionRules.canTransition("PUBLISHED", "SKIPPED")).isTrue();
        assertThat(SessionRules.canTransition("SKIPPED", "PUBLISHED")).isTrue();
        // held and cancelled are final
        assertThat(SessionRules.canTransition("HELD", "PUBLISHED")).isFalse();
        assertThat(SessionRules.canTransition("HELD", "CANCELLED")).isFalse();
        assertThat(SessionRules.canTransition("CANCELLED", "PUBLISHED")).isFalse();
        // a draft cannot be held or cancelled, it is simply deleted
        assertThat(SessionRules.canTransition("DRAFT", "HELD")).isFalse();
        assertThat(SessionRules.canTransition("DRAFT", "CANCELLED")).isFalse();
    }

    @Test
    void whatMayBeDoneInEachStatus() {
        assertThat(SessionRules.editable("DRAFT")).isTrue();
        assertThat(SessionRules.editable("PUBLISHED")).isTrue();
        for (String status : new String[] {"HELD", "CANCELLED", "SKIPPED"}) {
            assertThat(SessionRules.editable(status)).as(status).isFalse();
            assertThat(SessionRules.movable(status)).as(status).isFalse();
            assertThat(SessionRules.deletable(status)).as(status).isFalse();
            assertThat(SessionRules.takesPeople(status)).as(status).isFalse();
        }
        assertThat(SessionRules.movable("PUBLISHED")).isTrue();
        assertThat(SessionRules.cancellable("PUBLISHED")).isTrue();
        assertThat(SessionRules.cancellable("DRAFT")).isFalse();
        assertThat(SessionRules.publishable("DRAFT")).isTrue();
        assertThat(SessionRules.publishable("PUBLISHED")).isFalse();
        // only a Draft can be deleted
        assertThat(SessionRules.deletable("DRAFT")).isTrue();
        assertThat(SessionRules.deletable("PUBLISHED")).isFalse();
        assertThat(SessionRules.takesPeople("PUBLISHED")).isTrue();
        assertThat(SessionRules.takesPeople("DRAFT")).isFalse();
    }

    @Test
    void theEndMustBeAfterTheStart() {
        SessionRules.checkTimes(LocalTime.of(10, 0), LocalTime.of(13, 0));
        assertThatThrownBy(() -> SessionRules.checkTimes(LocalTime.of(13, 0), LocalTime.of(13, 0))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> SessionRules.checkTimes(LocalTime.of(14, 0), LocalTime.of(13, 0))).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> SessionRules.checkTimes(null, LocalTime.of(13, 0))).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void aVirtualVenueMustBeAnHttpsLink() {
        SessionRules.checkVenue("PHYSICAL", "Head office, 2nd floor");
        SessionRules.checkVenue("VIRTUAL", "https://meet.example.org/abc");
        SessionRules.checkVenue("VIRTUAL", null);
        for (String bad : new String[] {"http://meet.example.org/abc", "javascript:alert(1)", "meet.example.org/abc", "https://"}) {
            assertThatThrownBy(() -> SessionRules.checkVenue("VIRTUAL", bad)).as(bad).isInstanceOf(InvalidInputException.class);
        }
        assertThatThrownBy(() -> SessionRules.checkVenue("ONLINE", "x")).isInstanceOf(InvalidInputException.class);
        assertThat(SessionRules.isHttps("HTTPS://Example.org")).isTrue();
        assertThat(SessionRules.isHttps("ftp://example.org")).isFalse();
    }

    @Test
    void textLengthsAreCapped() {
        assertThat(SessionRules.required("The topic", " Phonics ", SessionRules.TOPIC_MAX)).isEqualTo("Phonics");
        assertThatThrownBy(() -> SessionRules.required("The topic", " ", SessionRules.TOPIC_MAX)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> SessionRules.required("The topic", "x".repeat(201), SessionRules.TOPIC_MAX))
                .isInstanceOf(InvalidInputException.class);
        assertThat(SessionRules.required("The topic", "x".repeat(200), SessionRules.TOPIC_MAX)).hasSize(200);
        assertThat(SessionRules.optional("The description", "", SessionRules.DESCRIPTION_MAX)).isNull();
        assertThatThrownBy(() -> SessionRules.optional("The description", "x".repeat(1001), SessionRules.DESCRIPTION_MAX))
                .isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> SessionRules.checkMax("The reason", "x".repeat(301), SessionRules.REASON_MAX))
                .isInstanceOf(InvalidInputException.class);
    }
}
