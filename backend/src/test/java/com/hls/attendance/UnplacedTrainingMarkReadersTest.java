package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.TrainingAttendance;
import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import com.hls.teacher.api.TeacherRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Amendment A5 (task T065): every reader of {@code attendance_mark.school_id} copes with a mark that has no School,
 * so an unplaced Teacher's induction day never turns a month view, a history or the CSV export into a 500.
 */
class UnplacedTrainingMarkReadersTest extends MasterDataTestBase {

    @Autowired
    TrainingAttendance training;

    @Autowired
    TeacherRegistry registry;

    @Test
    void monthViewHistoryGridAndCsvAllShowATrainingDayForAnUnplacedTeacher() {
        String admin = signInAs(Role.ADMIN).token();
        UUID actor = signInAs(Role.ADMIN).userId();
        String phone = "9" + String.format("%09d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 1_000_000_000L);
        UUID teacher = registry.createTrainee(actor, new TeacherRegistry.Candidate("Unplaced " + phone, phone, null, null));
        LocalDate day = LocalDate.now().minusDays(1);
        training.markTrainingDay(actor, teacher, day, new BigDecimal("1.00"));
        YearMonth month = YearMonth.from(day);

        Resp view = get("/api/v1/attendance/teachers/" + teacher + "?month=" + month, admin);
        assertThat(view.status()).as(view.body()).isEqualTo(200);
        assertThat(view.body()).contains("\"T\"").contains("Training day");
        @SuppressWarnings("unchecked")
        Map<String, Object> rollup = (Map<String, Object>) view.map().get("rollup");
        assertThat(new BigDecimal(rollup.get("trainingAttended").toString())).isEqualByComparingTo("1.00");
        assertThat(new BigDecimal(rollup.get("workingDays").toString())).isEqualByComparingTo("0");

        Resp history = get("/api/v1/attendance/teachers/" + teacher + "/marks/" + day + "/history", admin);
        assertThat(history.status()).as(history.body()).isEqualTo(200);

        Resp grid = get("/api/v1/attendance/grid?month=" + month + "&query=" + phone, admin);
        assertThat(grid.status()).as(grid.body()).isEqualTo(200);

        Resp csv = get("/api/v1/attendance/export?month=" + month + "&query=" + phone, admin);
        assertThat(csv.status()).as(csv.body()).isEqualTo(200);
    }

    @Test
    void theTeacherMonthReadApiListsTheTrainingMarkWithADashForTheSchool() {
        UUID actor = signInAs(Role.ADMIN).userId();
        String phone = "9" + String.format("%09d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 1_000_000_000L);
        UUID teacher = registry.createTrainee(actor, new TeacherRegistry.Candidate("Reader " + phone, phone, null, null));
        LocalDate day = LocalDate.now().minusDays(1);
        training.markTrainingDay(actor, teacher, day, new BigDecimal("0.50"));

        List<com.hls.attendance.api.MarkView> marks = readApi.marksOf(teacher, YearMonth.from(day));

        assertThat(marks).hasSize(1);
        assertThat(marks.get(0).schoolId()).isNull();
        assertThat(marks.get(0).schoolName()).isNotBlank();
        assertThat(marks.get(0).category()).isEqualTo("TRAINING");
    }

    @Autowired
    com.hls.attendance.api.AttendanceReadApi readApi;
}
