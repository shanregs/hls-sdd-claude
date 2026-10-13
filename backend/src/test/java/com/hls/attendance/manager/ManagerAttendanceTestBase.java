package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.identity.user.Role;
import com.hls.support.AttendanceTestBase;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

/** Helpers shared by the spec 032 integration tests: Managers with a joining date, day rows and the settings row. */
public abstract class ManagerAttendanceTestBase extends AttendanceTestBase {

    protected static final String ME = "/api/v1/manager-attendance/me";
    protected static final String ATT = "/api/v1/manager-attendance";
    protected static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    protected static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};

    @Autowired
    protected WorkingCalendar workingCalendar;

    @AfterEach
    void restoreSettings() {
        jdbc.update("update manager_attendance_setting set late_after = time '09:30', missed_cutoff = time '10:30' where id = 1");
    }

    /** A Manager who joined 60 days ago, signed in. */
    protected ManagerCtx joinedManager(String adminToken) {
        ManagerCtx manager = newManager(adminToken);
        jdbc.update("update manager set joining_date = ? where id = ?", today().minusDays(60), manager.managerId());
        return manager;
    }

    protected void setJoining(UUID managerId, LocalDate joining, LocalDate exit) {
        jdbc.update("update manager set joining_date = ?, exit_date = ? where id = ?", joining, exit, managerId);
    }

    protected static Map<String, Object> here(String dayType) {
        Map<String, Object> body = new HashMap<>();
        body.put("dayType", dayType);
        body.put("latitude", 12.971599);
        body.put("longitude", 77.594566);
        body.put("accuracyMeters", 18.5);
        return body;
    }

    protected static Map<String, Object> place() {
        Map<String, Object> body = new HashMap<>();
        body.put("latitude", 12.97);
        body.put("longitude", 77.59);
        return body;
    }

    protected Resp checkIn(String token, String dayType) {
        return post(ME + "/check-in", token, here(dayType));
    }

    protected Resp checkOut(String token) {
        return post(ME + "/check-out", token, place());
    }

    /** Inserts a day row directly, for dates the clock cannot reach. Times are given as instants. */
    protected UUID seedDay(UUID managerId, LocalDate date, Instant checkIn, Instant checkOut) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into manager_day (id, manager_id, day_date, day_type, check_in_at, check_in_lat, check_in_lng,"
                        + " check_out_at, check_out_lat, check_out_lng, version)"
                        + " values (?, ?, ?, 'OFFICE', ?, 12.97, 77.59, ?, ?, ?, 0)",
                id,
                managerId,
                date,
                checkIn == null ? null : java.sql.Timestamp.from(checkIn),
                checkOut == null ? null : java.sql.Timestamp.from(checkOut),
                checkOut == null ? null : java.math.BigDecimal.valueOf(12.97),
                checkOut == null ? null : java.math.BigDecimal.valueOf(77.59));
        return id;
    }

    protected void seedLeave(UUID managerId, LocalDate date, double fraction) {
        jdbc.update(
                "insert into manager_day (id, manager_id, day_date, leave_request_id, leave_fraction, leave_unpaid, version)"
                        + " values (?, ?, ?, ?, ?, false, 0)",
                UUID.randomUUID(),
                managerId,
                date,
                UUID.randomUUID(),
                java.math.BigDecimal.valueOf(fraction));
    }

    protected void lockMonth(UUID managerId, YearMonth month) {
        jdbc.update(
                "insert into manager_month (manager_id, year_month, state, locked_at, version)"
                        + " values (?, ?, 'LOCKED', now(), 0)",
                managerId,
                month.toString());
    }

    protected long dayRows(UUID managerId) {
        return jdbc.queryForObject("select count(*) from manager_day where manager_id = ?", Long.class, managerId);
    }

    protected long historyRows(UUID managerId, String action) {
        return jdbc.queryForObject(
                "select count(*) from manager_day_history h join manager_day d on d.id = h.day_id"
                        + " where d.manager_id = ? and h.action = ?",
                Long.class,
                managerId,
                action);
    }

    protected Signed teacher() {
        return signInAs(Role.TEACHER);
    }

    /** A multipart upload of one file named {@code file}. */
    protected Resp upload(String uri, String token, String filename, byte[] content) {
        String boundary = "----hls" + UUID.randomUUID();
        String crlf = "\r\n";
        byte[] head = ("--" + boundary + crlf + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\""
                        + crlf + "Content-Type: application/octet-stream" + crlf + crlf)
                .getBytes(StandardCharsets.UTF_8);
        byte[] tail = (crlf + "--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + content.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(content, 0, body, head.length, content.length);
        System.arraycopy(tail, 0, body, head.length + content.length, tail.length);
        var result = client.post()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
                .body(body)
                .exchange()
                .returnResult(String.class);
        assertThat(result.getStatus()).isNotNull();
        return new Resp(result.getStatus().value(), result.getResponseBody());
    }

    /** A Manager who joined six months ago, so every day of last month is inside the employment. */
    protected ManagerCtx longJoinedManager(String adminToken) {
        ManagerCtx manager = newManager(adminToken);
        jdbc.update("update manager set joining_date = ? where id = ?", today().minusMonths(6), manager.managerId());
        return manager;
    }

    protected YearMonth lastMonth() {
        return YearMonth.from(today()).minusMonths(1);
    }

    protected List<LocalDate> workingDates(YearMonth month) {
        return workingCalendar.workingDays(null, month.atDay(1), month.atEndOfMonth());
    }

    protected Instant instant(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(businessCalendar.zone()).toInstant();
    }

    protected UUID seedFieldDay(UUID managerId, LocalDate date, String purpose, Instant checkIn, Instant checkOut) {
        UUID id = seedDay(managerId, date, checkIn, checkOut);
        jdbc.update("update manager_day set day_type = 'FIELD', purpose = ? where id = ?", purpose, id);
        return id;
    }

    protected String auditBody(String adminToken, String entityType, Object entityId) {
        return get("/api/v1/audit/change-history?entityType=" + entityType + "&entityId=" + entityId + "&size=100", adminToken)
                .body();
    }

    protected long notifications(UUID userId, String type) {
        return jdbc.queryForObject(
                "select count(*) from notification where recipient_user_id = ? and type = ?", Long.class, userId, type);
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> listOf(Resp resp) {
        return (List<Map<String, Object>>)
                (List<?>) org.springframework.boot.json.JsonParserFactory.getJsonParser().parseList(resp.body());
    }

    /** The entry of a month response for one date. */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> dayOf(Resp month, LocalDate date) {
        return ((List<Map<String, Object>>) month.map().get("days"))
                .stream().filter(d -> date.toString().equals(d.get("date"))).findFirst().orElseThrow();
    }

    protected void addHoliday(String adminToken, LocalDate date) {
        assertThat(post("/api/v1/attendance/calendar/non-working-dates", adminToken, Map.of("onDate", date.toString(), "description", "Test holiday")).status())
                .isEqualTo(201);
    }

    protected void removeHoliday(String adminToken, LocalDate date) {
        delete("/api/v1/attendance/calendar/non-working-dates/" + date, adminToken);
    }
}
