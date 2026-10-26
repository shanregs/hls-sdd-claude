package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.support.MasterDataTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** Spec 024 T005: the schema itself refuses what the rules forbid. */
class TrainingMigrationTest extends MasterDataTestBase {

    private UUID session(UUID patternId, LocalDate month, String status, String start, String end) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into training_session (id, session_type, topic, session_date, starts_at, ends_at, venue_kind, status,"
                        + " pattern_id, pattern_month, hand_edited, version, created_by, created_at, updated_at)"
                        + " values (?, 'MONTHLY', 'Topic', ?, ?::time, ?::time, 'PHYSICAL', ?, ?, ?, false, 0, ?, now(), now())",
                id, LocalDate.now().plusDays(10), start, end, status, patternId, month, UUID.randomUUID());
        return id;
    }

    private UUID pattern(String ruleKind, Integer nth, Integer weekday, Integer dayOfMonth, String start, LocalDate endMonth) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into training_pattern (id, rule_kind, nth, weekday, day_of_month, starts_at, ends_at, topic, venue_kind,"
                        + " start_month, end_month, version, created_by, created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?::time, '13:00', 'Topic', 'PHYSICAL', ?, ?, 0, ?, now(), now())",
                id, ruleKind, nth, weekday, dayOfMonth, start, LocalDate.now().withDayOfMonth(1), endMonth, UUID.randomUUID());
        return id;
    }

    @Test
    void sessionTimesAndPatternRulesAreChecked() {
        assertThatThrownBy(() -> session(null, null, "DRAFT", "13:00", "10:00")).isInstanceOf(DataAccessException.class);
        assertThat(session(null, null, "DRAFT", "10:00", "13:00")).isNotNull();
        // a rule that mixes the two kinds is refused
        assertThatThrownBy(() -> pattern("NTH_WEEKDAY", 2, 6, 14, "10:00", null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> pattern("DAY_OF_MONTH", null, null, 32, "10:00", null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> pattern("NTH_WEEKDAY", 6, 6, null, "10:00", null)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void oneSessionPerPatternAndMonth() {
        UUID pattern = pattern("NTH_WEEKDAY", 3, 6, null, "09:15", LocalDate.now().withDayOfMonth(1));
        LocalDate month = LocalDate.now().withDayOfMonth(1);
        session(pattern, month, "PUBLISHED", "09:15", "13:00");
        assertThatThrownBy(() -> session(pattern, month, "PUBLISHED", "09:15", "13:00")).isInstanceOf(DataAccessException.class);
        assertThat(session(pattern, month.plusMonths(1), "PUBLISHED", "09:15", "13:00")).isNotNull();
    }

    @Test
    void aSkippedSessionMustComeFromAPattern() {
        assertThatThrownBy(() -> session(null, null, "SKIPPED", "10:00", "13:00")).isInstanceOf(DataAccessException.class);
    }

    @Test
    void aSecondActivePatternWithTheSameRuleAndTimeIsRefused() {
        pattern("NTH_WEEKDAY", 4, 7, null, "07:05", null);
        assertThatThrownBy(() -> pattern("NTH_WEEKDAY", 4, 7, null, "07:05", null)).isInstanceOf(DataAccessException.class);
        // a different time, or a stopped pattern, is fine
        assertThat(pattern("NTH_WEEKDAY", 4, 7, null, "07:10", null)).isNotNull();
        assertThat(pattern("NTH_WEEKDAY", 4, 7, null, "07:05", LocalDate.now().withDayOfMonth(1))).isNotNull();
    }

    @Test
    void oneAssignmentRegistrationEntryAndExceptionPerPersonPerSession() {
        UUID s = session(null, null, "PUBLISHED", "10:00", "13:00");
        UUID person = UUID.randomUUID();
        String assign = "insert into training_assignment (id, session_id, person_kind, person_id, source, status, assigned_by,"
                + " assigned_at, version) values (?, ?, 'TEACHER', ?, 'INDIVIDUAL', 'REQUIRED', ?, now(), 0)";
        jdbc.update(assign, UUID.randomUUID(), s, person, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update(assign, UUID.randomUUID(), s, person, UUID.randomUUID())).isInstanceOf(DataAccessException.class);

        String register = "insert into training_registration (id, session_id, teacher_id, status, registered_by, registered_at, version)"
                + " values (?, ?, ?, 'REGISTERED', ?, now(), 0)";
        jdbc.update(register, UUID.randomUUID(), s, person, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update(register, UUID.randomUUID(), s, person, UUID.randomUUID())).isInstanceOf(DataAccessException.class);

        UUID entry = UUID.randomUUID();
        String record = "insert into training_attendance_entry (id, session_id, person_kind, person_id, basis, outcome, day_value,"
                + " sync_state, recorded_by, recorded_at, version) values (?, ?, 'TEACHER', ?, 'ASSIGNED', ?, ?, 'NOT_NEEDED', ?, now(), 0)";
        jdbc.update(record, entry, s, person, "PRESENT", java.math.BigDecimal.ONE, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update(record, UUID.randomUUID(), s, person, "PRESENT", java.math.BigDecimal.ONE, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);

        String raise = "insert into training_exception (id, session_id, person_kind, person_id, entry_id, status, raised_at, version)"
                + " values (?, ?, 'TEACHER', ?, ?, 'OPEN', now(), 0)";
        jdbc.update(raise, UUID.randomUUID(), s, person, entry);
        assertThatThrownBy(() -> jdbc.update(raise, UUID.randomUUID(), s, person, entry)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void aPresentEntryNeedsAWholeOrHalfDayAndAWalkInIsPresent() {
        UUID s = session(null, null, "PUBLISHED", "10:00", "13:00");
        String record = "insert into training_attendance_entry (id, session_id, person_kind, person_id, basis, outcome, day_value,"
                + " sync_state, recorded_by, recorded_at, version) values (?, ?, 'TEACHER', ?, ?, ?, ?, 'NOT_NEEDED', ?, now(), 0)";
        assertThatThrownBy(() -> jdbc.update(record, UUID.randomUUID(), s, UUID.randomUUID(), "ASSIGNED", "PRESENT", new java.math.BigDecimal("0.75"), UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(record, UUID.randomUUID(), s, UUID.randomUUID(), "WALK_IN", "ABSENT", null, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
        jdbc.update(record, UUID.randomUUID(), s, UUID.randomUUID(), "ASSIGNED", "PRESENT", new java.math.BigDecimal("0.50"), UUID.randomUUID());
    }

    @Test
    void theHistoryTablesRefuseUpdateAndDelete() {
        UUID s = session(null, null, "PUBLISHED", "10:00", "13:00");
        UUID history = UUID.randomUUID();
        jdbc.update("insert into training_session_history (id, session_id, field, before_value, after_value, changed_at)"
                + " values (?, ?, 'date', '2026-11-01', '2026-11-08', now())", history, s);
        assertThatThrownBy(() -> jdbc.update("update training_session_history set after_value = 'x' where id = ?", history))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from training_session_history where id = ?", history))
                .isInstanceOf(DataAccessException.class);

        UUID entry = UUID.randomUUID();
        jdbc.update("insert into training_attendance_entry (id, session_id, person_kind, person_id, basis, outcome, sync_state,"
                + " recorded_by, recorded_at, version) values (?, ?, 'TEACHER', ?, 'ASSIGNED', 'ABSENT', 'NOT_NEEDED', ?, now(), 0)",
                entry, s, UUID.randomUUID(), UUID.randomUUID());
        UUID row = UUID.randomUUID();
        jdbc.update("insert into training_attendance_history (id, entry_id, action, outcome, changed_by, changed_at)"
                + " values (?, ?, 'CREATED', 'ABSENT', ?, now())", row, entry, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update training_attendance_history set outcome = 'PRESENT' where id = ?", row))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from training_attendance_history where id = ?", row))
                .isInstanceOf(DataAccessException.class);
    }
}
