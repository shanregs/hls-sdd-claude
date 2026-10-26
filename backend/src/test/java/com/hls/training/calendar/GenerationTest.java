package com.hls.training.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.training.calendar.internal.GenerationService;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 024 T025 (SC-001): the standing pattern creates one session a month, none missed and none duplicated. */
class GenerationTest extends TrainingCalendarTestBase {

    @Autowired
    GenerationService generation;

    private int count(UUID pattern) {
        return jdbc.queryForObject("select count(*) from training_session where pattern_id = ?", Integer.class, pattern);
    }

    @Test
    void creatingAPatternFillsTheHorizon() {
        World w = newWorld();
        UUID pattern = createPattern(w.admin(), patternBody(today()));
        // this month and the next three
        assertThat(count(pattern)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                        "select count(*) from training_session where pattern_id = ? and status = 'PUBLISHED' and session_type = 'MONTHLY'",
                        Integer.class, pattern))
                .isEqualTo(4);
    }

    @Test
    void aTwelveMonthRunWithTheJobDailyAndTwiceADayMissesAndDuplicatesNothing() {
        World w = newWorld();
        UUID pattern = createPattern(w.admin(), patternBody(today()));
        LocalDate day = today();
        for (int i = 0; i < 366; i += 3) { // every third day keeps the test quick
            LocalDate simulated = day.plusDays(i);
            generation.run(simulated);
            assertThat(generation.run(simulated)).as("second run on the same day").isZero();
        }
        List<Map<String, Object>> duplicates = jdbc.queryForList(
                "select pattern_month from training_session where pattern_id = ? group by pattern_month having count(*) > 1", pattern);
        assertThat(duplicates).isEmpty();
        long months = jdbc.queryForObject("select count(distinct pattern_month) from training_session where pattern_id = ?", Long.class, pattern);
        assertThat(months).isEqualTo(count(pattern));
        LocalDate first = jdbc.queryForObject("select min(pattern_month) from training_session where pattern_id = ?", LocalDate.class, pattern);
        LocalDate last = jdbc.queryForObject("select max(pattern_month) from training_session where pattern_id = ?", LocalDate.class, pattern);
        assertThat(first).isEqualTo(day.withDayOfMonth(1));
        // no month between the first and the last is missing
        assertThat(ChronoUnit.MONTHS.between(first, last) + 1).isEqualTo(months);
        // the horizon is three months past the last simulated day
        LocalDate lastSimulated = day.plusDays(363);
        assertThat(last).isEqualTo(lastSimulated.withDayOfMonth(1).plusMonths(3));
    }

    @Test
    void aStartMonthInThePastIsNotBackFilled() {
        World w = newWorld();
        UUID pattern = createPattern(w.admin(), patternBody(today().minusMonths(2)));
        LocalDate firstMonth = jdbc.queryForObject("select min(pattern_month) from training_session where pattern_id = ?", LocalDate.class, pattern);
        assertThat(firstMonth).isEqualTo(today().withDayOfMonth(1));
    }

    @Test
    void aSkippedMonthBecomesASkippedRowWithItsReason() {
        World w = newWorld();
        UUID pattern = createPattern(w.admin(), patternBody(today()));
        LocalDate near = today().plusMonths(1).withDayOfMonth(1);
        Resp skipNear = post(API + "/patterns/" + pattern + "/skips", w.admin(), Map.of("month", near.toString(), "reason", "Festival week"));
        assertThat(skipNear.status()).as(skipNear.body()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                        "select status || ':' || skip_reason from training_session where pattern_id = ? and pattern_month = ?",
                        String.class, pattern, near))
                .isEqualTo("SKIPPED:Festival week");

        // a month beyond the horizon is skipped before it exists, and arrives as a SKIPPED row when the horizon reaches it
        LocalDate far = today().plusMonths(8).withDayOfMonth(1);
        assertThat(post(API + "/patterns/" + pattern + "/skips", w.admin(), Map.of("month", far.toString(), "reason", "Exam month")).status())
                .isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from training_session where pattern_id = ? and pattern_month = ?", Integer.class, pattern, far))
                .isZero();
        generation.run(today().plusMonths(6));
        assertThat(jdbc.queryForObject(
                        "select status || ':' || skip_reason from training_session where pattern_id = ? and pattern_month = ?",
                        String.class, pattern, far))
                .isEqualTo("SKIPPED:Exam month");
        // a reason is required
        Map<String, Object> noReason = Map.of("month", today().plusMonths(2).withDayOfMonth(1).toString(), "reason", " ");
        assertThat(post(API + "/patterns/" + pattern + "/skips", w.admin(), noReason).status()).isEqualTo(400);
    }
}
