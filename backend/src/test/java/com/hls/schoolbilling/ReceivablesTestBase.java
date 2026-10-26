package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Helpers for the spec 022 tests: a School with a contract, mapped Teachers, raising dues, recording collections. */
public abstract class ReceivablesTestBase extends SchoolContractsTestBase {

    protected static final String BILLING = "/api/v1/school-billing";

    protected static YearMonth thisMonth() {
        return YearMonth.now(ZoneId.of("Asia/Kolkata"));
    }

    protected static BigDecimal dec(Object value) {
        return new BigDecimal(String.valueOf(value)).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** A School with a different-salary contract that covers the whole of this month. */
    protected Fixture billedSchool(String admin, String... salaries) {
        Fixture f = fixture(admin);
        UUID dir = director().userId();
        contractId(createContract(admin, f.schoolId(), startedDaysAgo(perTeacherBody(f, dir, salaries), 40)));
        return f;
    }

    protected UUID positionId(String admin, UUID schoolId, int index) {
        return UUID.fromString((String) positionsOf(contractsOf(admin, schoolId).get(0)).get(index).get("id"));
    }

    /** A new Teacher mapped to the School's position {@code index} from {@code from}. */
    protected UUID mapTeacher(String admin, Fixture f, int index, LocalDate from) {
        UUID teacher = teacher(admin);
        Resp resp = assign(admin, teacher, f.schoolId(), positionId(admin, f.schoolId(), index), from);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return teacher;
    }

    protected Resp raise(String token, YearMonth month, UUID... schools) {
        return post(BILLING + "/dues/raise", token, Map.of("month", month.toString(), "schoolIds", List.of(schools)));
    }

    /** Raises the School's due for the month and returns its id. */
    @SuppressWarnings("unchecked")
    protected UUID raiseOne(String admin, YearMonth month, UUID schoolId) {
        Resp resp = raise(admin, month, schoolId);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        List<Map<String, Object>> raised = (List<Map<String, Object>>) resp.map().get("raised");
        assertThat(raised).as(resp.body()).hasSize(1);
        return UUID.fromString((String) raised.get(0).get("id"));
    }

    protected Resp dueDetail(String token, UUID dueId) {
        return get(BILLING + "/dues/" + dueId, token);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> dueOf(String token, UUID dueId) {
        Resp resp = dueDetail(token, dueId);
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return (Map<String, Object>) resp.map().get("due");
    }

    protected Map<String, Object> collectionBody(UUID schoolId, String amount) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("schoolId", schoolId);
        body.put("amount", amount);
        body.put("receivedOn", LocalDate.now(ZoneId.of("Asia/Kolkata")).toString());
        body.put("mode", "BANK");
        body.put("reference", "REF-" + UUID.randomUUID().toString().substring(0, 8));
        body.put("receiver", "Accounts");
        return body;
    }

    protected Resp collect(String token, Map<String, Object> body) {
        return post(BILLING + "/collections", token, body);
    }

    /** Moves a due's dates back so it is overdue (tests only; the ledger tables forbid this in production by design). */
    protected void makeOverdue(UUID dueId, int daysPastDue) {
        jdbc.execute("alter table monthly_due disable trigger trg_monthly_due_write_once");
        try {
            jdbc.update("update monthly_due set due_on = current_date - ? where id = ?", daysPastDue, dueId);
        } finally {
            jdbc.execute("alter table monthly_due enable trigger trg_monthly_due_write_once");
        }
    }
}
