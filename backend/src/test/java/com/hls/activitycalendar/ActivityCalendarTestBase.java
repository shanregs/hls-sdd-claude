package com.hls.activitycalendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.recruitment.marketing.MarketingTestBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helpers for the Activity Calendar tests. The tests build their data in future dates through the owning screens' APIs,
 * then move the dates into a fixed past month with SQL, so each test owns a month no other test touches.
 */
public abstract class ActivityCalendarTestBase extends MarketingTestBase {

    protected static final String CAL = "/api/v1/activity-calendar";

    protected UUID college(String adminToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", uniqueName("Calendar College"));
        body.put("city", "Chennai");
        Resp resp = post("/api/v1/recruitment/colleges", adminToken, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected UUID drive(String token, UUID college, List<UUID> interviewers, LocalDate... dates) {
        Map<String, Object> body = new HashMap<>();
        body.put("collegeId", college);
        body.put("dates", List.of(dates).stream().map(LocalDate::toString).toList());
        body.put("interviewerUserIds", interviewers);
        Resp resp = post("/api/v1/recruitment/drives", token, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return resp.id();
    }

    protected UUID task(String adminToken, UUID assignee, LocalDate due) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "SCHOOL_VISIT");
        body.put("title", "Visit " + uniqueName("School"));
        body.put("assigneeUserId", assignee);
        body.put("dueOn", due.toString());
        Resp resp = post("/api/v1/tasks", adminToken, body);
        assertThat(resp.status()).as(resp.body()).isEqualTo(201);
        return UUID.fromString((String) ((Map<?, ?>) resp.map().get("task")).get("id"));
    }

    protected Resp calendar(String token, String month) {
        return get(CAL + "?month=" + month, token);
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> items(Resp resp) {
        return (List<Map<String, Object>>) resp.map().get("items");
    }

    protected static Map<String, Object> item(Resp resp, UUID activityId) {
        return items(resp).stream()
                .filter(i -> activityId.toString().equals(i.get("activityId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Item " + activityId + " is not in " + resp.body()));
    }

    protected static List<Map<String, Object>> itemsOf(Resp resp, UUID activityId) {
        return items(resp).stream().filter(i -> activityId.toString().equals(i.get("activityId"))).toList();
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> summary(Resp resp) {
        return (Map<String, Object>) resp.map().get("summary");
    }

    protected void moveVisitTo(UUID visit, LocalDate date) {
        jdbc.update("update marketing_activity set activity_date = ? where id = ?", date, visit);
    }

    protected void moveTaskTo(UUID task, LocalDate due) {
        jdbc.update("update task set due_on = ? where id = ?", due, task);
    }

    protected void taskCompletedAt(UUID task, Instant at) {
        jdbc.update("update task set completed_at = ? where id = ?", Timestamp.from(at), task);
    }
}
