package com.hls.peoplereports;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.transfer.TransferTestBase;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 014c US3: transfers and replacements in the Teacher report are counted in the month of their effective date only. */
class TeacherReportMovementTest extends TransferTestBase {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> teacherRow(Resp resp, UUID teacher) {
        Map<String, Object> section = ((List<Map<String, Object>>) resp.map().get("sections")).stream()
                .filter(s -> "teachers".equals(s.get("key")))
                .findFirst()
                .orElseThrow();
        return ((List<Map<String, Object>>) section.get("rows")).stream()
                .filter(r -> teacher.toString().equals(r.get("id")))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static long cell(Map<String, Object> row, int index) {
        return ((Number) ((List<Map<String, Object>>) row.get("cells")).get(index).get("value")).longValue();
    }

    @Test
    void anApprovedTransferIsCountedInItsMonthOnly() {
        World w = world();
        UUID request = newTransfer(w.managerA().token(), w.t1(), w.schoolB1(), in(3));
        assertThat(approve(w.directorSigned().token(), request).status()).isEqualTo(200);
        YearMonth month = YearMonth.from(in(3));

        Resp inMonth = get("/api/v1/reports/teacher?month=" + month, w.admin());
        Resp later = get("/api/v1/reports/teacher?month=" + month.plusMonths(2), w.admin());

        assertThat(inMonth.status()).as(inMonth.body()).isEqualTo(200);
        assertThat(cell(teacherRow(inMonth, w.t1()), 7)).as("transfers").isEqualTo(1);
        assertThat(cell(teacherRow(inMonth, w.t1()), 8)).as("replacements").isZero();
        assertThat(cell(teacherRow(later, w.t1()), 7)).isZero();
    }

    @Test
    void aReplacementCountsForTheOutgoingTeacher() {
        World w = world();
        UUID request = newReplacement(w.managerA().token(), w.t1(), w.t3(), in(3));
        assertThat(approve(w.directorSigned().token(), request).status()).isEqualTo(200);

        Resp resp = get("/api/v1/reports/teacher?month=" + YearMonth.from(in(3)), w.admin());

        assertThat(cell(teacherRow(resp, w.t1()), 8)).isEqualTo(1);
        assertThat(cell(teacherRow(resp, w.t1()), 7)).isZero();
    }
}
