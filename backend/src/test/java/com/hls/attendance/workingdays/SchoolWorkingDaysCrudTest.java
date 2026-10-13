package com.hls.attendance.workingdays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.hls.identity.user.Role;
import com.hls.support.WorkingDaysTestBase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/** Spec 037 US1: create, edit and delete a School's monthly figure, with its rules, locking and audit. */
class SchoolWorkingDaysCrudTest extends WorkingDaysTestBase {

    private UUID newSchool(String admin) {
        return schoolInNewZone(admin)[2];
    }

    @Test
    void anAdminCreatesReadsEditsAndDeletesAFigure() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = newSchool(admin);
        int year = thisYear();

        Resp created = createRow(admin, school, year, 3, 26);
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        assertThat(created.map()).containsEntry("year", year).containsEntry("month", 3).containsEntry("daysInMonth", 31);
        assertThat(((Number) created.map().get("workingDays")).doubleValue()).isEqualTo(26.0);
        assertThat(created.map()).containsKey("calendarDerived").containsKey("schoolName");
        UUID id = created.id();

        List<Map<String, Object>> rows = items(listRows(admin, year, school));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("id", id.toString());

        Resp edited = editRow(admin, id, 25.5, versionOf(created));
        assertThat(edited.status()).as(edited.body()).isEqualTo(200);
        assertThat(((Number) edited.map().get("workingDays")).doubleValue()).isEqualTo(25.5);
        assertThat(versionOf(edited)).isGreaterThan(versionOf(created));

        Resp deleted = deleteRow(admin, id, versionOf(edited));
        assertThat(deleted.status()).isEqualTo(204);
        assertThat(items(listRows(admin, year, school))).isEmpty();
        assertThat(historyCount(school)).isEqualTo(3);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(auditCount(id)).isEqualTo(3));
    }

    @Test
    void aSecondFigureForTheSameSchoolAndMonthIsRefusedNamingTheExistingRow() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = newSchool(admin);
        int year = thisYear();
        UUID first = createRowOk(admin, school, year, 3, 26);

        Resp again = createRow(admin, school, year, 3, 20);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.map()).containsEntry("code", "DUPLICATE");
        @SuppressWarnings("unchecked")
        Map<String, Object> existing = (Map<String, Object>) again.map().get("existing");
        assertThat(existing).containsEntry("id", first.toString());
        assertThat(historyCount(school)).isEqualTo(1);
    }

    @Test
    void theFigureMustBeZeroToTheDaysInTheMonthInHalfDays() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = newSchool(admin);
        int year = thisYear();

        Resp tooMany = createRow(admin, school, year, 4, 31);
        assertThat(tooMany.status()).isEqualTo(400);
        assertThat(tooMany.body()).contains("0 to 30, in half days");
        assertThat(createRow(admin, school, year, 4, -1).status()).isEqualTo(400);
        assertThat(createRow(admin, school, year, 4, 25.3).status()).isEqualTo(400);
        assertThat(createRow(admin, school, year, 13, 10).status()).isEqualTo(400);
        assertThat(historyCount(school)).isZero();

        assertThat(createRow(admin, school, year, 4, 0).status()).isEqualTo(201);
        assertThat(createRow(admin, school, year, 6, 30).status()).isEqualTo(201);
    }

    @Test
    void aNewFigureMustBeWithinFiveYearsOfToday() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = newSchool(admin);
        int year = thisYear();

        Resp past = createRow(admin, school, year - 6, 3, 20);
        assertThat(past.status()).isEqualTo(400);
        assertThat(past.body()).contains("Year must be " + (year - 5) + " to " + (year + 5));
        assertThat(createRow(admin, school, year + 6, 3, 20).status()).isEqualTo(400);
        assertThat(createRow(admin, school, year - 5, 3, 20).status()).isEqualTo(201);
        assertThat(createRow(admin, school, year + 5, 3, 20).status()).isEqualTo(201);
    }

    @Test
    void anUnknownSchoolIsRefusedButADeactivatedOneKeepsItsRows() {
        String admin = signInAs(Role.ADMIN).token();
        assertThat(createRow(admin, UUID.randomUUID(), thisYear(), 3, 20).status()).isEqualTo(400);

        UUID school = newSchool(admin);
        UUID id = createRowOk(admin, school, thisYear(), 3, 26);
        assertThat(post("/api/v1/schools/" + school + "/deactivate", admin, Map.of()).status())
                .isEqualTo(204);

        List<Map<String, Object>> rows = items(listRows(admin, thisYear(), school));
        assertThat(rows).hasSize(1);
        long version = ((Number) rows.get(0).get("version")).longValue();
        assertThat(editRow(admin, id, 24, version).status()).isEqualTo(200);
        assertThat(createRow(admin, school, thisYear(), 5, 20).status()).isEqualTo(201);
        // a School cannot be deleted, only deactivated, so its rows can never be orphaned (FR-005)
        assertThat(delete("/api/v1/schools/" + school, admin).status()).isIn(404, 405);
    }

    @Test
    void anEditWithAnOldVersionIsToldTheRowChangedAndShowsTheCurrentFigure() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = newSchool(admin);
        Resp created = createRow(admin, school, thisYear(), 3, 26);
        long v0 = versionOf(created);
        assertThat(editRow(admin, created.id(), 25, v0).status()).isEqualTo(200);
        long historyBefore = historyCount(school);

        Resp stale = editRow(admin, created.id(), 24, v0);
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.map()).containsEntry("code", "STALE");
        @SuppressWarnings("unchecked")
        Map<String, Object> current = (Map<String, Object>) stale.map().get("current");
        assertThat(((Number) current.get("workingDays")).doubleValue()).isEqualTo(25.0);
        assertThat(deleteRow(admin, created.id(), v0).status()).isEqualTo(409);
        assertThat(historyCount(school)).isEqualTo(historyBefore);
    }

    @Test
    void editingOrDeletingARowAnotherPersonDeletedIsRefusedAndNeverRecreatesIt() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = newSchool(admin);
        Resp created = createRow(admin, school, thisYear(), 3, 26);
        long version = versionOf(created);
        assertThat(deleteRow(admin, created.id(), version).status()).isEqualTo(204);
        long historyBefore = historyCount(school);

        Resp edit = editRow(admin, created.id(), 25, version);
        assertThat(edit.status()).isEqualTo(404);
        assertThat(edit.map()).containsEntry("code", "ROW_DELETED");
        Resp again = deleteRow(admin, created.id(), version);
        assertThat(again.status()).isEqualTo(404);
        assertThat(again.map()).containsEntry("code", "ROW_DELETED");
        assertThat(items(listRows(admin, thisYear(), school))).isEmpty();
        assertThat(historyCount(school)).isEqualTo(historyBefore);
    }

    @Test
    void anEditThatSavesTheSameFigureChangesNothing() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = newSchool(admin);
        Resp created = createRow(admin, school, thisYear(), 3, 26);
        long version = versionOf(created);

        Resp same = editRow(admin, created.id(), 26, version);
        assertThat(same.status()).as(same.body()).isEqualTo(200);
        assertThat(versionOf(same)).isEqualTo(version);
        assertThat(historyCount(school)).isEqualTo(1);
    }

    @Test
    void twoCreatesAtTheSameMomentLeaveExactlyOneRowAndOneRefusal() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = newSchool(admin);
        int year = thisYear();

        CompletableFuture<Resp> a = CompletableFuture.supplyAsync(() -> createRow(admin, school, year, 8, 20));
        CompletableFuture<Resp> b = CompletableFuture.supplyAsync(() -> createRow(admin, school, year, 8, 21));
        List<Integer> statuses = List.of(a.join().status(), b.join().status());
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(items(listRows(admin, year, school))).hasSize(1);
        assertThat(historyCount(school)).isEqualTo(1);
    }
}
