package com.hls.attendance.workingdays;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.support.WorkingDaysTestBase;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 038: the year entry endpoints, GET and PUT /api/v1/school-working-days/year. */
class YearEntryApiTest extends WorkingDaysTestBase {

    @Autowired
    private PermissionMatrixService matrix;

    @Autowired
    private SessionFactory sessionFactory;

    private Resp getYear(String token, UUID school, int year) {
        return get(BASE + "/year?schoolId=" + school + "&year=" + year, token);
    }

    private static Map<String, Object> month(int month, Object days, Long version) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", month);
        m.put("workingDays", days);
        m.put("version", version);
        return m;
    }

    private Resp saveYear(String token, UUID school, int year, Map<String, Object>... months) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schoolId", school);
        body.put("year", year);
        body.put("months", List.of(months));
        return put(BASE + "/year", token, body);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> months(Resp resp) {
        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        return (List<Map<String, Object>>) resp.map().get("months");
    }

    private static Map<String, Object> of(Resp resp, int month) {
        return months(resp).stream()
                .filter(m -> ((Number) m.get("month")).intValue() == month)
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> current(Map<String, Object> result) {
        return (Map<String, Object>) result.get("current");
    }

    private static long versionOfMonth(Map<String, Object> state) {
        return ((Number) state.get("version")).longValue();
    }

    private long rowCount(UUID school, int year) {
        return jdbc.queryForObject(
                "select count(*) from school_working_days where school_id = ? and year = ?", Long.class, school, year);
    }

    private long auditCountForSchool(UUID school, int year) {
        return jdbc.queryForObject(
                "select count(*) from change_history_entry c join school_working_days r on c.entity_id = r.id::text"
                        + " where c.entity_type = 'SCHOOL_WORKING_DAYS' and r.school_id = ? and r.year = ?",
                Long.class,
                school,
                year);
    }

    // ---- US1: load and save a year ----

    @Test
    void theYearHasTwelveMonthsWithDaysAndHintsAndNoStoredFigures() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;

        Resp resp = getYear(admin, school, year);
        List<Map<String, Object>> months = months(resp);

        assertThat(months).hasSize(12);
        for (int i = 0; i < 12; i++) {
            Map<String, Object> m = months.get(i);
            assertThat(((Number) m.get("month")).intValue()).isEqualTo(i + 1);
            assertThat(((Number) m.get("daysInMonth")).intValue()).isEqualTo(YearMonth.of(year, i + 1).lengthOfMonth());
            assertThat(m.get("calendarDerived")).isNotNull();
            assertThat(m.get("id")).isNull();
            assertThat(m.get("workingDays")).isNull();
            assertThat(m.get("version")).isNull();
        }
        assertThat(resp.map().get("schoolName")).isNotNull();
    }

    @Test
    void theHintEqualsTheGridsCalendarDerivedCountAndAStoredFigureIsShown() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        createRowOk(admin, school, year, 3, 25.5);

        Map<String, Object> march = of(getYear(admin, school, year), 3);
        Map<String, Object> gridRow = items(listRows(admin, year, school)).get(0);

        assertThat(march.get("calendarDerived")).isEqualTo(gridRow.get("calendarDerived"));
        assertThat(((Number) march.get("workingDays")).doubleValue()).isEqualTo(25.5);
        assertThat(march.get("id")).isEqualTo(gridRow.get("id"));
        assertThat(march.get("updatedBy")).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSaveCreatesOnlyTheSentMonthsAndTheyAppearInTheGridAndTheHistory() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;

        Resp saved = saveYear(admin, school, year, month(1, 26, null), month(2, 24.5, null), month(12, 0, null));

        assertThat(saved.map().get("created")).isEqualTo(3);
        assertThat(months(saved)).extracting(m -> m.get("status")).containsOnly("CREATED");
        assertThat(rowCount(school, year)).isEqualTo(3);
        assertThat(items(listRows(admin, year, school))).hasSize(3);
        UUID januaryId = UUID.fromString((String) current(of(saved, 1)).get("id"));
        assertThat(auditCount(januaryId)).isEqualTo(1);
        assertThat(historyCount(school)).isEqualTo(3);
        Resp history = get(BASE + "/" + januaryId + "/history", admin);
        assertThat(history.status()).isEqualTo(200);
        assertThat(history.body()).contains("CREATE");
        // a zero is a figure, not a blank
        assertThat(((Number) current(of(saved, 12)).get("workingDays")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    void anUpdateChangesOnlyTheSentMonthsAndTheirVersions() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        Resp first = saveYear(admin, school, year, month(1, 26, null), month(2, 24, null), month(3, 27, null));
        long v1 = versionOfMonth(current(of(first, 1)));
        long v2 = versionOfMonth(current(of(first, 2)));
        long v3 = versionOfMonth(current(of(first, 3)));
        long historyBefore = historyCount(school);

        Resp second = saveYear(admin, school, year, month(1, 25.5, v1), month(3, 27, v3));

        assertThat(of(second, 1).get("status")).isEqualTo("UPDATED");
        assertThat(of(second, 3).get("status")).isEqualTo("UNCHANGED");
        assertThat(second.map()).containsEntry("updated", 1).containsEntry("unchanged", 1);
        Map<String, Object> view1 = of(getYear(admin, school, year), 1);
        Map<String, Object> view2 = of(getYear(admin, school, year), 2);
        Map<String, Object> view3 = of(getYear(admin, school, year), 3);
        assertThat(versionOfMonth(view1)).isEqualTo(v1 + 1);
        assertThat(versionOfMonth(view2)).isEqualTo(v2);
        assertThat(versionOfMonth(view3)).isEqualTo(v3);
        assertThat(historyCount(school)).isEqualTo(historyBefore + 1);
    }

    @Test
    void anUnchangedMonthWritesNoHistoryAndNoAuditEntry() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        Resp first = saveYear(admin, school, year, month(4, 22, null));
        long version = versionOfMonth(current(of(first, 4)));
        long history = historyCount(school);
        long audit = auditCountForSchool(school, year);

        Resp again = saveYear(admin, school, year, month(4, 22.0, version));

        assertThat(of(again, 4).get("status")).isEqualTo("UNCHANGED");
        assertThat(historyCount(school)).isEqualTo(history);
        assertThat(auditCountForSchool(school, year)).isEqualTo(audit);
        assertThat(versionOfMonth(of(getYear(admin, school, year), 4))).isEqualTo(version);
    }

    @Test
    void aMonthNotSentIsNeverCreatedOrDeleted() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        saveYear(admin, school, year, month(1, 26, null), month(2, 24, null));

        saveYear(admin, school, year, month(1, 25, ((Number) of(getYear(admin, school, year), 1).get("version")).longValue()));

        assertThat(rowCount(school, year)).isEqualTo(2);
    }

    @Test
    void theRequestItselfIsRefusedWhenItIsMalformed() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;

        assertThat(saveYear(admin, UUID.randomUUID(), year, month(1, 26, null)).status()).isEqualTo(400);
        assertThat(saveYear(admin, school, thisYear() + 6, month(1, 26, null)).status()).isEqualTo(400);
        assertThat(saveYear(admin, school, thisYear() - 6, month(1, 26, null)).status()).isEqualTo(400);
        assertThat(saveYear(admin, school, year, month(13, 26, null)).status()).isEqualTo(400);
        assertThat(saveYear(admin, school, year, month(0, 26, null)).status()).isEqualTo(400);
        assertThat(saveYear(admin, school, year, month(1, 26, null), month(1, 25, null)).status()).isEqualTo(400);
        assertThat(saveYear(admin, school, year).status()).isEqualTo(400);
        List<Map<String, Object>> thirteen = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            thirteen.add(month(i % 12 + 1, 20, null));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schoolId", school);
        body.put("year", year);
        body.put("months", thirteen);
        assertThat(put(BASE + "/year", admin, body).status()).isEqualTo(400);
        Map<String, Object> noSchool = new LinkedHashMap<>();
        noSchool.put("year", year);
        noSchool.put("months", List.of(month(1, 20, null)));
        assertThat(put(BASE + "/year", admin, noSchool).status()).isEqualTo(400);
        assertThat(get(BASE + "/year?year=" + year, admin).status()).isEqualTo(400);
        assertThat(getYear(admin, school, thisYear() + 6).status()).isEqualTo(400);
        assertThat(rowCount(school, year)).isZero();
    }

    @Test
    void theNumberOfStatementsForTheYearDoesNotGrowWithTheStoredMonths() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int emptyYear = thisYear() + 1; // one stored month; a year with none skips the user-name lookup
        int fullYear = thisYear() + 2;
        assertThat(saveYear(admin, school, emptyYear, month(1, 20, null)).status()).isEqualTo(200);
        List<Map<String, Object>> all = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            all.add(month(m, 20, null));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schoolId", school);
        body.put("year", fullYear);
        body.put("months", all);
        assertThat(put(BASE + "/year", admin, body).status()).isEqualTo(200);

        getYear(admin, school, emptyYear); // warm caches
        long few = fewestStatements(sessionFactory, () -> getYear(admin, school, emptyYear));
        long many = fewestStatements(sessionFactory, () -> getYear(admin, school, fullYear));

        assertThat(many).isEqualTo(few);
    }

    // ---- US2: partial save and conflicts ----

    @Test
    void badFiguresAreRefusedByMonthWhileTheValidMonthsAreStored() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1; // never a leap year at the same time as thisYear()+... is checked below

        Resp saved = saveYear(
                admin,
                school,
                year,
                month(1, 26, null),
                month(4, 31, null), // April has 30 days
                month(5, 25.3, null), // not a half day
                month(6, -1, null),
                month(7, 24, null));

        assertThat(of(saved, 1).get("status")).isEqualTo("CREATED");
        assertThat(of(saved, 7).get("status")).isEqualTo("CREATED");
        for (int bad : new int[] {4, 5, 6}) {
            assertThat(of(saved, bad).get("status")).isEqualTo("NOT_SAVED");
            assertThat(of(saved, bad).get("reason")).isEqualTo("INVALID_FIGURE");
            assertThat(of(saved, bad).get("message").toString()).contains("0 to").contains("half days");
        }
        assertThat(saved.map()).containsEntry("created", 2).containsEntry("notSaved", 3);
        assertThat(rowCount(school, year)).isEqualTo(2);
    }

    @Test
    void aLeapFebruaryAllows29AndOtherYearsDoNot() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        for (int year = thisYear() - 4; year <= thisYear() + 4; year++) {
            Resp saved = saveYear(admin, school, year, month(2, 29, null));
            boolean leap = YearMonth.of(year, 2).isLeapYear();
            assertThat(of(saved, 2).get("status")).as("year " + year).isEqualTo(leap ? "CREATED" : "NOT_SAVED");
        }
    }

    @Test
    void aMonthChangedBySomeoneElseIsStaleAndNothingIsOverwritten() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        Resp first = saveYear(admin, school, year, month(1, 26, null), month(2, 24, null));
        long v1 = versionOfMonth(current(of(first, 1)));
        long v2 = versionOfMonth(current(of(first, 2)));

        // someone else changes January after the screen was loaded
        assertThat(saveYear(director, school, year, month(1, 27, v1)).status()).isEqualTo(200);

        Resp mine = saveYear(admin, school, year, month(1, 25, v1), month(2, 23, v2));

        assertThat(of(mine, 1).get("status")).isEqualTo("NOT_SAVED");
        assertThat(of(mine, 1).get("reason")).isEqualTo("STALE");
        assertThat(((Number) current(of(mine, 1)).get("workingDays")).doubleValue()).isEqualTo(27.0);
        assertThat(current(of(mine, 1)).get("updatedBy")).isNotNull();
        assertThat(of(mine, 2).get("status")).isEqualTo("UPDATED");
        assertThat(((Number) of(getYear(admin, school, year), 1).get("workingDays")).doubleValue()).isEqualTo(27.0);
    }

    @Test
    void aMonthCreatedMeanwhileWithADifferentFigureAlreadyExists() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        saveYear(director, school, year, month(5, 22, null));

        Resp mine = saveYear(admin, school, year, month(5, 21, null), month(6, 20, null));

        assertThat(of(mine, 5).get("reason")).isEqualTo("ALREADY_EXISTS");
        assertThat(((Number) current(of(mine, 5)).get("workingDays")).doubleValue()).isEqualTo(22.0);
        assertThat(of(mine, 6).get("status")).isEqualTo("CREATED");
    }

    @Test
    void aMonthDeletedMeanwhileIsReportedAndNeverRecreated() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        Resp first = saveYear(admin, school, year, month(1, 26, null), month(2, 24, null));
        UUID januaryId = UUID.fromString((String) current(of(first, 1)).get("id"));
        long v1 = versionOfMonth(current(of(first, 1)));
        long v2 = versionOfMonth(current(of(first, 2)));
        assertThat(deleteRow(admin, januaryId, v1).status()).isEqualTo(204);
        long audit = auditCountForSchool(school, year);

        Resp mine = saveYear(admin, school, year, month(1, 25, v1), month(2, 23, v2));

        assertThat(of(mine, 1).get("reason")).isEqualTo("ROW_DELETED");
        assertThat(current(of(mine, 1)).get("id")).isNull();
        assertThat(of(mine, 2).get("status")).isEqualTo("UPDATED");
        assertThat(rowCount(school, year)).isEqualTo(1);
        assertThat(auditCountForSchool(school, year)).isEqualTo(audit + 1); // only February's change
    }

    // ---- clarified idempotency (2026-10-16) ----

    @Test
    void theSameSaveSentTwiceWritesNothingTheSecondTime() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;

        saveYear(admin, school, year, month(1, 26, null), month(2, 24, null));
        long history = historyCount(school);
        long audit = auditCountForSchool(school, year);
        long rows = rowCount(school, year);

        Resp again = saveYear(admin, school, year, month(1, 26, null), month(2, 24, null));

        assertThat(months(again)).extracting(m -> m.get("status")).containsOnly("UNCHANGED");
        assertThat(again.map()).containsEntry("unchanged", 2).containsEntry("created", 0).containsEntry("notSaved", 0);
        assertThat(historyCount(school)).isEqualTo(history);
        assertThat(auditCountForSchool(school, year)).isEqualTo(audit);
        assertThat(rowCount(school, year)).isEqualTo(rows);
    }

    @Test
    void aMonthAlreadyStoredWithTheTypedFigureIsUnchangedNotAConflict() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        saveYear(director, school, year, month(5, 22, null));

        Resp mine = saveYear(admin, school, year, month(5, 22, null));

        assertThat(of(mine, 5).get("status")).isEqualTo("UNCHANGED");
        assertThat(mine.map()).containsEntry("notSaved", 0);
    }

    @Test
    void anUpdateWhoseFirstAttemptAlreadySucceededIsUnchanged() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        Resp first = saveYear(admin, school, year, month(1, 26, null));
        long v = versionOfMonth(current(of(first, 1)));
        assertThat(of(saveYear(admin, school, year, month(1, 25, v)), 1).get("status")).isEqualTo("UPDATED");

        // the first attempt's answer was lost; the screen retries with the old version
        Resp retry = saveYear(admin, school, year, month(1, 25, v));

        assertThat(of(retry, 1).get("status")).isEqualTo("UNCHANGED");
        assertThat(versionOfMonth(of(getYear(admin, school, year), 1))).isEqualTo(v + 1);
    }

    @Test
    void twoSimultaneousSavesOfTheSameNewMonthMakeOneRowAndNoServerError() throws Exception {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> saveYear(admin, school, year, month(8, 21, null)));
            var b = pool.submit(() -> saveYear(admin, school, year, month(8, 21, null)));
            Resp ra = a.get();
            Resp rb = b.get();
            assertThat(ra.status()).isEqualTo(200);
            assertThat(rb.status()).isEqualTo(200);
            assertThat(List.of((String) of(ra, 8).get("status"), (String) of(rb, 8).get("status")))
                    .containsExactlyInAnyOrder("CREATED", "UNCHANGED");
        } finally {
            pool.shutdownNow();
        }
        assertThat(rowCount(school, year)).isEqualTo(1);
    }

    // ---- US3: permissions ----

    @Test
    void managerTeacherAndSystemAreRefusedBothEndpoints() {
        String admin = signInAs(Role.ADMIN).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        for (Role role : List.of(Role.MANAGER, Role.TEACHER, Role.SYSTEM)) {
            String token = signInAs(role).token();
            assertThat(getYear(token, school, year).status()).as(role + " GET").isEqualTo(403);
            assertThat(saveYear(token, school, year, month(1, 26, null)).status()).as(role + " PUT").isEqualTo(403);
        }
        assertThat(rowCount(school, year)).isZero();
    }

    @Test
    void aDirectorMayReadAndSaveByDefault() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;

        assertThat(getYear(director, school, year).status()).isEqualTo(200);
        Resp saved = saveYear(director, school, year, month(1, 26, null));
        assertThat(of(saved, 1).get("status")).isEqualTo("CREATED");
    }

    @Test
    void aManagerWithViewOnlyMayReadButNotSave() {
        World w = newWorld();
        int year = thisYear() + 1;
        UUID actor = UUID.randomUUID();
        matrix.updateGrant(Role.MANAGER, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW, true, actor);
        try {
            String manager = w.managerA().token();
            assertThat(getYear(manager, w.schoolA(), year).status()).isEqualTo(200);
            assertThat(saveYear(manager, w.schoolA(), year, month(1, 26, null)).status()).isEqualTo(403);
            // a School outside the Manager's assignment is indistinguishable from a missing one
            assertThat(getYear(manager, w.schoolB(), year).status()).isEqualTo(404);
            assertThat(rowCount(w.schoolA(), year)).isZero();
        } finally {
            matrix.updateGrant(Role.MANAGER, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.VIEW, false, actor);
        }
    }

    @Test
    void createWithoutEditSavesOnlyNewMonthsAndEditWithoutCreateOnlyStoredMonths() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        Resp first = saveYear(admin, school, year, month(1, 26, null));
        long v1 = versionOfMonth(current(of(first, 1)));
        UUID actor = UUID.randomUUID();

        matrix.updateGrant(Role.DIRECTOR, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.EDIT, false, actor);
        try {
            Resp createOnly = saveYear(director, school, year, month(1, 25, v1), month(2, 24, null));
            assertThat(of(createOnly, 1).get("reason")).isEqualTo("NOT_PERMITTED");
            assertThat(of(createOnly, 2).get("status")).isEqualTo("CREATED");
        } finally {
            matrix.updateGrant(Role.DIRECTOR, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.EDIT, true, actor);
        }
        assertThat(((Number) of(getYear(admin, school, year), 1).get("workingDays")).doubleValue()).isEqualTo(26.0);

        matrix.updateGrant(Role.DIRECTOR, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.CREATE, false, actor);
        try {
            Resp editOnly = saveYear(director, school, year, month(1, 25, v1), month(3, 22, null));
            assertThat(of(editOnly, 1).get("status")).isEqualTo("UPDATED");
            assertThat(of(editOnly, 3).get("reason")).isEqualTo("NOT_PERMITTED");
        } finally {
            matrix.updateGrant(Role.DIRECTOR, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.CREATE, true, actor);
        }
        assertThat(rowCount(school, year)).isEqualTo(2);
    }

    @Test
    void aCallerWithNeitherCreateNorEditIsRefusedTheSaveButMayRead() {
        String admin = signInAs(Role.ADMIN).token();
        String director = signInAs(Role.DIRECTOR).token();
        UUID school = schoolInNewZone(admin)[2];
        int year = thisYear() + 1;
        UUID actor = UUID.randomUUID();
        matrix.updateGrant(Role.DIRECTOR, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.CREATE, false, actor);
        matrix.updateGrant(Role.DIRECTOR, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.EDIT, false, actor);
        try {
            assertThat(getYear(director, school, year).status()).isEqualTo(200);
            assertThat(saveYear(director, school, year, month(1, 26, null)).status()).isEqualTo(403);
        } finally {
            matrix.updateGrant(Role.DIRECTOR, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.CREATE, true, actor);
            matrix.updateGrant(Role.DIRECTOR, PermissionModule.SCHOOL_WORKING_DAYS, PermissionAction.EDIT, true, actor);
        }
    }
}
