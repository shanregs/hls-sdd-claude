package com.hls.leave;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.leave.api.LeaveTypes;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 009 amendment A3: the Loss-of-Pay leave type behaves like any type and is identifiable by payroll. */
class LeaveLossOfPayTest extends LeaveTestBase {

    @Autowired
    private LeaveTypes leaveTypes;

    private UUID lop() {
        return jdbc.queryForObject("select id from leave_type where code = 'LOP'", UUID.class);
    }

    private Map<String, Object> lopDraft(LocalDate first, LocalDate last) {
        Map<String, Object> body = new HashMap<>(draft(first, last));
        body.put("leaveTypeId", lop().toString());
        return body;
    }

    @Test
    void theTypeIsSeededWithTheStableCodeAndListedToATeacherAfterTheOthers() {
        assertThat(LeaveTypes.LOSS_OF_PAY_CODE).isEqualTo("LOP");
        assertThat(jdbc.queryForList("select code from leave_type order by sort_order", String.class))
                .containsExactly("CASUAL", "SICK", "PERSONAL", "OTHER", "LOP");

        World w = newWorld();
        Resp types = get(ME + "/types", w.teacherA().token());

        assertThat(types.status()).isEqualTo(200);
        assertThat(types.body()).contains("\"code\":\"LOP\"").contains("Loss of Pay");
        assertThat(types.body()).contains("\"code\":\"CASUAL\"").contains("\"code\":\"OTHER\"");
    }

    @Test
    void applyingApprovingAndCancellingBehaveLikeAnyOtherType() {
        World w = newWorld();
        LocalDate mon = monday();

        Resp preview = post(ME + "/preview", w.teacherA().token(), lopDraft(mon, mon.plusDays(4)));
        assertThat(preview.status()).as(preview.body()).isEqualTo(200);
        assertThat(new java.math.BigDecimal(field(preview.body(), "workingDays"))).isEqualByComparingTo("5");

        Resp applied = post(ME, w.teacherA().token(), lopDraft(mon, mon.plusDays(2)));
        assertThat(applied.status()).as(applied.body()).isEqualTo(201);
        String id = field(applied.body(), "id");
        assertThat(applied.body()).contains("Loss of Pay").contains("\"status\":\"PENDING\"");

        Resp overlap = post(ME, w.teacherA().token(), draft(mon, mon));
        assertThat(overlap.status()).as("overlap rule applies across types").isEqualTo(409);

        Resp approved = post(SUP + "/" + id + "/approve", w.managerA().token(), Map.of("version", 0));
        assertThat(approved.status()).as(approved.body()).isEqualTo(200);
        Map<String, Object> mark = markRow(w.teacherA().teacherId(), mon);
        assertThat(mark.get("short_code")).isEqualTo("L");
        assertThat(mark.get("leave_request_id")).isEqualTo(UUID.fromString(id));

        Resp revoked = post(SUP + "/" + id + "/revoke", w.managerA().token(), Map.of("reason", "Needed", "version", 1));
        assertThat(revoked.status()).as(revoked.body()).isEqualTo(200);
        assertThat(markRow(w.teacherA().teacherId(), mon)).isNull();

        String second = apply(w.teacherA(), mon.plusDays(7), mon.plusDays(8));
        assertThat(post(ME + "/" + second + "/cancel", w.teacherA().token(), Map.of()).status()).isEqualTo(200);
    }

    @Test
    void thePastLimitAndTheLockedMonthRuleApplyToLossOfPayToo() {
        World w = newWorld();
        LocalDate old = today().minusDays(31);

        Resp resp = post(ME, w.teacherA().token(), lopDraft(old, old.plusDays(1)));

        assertThat(resp.status()).isEqualTo(409);
        assertThat(resp.body()).contains("30 days");
    }

    @Test
    void otherModulesCanTellALossOfPayRequestFromAnyOtherWithoutLeaveTables() {
        World w = newWorld();
        LocalDate mon = monday();
        String lopId = field(post(ME, w.teacherA().token(), lopDraft(mon, mon)).body(), "id");
        String casualId = apply(w.teacherA(), mon.plusDays(7), mon.plusDays(7));

        assertThat(leaveTypes.isLossOfPay(UUID.fromString(lopId))).isTrue();
        assertThat(leaveTypes.isLossOfPay(UUID.fromString(casualId))).isFalse();
        assertThat(leaveTypes.isLossOfPay(UUID.randomUUID())).isFalse();
        assertThat(leaveTypes.isLossOfPay(null)).isFalse();
        assertThat(leaveTypes.lossOfPayRequests(Set.of(UUID.fromString(lopId), UUID.fromString(casualId))))
                .containsExactly(UUID.fromString(lopId));
        assertThat(leaveTypes.lossOfPayRequests(Set.of())).isEmpty();
    }

    @Test
    void anApprovedLossOfPayDayIsIdentifiableFromItsAttendanceMark() {
        World w = newWorld();
        LocalDate mon = monday();
        String lopId = field(post(ME, w.teacherA().token(), lopDraft(mon, mon)).body(), "id");
        String casualId = apply(w.teacherA(), mon.plusDays(1), mon.plusDays(1));
        post(SUP + "/" + lopId + "/approve", w.managerA().token(), Map.of("version", 0));
        post(SUP + "/" + casualId + "/approve", w.managerA().token(), Map.of("version", 0));

        UUID lopMark = (UUID) markRow(w.teacherA().teacherId(), mon).get("leave_request_id");
        UUID casualMark = (UUID) markRow(w.teacherA().teacherId(), mon.plusDays(1)).get("leave_request_id");

        assertThat(leaveTypes.lossOfPayRequests(Set.of(lopMark, casualMark))).containsExactly(lopMark);
    }
}
