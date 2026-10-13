package com.hls.attendance.workingdays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.support.MasterDataTestBase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 037 data-model.md: the database refuses what the application rules also refuse, and the history is final. */
class WorkingDaysSchemaTest extends MasterDataTestBase {

    private void insert(UUID schoolId, int year, int month, String days) {
        jdbc.update(
                "insert into school_working_days (id, school_id, year, month, working_days, version, created_by,"
                        + " created_at, updated_by, updated_at) values (?, ?, ?, ?, ?::numeric, 0, ?, now(), ?, now())",
                UUID.randomUUID(),
                schoolId,
                year,
                month,
                days,
                UUID.randomUUID(),
                UUID.randomUUID());
    }

    private void insertHistory(UUID schoolId) {
        jdbc.update(
                "insert into school_working_days_history (id, school_id, year, month, action, new_value, actor_id,"
                        + " actor_roles, occurred_at) values (?, ?, 2027, 3, 'CREATE', 26.0, ?, 'ADMIN', now())",
                UUID.randomUUID(),
                schoolId,
                UUID.randomUUID());
    }

    @Test
    void aSecondRowForTheSameSchoolAndMonthIsRefused() {
        UUID school = UUID.randomUUID();
        insert(school, 2027, 3, "26.0");
        assertThatThrownBy(() -> insert(school, 2027, 3, "25.0")).hasMessageContaining("uq_school_working_days");
        insert(school, 2027, 4, "25.0");
    }

    @Test
    void outOfRangeMonthAndFiguresAreRefused() {
        UUID school = UUID.randomUUID();
        assertThatThrownBy(() -> insert(school, 2027, 13, "10.0")).hasMessageContaining("month");
        assertThatThrownBy(() -> insert(school, 2027, 0, "10.0")).hasMessageContaining("month");
        assertThatThrownBy(() -> insert(school, 2027, 5, "30.7")).hasMessageContaining("working_days");
        assertThatThrownBy(() -> insert(school, 2027, 5, "32")).hasMessageContaining("working_days");
        assertThatThrownBy(() -> insert(school, 2027, 5, "-0.5")).hasMessageContaining("working_days");
        insert(school, 2027, 5, "0");
        insert(school, 2027, 6, "30.5");
    }

    @Test
    void theHistoryCannotBeUpdatedOrDeleted() {
        UUID school = UUID.randomUUID();
        insertHistory(school);
        assertThatThrownBy(() -> jdbc.update(
                        "update school_working_days_history set new_value = 1.0 where school_id = ?", school))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from school_working_days_history where school_id = ?", school))
                .hasMessageContaining("append-only");
    }
}
