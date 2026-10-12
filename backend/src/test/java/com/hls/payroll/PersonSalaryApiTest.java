package com.hls.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.PayrollTestBase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Spec 013a User Story 5 (T052): each person's own salary, the People list, staff dates and every refusal. */
class PersonSalaryApiTest extends PayrollTestBase {

    private Resp recordFor(String token, String kind, UUID id, Object amount, Object effectiveOn, String note) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount == null ? null : String.valueOf(amount));
        body.put("effectiveOn", effectiveOn == null ? null : String.valueOf(effectiveOn));
        body.put("note", note);
        return post(BASE + "/people/" + kind + "/" + id + "/salaries", token, body);
    }

    private Resp detail(String token, String kind, UUID id) {
        return get(BASE + "/people/" + kind + "/" + id, token);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> history(Resp detail) {
        return (List<Map<String, Object>>) detail.map().get("history");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> person(Resp detail) {
        return (Map<String, Object>) detail.map().get("person");
    }

    /** The person's row in the list. The database is shared, so the list is searched by the person's own name. */
    private Map<String, Object> rowOf(String token, String query, UUID personId) {
        String kind = query.contains("kind=") ? query.replaceAll(".*kind=([A-Z]+).*", "$1") : null;
        String name = nameOf(token, kind, personId);
        String extra = query.contains("withoutSalary=true") ? "&withoutSalary=true" : "";
        // many test users share a display name, so the person may be on a later page of the matches
        for (int page = 0; ; page++) {
            Resp list = get(
                    BASE + "/people?q=" + name + (kind == null ? "" : "&kind=" + kind) + extra
                            + "&size=200&page=" + page,
                    token);
            assertThat(list.status()).as(list.body()).isEqualTo(200);
            List<Map<String, Object>> rows = content(list);
            for (Map<String, Object> row : rows) {
                if (personId.toString().equals(row.get("personId"))) {
                    return row;
                }
            }
            if (rows.isEmpty() || (long) (page + 1) * 200 >= total(list)) {
                throw new AssertionError("person not in the list: " + personId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String nameOf(String token, String kind, UUID personId) {
        // a person's name, from the detail endpoint when the kind is known, else by trying each kind
        for (String candidate : kind == null ? List.of("MANAGER", "TEACHER", "STAFF") : List.of(kind)) {
            Resp detail = detail(token, candidate, personId);
            if (detail.status() == 200) {
                return (String) ((Map<String, Object>) detail.map().get("person")).get("name");
            }
        }
        throw new AssertionError("no such person: " + personId);
    }

    @Test
    void twoManagersOnOneDesignationKeepDifferentSalaries() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        ManagerCtx first = newManager(admin);
        ManagerCtx second = newManager(admin);
        for (ManagerCtx m : List.of(first, second)) {
            assertThat(designate(admin, m.managerId(), designation, LocalDate.now().minusDays(100)).status()).isEqualTo(200);
        }
        LocalDate from = firstOfMonth();

        Resp one = recordFor(admin, "MANAGER", first.managerId(), "50000", from, "Agreed at recruitment");
        assertThat(one.status()).as(one.body()).isEqualTo(201);
        assertThat(one.map()).containsEntry("amount", "50000.00").containsEntry("note", "Agreed at recruitment");
        assertThat(recordFor(admin, "MANAGER", second.managerId(), "65000", from, null).status()).isEqualTo(201);

        assertThat(rowOf(admin, "kind=MANAGER", first.managerId()).get("inEffect"))
                .isEqualTo(Map.of("amount", "50000.00", "effectiveOn", from.toString()));
        assertThat(rowOf(admin, "kind=MANAGER", second.managerId()).get("inEffect"))
                .isEqualTo(Map.of("amount", "65000.00", "effectiveOn", from.toString()));
        // the designation itself carries no pay
        assertThat(entryOf(admin, designation).get("inEffect")).isNull();
    }

    @Test
    void aRaiseIsANewRowTheEarlierRowIsUnchangedAndTheLastRecordedWinsATie() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        LocalDate from = firstOfMonth();
        recordFor(admin, "MANAGER", manager.managerId(), "40000", from, "first");
        Map<String, Object> firstRowBefore = history(detail(admin, "MANAGER", manager.managerId())).get(0);

        assertThat(recordFor(admin, "MANAGER", manager.managerId(), "48000", from.plusMonths(3), "raise").status())
                .isEqualTo(201);
        assertThat(recordFor(admin, "MANAGER", manager.managerId(), "41000", from, "correction").status()).isEqualTo(201);

        Resp detail = detail(admin, "MANAGER", manager.managerId());
        List<Map<String, Object>> rows = history(detail);
        assertThat(rows).hasSize(3);
        assertThat(rows.stream().map(r -> r.get("amount")).toList()).containsExactly("48000.00", "41000.00", "40000.00");
        assertThat(rows).contains(firstRowBefore);
        // the correction was recorded last on the same date, so it is the one in effect today
        assertThat(person(detail).get("inEffect")).isEqualTo(Map.of("amount", "41000.00", "effectiveOn", from.toString()));
        assertThat(rows.get(0).get("recordedBy")).isInstanceOf(Map.class);
    }

    @Test
    void aStaffMemberGetsASalaryAndJoiningAndExitDates() {
        Signed staff = signInAs(Role.DIRECTOR);
        String admin = signInAs(Role.ADMIN).token();
        LocalDate joining = firstOfMonth().minusMonths(2);

        assertThat(recordFor(admin, "STAFF", staff.userId(), "120000", firstOfMonth(), null).status()).isEqualTo(201);
        Resp dates = put(
                BASE + "/people/STAFF/" + staff.userId() + "/employment",
                admin,
                Map.of("joiningDate", joining.toString()));
        assertThat(dates.status()).as(dates.body()).isEqualTo(200);

        Map<String, Object> row = rowOf(admin, "kind=STAFF", staff.userId());
        assertThat(row).containsEntry("kind", "STAFF").containsEntry("joiningDate", joining.toString());
        assertThat(row.get("roles")).isEqualTo(List.of("DIRECTOR"));
        assertThat(row.get("designation")).isNull();
        assertThat(row.get("inEffect")).isEqualTo(Map.of("amount", "120000.00", "effectiveOn", firstOfMonth().toString()));

        Resp exit = put(
                BASE + "/people/STAFF/" + staff.userId() + "/employment",
                admin,
                Map.of("joiningDate", joining.toString(), "exitDate", firstOfMonth().plusMonths(1).toString()));
        assertThat(exit.status()).isEqualTo(200);
        Resp backwards = put(
                BASE + "/people/STAFF/" + staff.userId() + "/employment",
                admin,
                Map.of("joiningDate", joining.toString(), "exitDate", joining.minusDays(1).toString()));
        assertThat(backwards.status()).isEqualTo(400);
        assertThat(backwards.body()).contains("exit date cannot be before the joining date");
    }

    @Test
    void everyBadSalaryIsRefusedWithTheProblemNamedAndNothingIsStored() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin);
        UUID id = manager.managerId();
        LocalDate first = firstOfMonth();

        assertRefused(recordFor(admin, "MANAGER", id, "0", first, null), "more than zero");
        assertRefused(recordFor(admin, "MANAGER", id, "-1", first, null), "more than zero");
        assertRefused(recordFor(admin, "MANAGER", id, "100.123", first, null), "two decimal places");
        assertRefused(recordFor(admin, "MANAGER", id, "10000000", first, null), "9,999,999.99");
        assertRefused(recordFor(admin, "MANAGER", id, null, first, null), "amount is required");
        assertRefused(recordFor(admin, "MANAGER", id, "100", null, null), "effective date is required");
        assertRefused(recordFor(admin, "MANAGER", id, "100", first.minusDays(1), null), "before the first day");
        assertRefused(recordFor(admin, "MANAGER", id, "100", first, "n".repeat(501)), "500 characters");
        assertThat(history(detail(admin, "MANAGER", id))).isEmpty();
        assertThat(changeCount(admin, "PERSON_SALARY", id)).isZero();

        assertThat(recordFor(admin, "MANAGER", id, "9999999.99", first, "n".repeat(500)).status()).isEqualTo(201);
    }

    @Test
    void aTeachersSalaryIsNotWrittenHereAndUnknownPeopleAreNotFound() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        Resp refused = recordFor(admin, "TEACHER", teacher, "30000", firstOfMonth(), null);
        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.body()).contains("changed on the Teachers screen");

        assertThat(recordFor(admin, "MANAGER", UUID.randomUUID(), "30000", firstOfMonth(), null).status()).isEqualTo(404);
        assertThat(recordFor(admin, "STAFF", UUID.randomUUID(), "30000", firstOfMonth(), null).status()).isEqualTo(404);
        assertThat(detail(admin, "MANAGER", UUID.randomUUID()).status()).isEqualTo(404);
        assertThat(recordFor(admin, "ROBOT", UUID.randomUUID(), "30000", firstOfMonth(), null).status()).isEqualTo(400);
        // a Teacher with no staff role is not staff
        assertThat(recordFor(admin, "STAFF", signInAs(Role.TEACHER).userId(), "30000", firstOfMonth(), null).status())
                .isEqualTo(404);
        // dates are for staff only
        assertThat(put(BASE + "/people/MANAGER/" + UUID.randomUUID() + "/employment", admin, Map.of("joiningDate", "2026-01-01"))
                        .status())
                .isEqualTo(400);
    }

    @Test
    void theListShowsTeachersReadOnlyAndFiltersByKindNameAndMissingSalary() {
        String admin = signInAs(Role.ADMIN).token();
        UUID teacher = teacher(admin);
        Resp salary = post(
                "/api/v1/teachers/" + teacher + "/salary",
                admin,
                Map.of("amount", new BigDecimal("33000"), "effectiveOn", LocalDate.now().minusDays(100).toString()));
        assertThat(salary.status()).isEqualTo(201);
        UUID teacherWithout = teacher(admin);
        ManagerCtx manager = newManager(admin);

        Map<String, Object> row = rowOf(admin, "kind=TEACHER", teacher);
        assertThat(row).containsEntry("salaryEditable", false).containsEntry("kind", "TEACHER");
        assertThat(((Map<?, ?>) row.get("inEffect")).get("amount")).isEqualTo("33000.00");

        // the filter keeps people with nothing recorded, and only those
        assertThat(rowOf(admin, "withoutSalary=true", teacherWithout)).containsEntry("inEffect", null);
        assertThat(rowOf(admin, "withoutSalary=true", manager.managerId())).containsEntry("kind", "MANAGER");
        String nameWith = nameOf(admin, "TEACHER", teacher);
        Resp without = get(BASE + "/people?withoutSalary=true&q=" + nameWith + "&size=200", admin);
        assertThat(content(without).stream().map(r -> r.get("personId"))).doesNotContain(teacher.toString());

        // kind and name
        String name = nameOf(admin, "MANAGER", manager.managerId());
        Resp byName = get(BASE + "/people?kind=MANAGER&q=" + name, admin);
        assertThat(content(byName).stream().map(r -> r.get("personId"))).contains(manager.managerId().toString());
        assertThat(content(byName)).allSatisfy(r -> assertThat(r.get("kind")).isEqualTo("MANAGER"));

        // pages
        Resp page = get(BASE + "/people?size=1&page=0", admin);
        assertThat(content(page)).hasSize(1);
        assertThat(total(page)).isGreaterThan(1);
    }

    @Test
    void theDesignationDefaultIsOfferedAsASuggestionAndNeverSavedAsTheSalary() {
        String admin = signInAs(Role.ADMIN).token();
        UUID designation = designation(admin, "MANAGER");
        recordSalaryOk(admin, designation, "52000", firstOfMonth());
        ManagerCtx manager = newManager(admin);
        assertThat(designate(admin, manager.managerId(), designation, LocalDate.now().minusDays(100)).status()).isEqualTo(200);

        Resp detail = detail(admin, "MANAGER", manager.managerId());
        assertThat(detail.map().get("suggestedDefault"))
                .isEqualTo(Map.of("amount", "52000.00", "effectiveOn", firstOfMonth().toString()));
        assertThat(person(detail).get("inEffect")).isNull();
        assertThat(history(detail)).isEmpty();
    }

    @Test
    void everyRecordedSalaryAndEveryChangedDateIsAudited() {
        Signed actor = signInAs(Role.ADMIN);
        String admin = actor.token();
        ManagerCtx manager = newManager(admin);
        recordFor(admin, "MANAGER", manager.managerId(), "40000", firstOfMonth(), null);
        recordFor(admin, "MANAGER", manager.managerId(), "45000", firstOfMonth().plusMonths(1), null);

        List<Map<String, Object>> entries = changeEntries(admin, "PERSON_SALARY", manager.managerId(), 2);
        assertThat(entries).allSatisfy(e -> {
            assertThat(e).containsEntry("field", "amount").containsEntry("actorUserId", actor.userId().toString());
        });
        assertThat(entries.stream().map(e -> e.get("beforeValue") + " -> " + e.get("afterValue")).toList())
                .containsExactlyInAnyOrder("null -> 40000.00", "40000.00 -> 45000.00");

        Signed staff = signInAs(Role.ADMIN);
        put(BASE + "/people/STAFF/" + staff.userId() + "/employment", admin, Map.of("joiningDate", "2026-01-05"));
        List<Map<String, Object>> dateEntries = changeEntries(admin, "STAFF_EMPLOYMENT", staff.userId(), 1);
        assertThat(dateEntries.get(0)).containsEntry("field", "joiningDate").containsEntry("afterValue", "2026-01-05");
    }

    private void assertRefused(Resp resp, String problem) {
        assertThat(resp.status()).as(resp.body()).isEqualTo(400);
        assertThat(resp.body()).contains(problem);
    }
}
