package com.hls.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.AttendanceTestBase;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Constitution Principle IX: every attendance endpoint against every role and an anonymous caller. An
 * allowed role must get past the permission check (anything but 401 or 403, whatever the business
 * outcome); every other role must get exactly 403; an anonymous caller must get 401. A route that
 * exists but is missing from this table fails the test, so the sweep stays complete.
 */
class AttendanceAuthorizationMatrixTest extends AttendanceTestBase {

    private static final String A = "/api/v1/attendance";
    private static final Set<Role> ALL = EnumSet.allOf(Role.class);
    private static final Set<Role> ADMIN_DIRECTOR = EnumSet.of(Role.ADMIN, Role.DIRECTOR);
    private static final Set<Role> SUPERVISORS = EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER);

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    private record Endpoint(String name, HttpMethod method, String path, Supplier<Object> body, Set<Role> allowed) {}

    private static Endpoint ep(String name, HttpMethod method, String path, Supplier<Object> body, Set<Role> allowed) {
        return new Endpoint(name, method, path, body, allowed);
    }

    private static Endpoint ep(String name, HttpMethod method, String path, Set<Role> allowed) {
        return new Endpoint(name, method, path, () -> null, allowed);
    }

    private List<Endpoint> endpoints(World w) {
        String teacher = w.teacherA().teacherId().toString();
        String past = YearMonth.from(today()).minusMonths(1).atDay(10).toString();
        String month = YearMonth.from(today()).minusMonths(1).toString();
        String ancient = "2001-01";
        String ghost = UUID.randomUUID().toString();
        Supplier<Object> newCode = () -> Map.of(
                "shortCode", "Z" + ThreadLocalRandom.current().nextInt(100000, 999999),
                "name", "Matrix",
                "category", "LEAVE",
                "weight", 0);
        Supplier<Object> mark = () -> Map.of("statusCode", "P", "dayValue", 1);
        List<Endpoint> list = new ArrayList<>();
        // Status codes and the calendar.
        list.add(ep("list codes", HttpMethod.GET, A + "/status-codes", EnumSet.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER)));
        list.add(ep("create code", HttpMethod.POST, A + "/status-codes", newCode, ADMIN_DIRECTOR));
        list.add(ep("update code", HttpMethod.PUT, A + "/status-codes/" + ghost,
                () -> Map.of("name", "x", "weight", 0, "active", true, "version", 0), ADMIN_DIRECTOR));
        list.add(ep("read calendar", HttpMethod.GET, A + "/calendar", ALL));
        list.add(ep("default weekly off", HttpMethod.PUT, A + "/calendar/default",
                () -> Map.of("weeklyOff", List.of("SUN"), "version", -1), ADMIN_DIRECTOR));
        list.add(ep("put school override", HttpMethod.PUT, A + "/calendar/schools/" + ghost,
                () -> Map.of("weeklyOff", List.of("SUN")), ADMIN_DIRECTOR));
        list.add(ep("delete school override", HttpMethod.DELETE, A + "/calendar/schools/" + ghost, ADMIN_DIRECTOR));
        list.add(ep("add holiday", HttpMethod.POST, A + "/calendar/non-working-dates",
                () -> Map.of("onDate", "2001-02-03", "description", "Matrix"), ADMIN_DIRECTOR));
        list.add(ep("delete holiday", HttpMethod.DELETE, A + "/calendar/non-working-dates/2001-02-04", ADMIN_DIRECTOR));
        // Teacher self-service.
        list.add(ep("my month", HttpMethod.GET, A + "/me?month=" + month, EnumSet.of(Role.TEACHER)));
        list.add(ep("my mark", HttpMethod.PUT, A + "/me/marks/" + today(), mark, EnumSet.of(Role.TEACHER)));
        // Supervisors.
        list.add(ep("teacher month", HttpMethod.GET, A + "/teachers/" + teacher + "?month=" + month, SUPERVISORS));
        list.add(ep("supervisor mark", HttpMethod.PUT, A + "/teachers/" + teacher + "/marks/" + past, mark, SUPERVISORS));
        list.add(ep("mark history", HttpMethod.GET, A + "/teachers/" + teacher + "/marks/" + past + "/history", SUPERVISORS));
        // Admin may clear (ATTENDANCE.DELETE) and a Manager may (TEACHER_ATTENDANCE.EDIT); a Director corrects by marking over.
        list.add(ep("clear mark", HttpMethod.DELETE, A + "/teachers/" + teacher + "/marks/" + past, EnumSet.of(Role.ADMIN, Role.MANAGER)));
        list.add(ep("manager grid", HttpMethod.GET, A + "/teacher-grid?month=" + month, EnumSet.of(Role.MANAGER)));
        // Admin and Director.
        list.add(ep("org grid", HttpMethod.GET, A + "/grid?month=" + month, ADMIN_DIRECTOR));
        list.add(ep("lock month", HttpMethod.POST, A + "/months/" + ancient + "/lock", ADMIN_DIRECTOR));
        list.add(ep("reopen", HttpMethod.POST, A + "/teachers/" + teacher + "/months/" + ancient + "/reopen",
                () -> Map.of("reason", "matrix"), ADMIN_DIRECTOR));
        list.add(ep("relock", HttpMethod.POST, A + "/teachers/" + teacher + "/months/" + ancient + "/relock", ADMIN_DIRECTOR));
        list.add(ep("month events", HttpMethod.GET, A + "/teachers/" + teacher + "/months/" + ancient + "/events", ADMIN_DIRECTOR));
        list.add(ep("export", HttpMethod.GET, A + "/export?month=" + month, ADMIN_DIRECTOR));
        return list;
    }

    /** The token each role uses; the Manager is the one in scope of the Teacher the table addresses. */
    private Map<Role, String> tokens(World w) {
        Map<Role, String> tokens = new LinkedHashMap<>();
        tokens.put(Role.ADMIN, w.admin());
        tokens.put(Role.DIRECTOR, w.director());
        tokens.put(Role.MANAGER, w.managerA().token());
        tokens.put(Role.TEACHER, w.teacherA().token());
        tokens.put(Role.SYSTEM, signInAs(Role.SYSTEM).token());
        return tokens;
    }

    @Test
    void everyEndpointHonoursTheRolesInTheContract() {
        World w = newWorld();
        Map<Role, String> tokens = tokens(w);
        List<String> wrong = new ArrayList<>();

        try {
            for (Endpoint e : endpoints(w)) {
                for (var role : tokens.entrySet()) {
                    int status = send(e.method(), e.path(), role.getValue(), e.body().get()).status();
                    boolean allowed = e.allowed().contains(role.getKey());
                    if (allowed && (status == 401 || status == 403)) {
                        wrong.add(e.name() + ": " + role.getKey() + " should be allowed but got " + status);
                    }
                    if (!allowed && status != 403) {
                        wrong.add(e.name() + ": " + role.getKey() + " should be refused (403) but got " + status);
                    }
                }
                int anonymous = send(e.method(), e.path(), null, e.body().get()).status();
                if (anonymous != 401) {
                    wrong.add(e.name() + ": anonymous should get 401 but got " + anonymous);
                }
            }
        } finally {
            send(HttpMethod.DELETE, A + "/calendar/non-working-dates/2001-02-03", w.admin(), null);
        }

        assertThat(wrong).as("authorization mismatches").isEmpty();
    }

    @Test
    void theTableCoversEveryAttendanceRoute() {
        World w = newWorld();
        Set<String> covered = new TreeSet<>();
        for (Endpoint e : endpoints(w)) {
            covered.add(e.method() + " " + normalize(e.path().replaceAll("\\?.*", "")));
        }
        Set<String> actual = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            Set<String> patterns = info.getPathPatternsCondition() == null
                    ? Set.of()
                    : info.getPathPatternsCondition().getPatternValues();
            for (String pattern : patterns) {
                if (pattern.startsWith(A)) {
                    for (var method : info.getMethodsCondition().getMethods()) {
                        actual.add(method.name() + " " + normalize(pattern));
                    }
                }
            }
        }

        assertThat(actual).as("attendance routes discovered").hasSizeGreaterThan(20);
        assertThat(actual).as("routes missing from the authorization table").isSubsetOf(covered);
    }

    /** Path parameters and concrete ids/dates both become {x} so a table row matches its route pattern. */
    private static String normalize(String path) {
        return path
                .replaceAll("\\{[^}]+}", "{x}")
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "{x}")
                .replaceAll("(?<=/)\\d{4}-\\d{2}-\\d{2}(?=/|$)", "{x}")
                .replaceAll("(?<=/)\\d{4}-\\d{2}(?=/|$)", "{x}");
    }
}
