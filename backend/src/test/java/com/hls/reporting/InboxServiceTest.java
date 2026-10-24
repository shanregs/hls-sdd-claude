package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.user.Role;
import com.hls.reporting.api.ApprovalItem;
import com.hls.reporting.api.ApprovalSource;
import com.hls.reporting.api.Link;
import com.hls.support.MasterDataTestBase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Spec 028 T042 and T044: merging, grant check, own requests dropped, oldest first, age, totals, paging and refusals. */
@Import(InboxServiceTest.FakeSources.class)
class InboxServiceTest extends MasterDataTestBase {

    private static final String INBOX = "/api/v1/dashboard/inbox";
    static volatile UUID callerOwn = UUID.randomUUID();
    static final UUID OLD = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID NEWER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    static final UUID NEWEST = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    static final UUID OWN = UUID.fromString("00000000-0000-0000-0000-0000000000a4");

    @TestConfiguration
    static class FakeSources {
        @Bean
        ApprovalSource fakeTasks() {
            return new ApprovalSource() {
                public String kind() {
                    return "TEST_KIND";
                }

                public String permissionModule() {
                    return "TASKS";
                }

                public PermissionAction action() {
                    return PermissionAction.APPROVE;
                }

                public List<ApprovalItem> pending(UUID userId, Set<Role> roles) {
                    LocalDate t = LocalDate.now();
                    return List.of(
                            new ApprovalItem("TEST_KIND", NEWEST, UUID.randomUUID(), "C", "third", t.minusDays(1), Link.to("/x")),
                            new ApprovalItem("TEST_KIND", OLD, UUID.randomUUID(), "A", "first", t.minusDays(30), Link.to("/x")),
                            new ApprovalItem("TEST_KIND", OWN, callerOwn, "Me", "mine", t.minusDays(40), Link.to("/x")),
                            new ApprovalItem("TEST_KIND", NEWER, UUID.randomUUID(), "B", "second", t.minusDays(10), Link.to("/x")));
                }
            };
        }

        @Bean
        ApprovalSource fakePayroll() {
            return new ApprovalSource() {
                public String kind() {
                    return "TEST_PAYROLL";
                }

                public String permissionModule() {
                    return "PAYROLL";
                }

                public PermissionAction action() {
                    return PermissionAction.APPROVE;
                }

                public List<ApprovalItem> pending(UUID userId, Set<Role> roles) {
                    return List.of(new ApprovalItem(
                            "TEST_PAYROLL", UUID.randomUUID(), UUID.randomUUID(), "P", "run", LocalDate.now(), Link.to("/y")));
                }
            };
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> ids(Resp resp) {
        return ((List<Map<String, Object>>) resp.map().get("items")).stream().map(i -> (String) i.get("itemId")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> totals(Resp resp) {
        return (Map<String, Object>) resp.map().get("totals");
    }

    @SuppressWarnings("unchecked")
    @Test
    void itemsAreOldestFirstWithoutTheCallersOwnRequestAndTotalsAreComplete() {
        Signed admin = signInAs(Role.ADMIN);
        callerOwn = admin.userId();

        Resp resp = get(INBOX + "?kind=TEST_KIND", admin.token());

        assertThat(resp.status()).as(resp.body()).isEqualTo(200);
        assertThat(ids(resp)).containsExactly(OLD.toString(), NEWER.toString(), NEWEST.toString());
        assertThat(((Number) totals(resp).get("TEST_KIND")).intValue()).isEqualTo(3);
        Map<String, Object> first = ((List<Map<String, Object>>) resp.map().get("items")).get(0);
        assertThat(((Number) first.get("ageDays")).intValue()).isBetween(15, 30);
        assertThat(first).containsKeys("kind", "requester", "subject", "askedOn", "link");
    }

    @Test
    void aKindWithoutTheGrantIsAbsentFromTheListAndTheTotals() {
        Signed admin = signInAs(Role.ADMIN);
        Signed director = signInAs(Role.DIRECTOR);
        callerOwn = admin.userId();

        assertThat(totals(get(INBOX, admin.token()))).doesNotContainKey("TEST_PAYROLL");
        assertThat(totals(get(INBOX, director.token()))).containsKey("TEST_PAYROLL");
        assertThat(get(INBOX + "?kind=TEST_PAYROLL", admin.token()).status()).isEqualTo(400);
    }

    @Test
    void pagingKeepsTheTotalsAndNeverLosesItems() {
        Signed admin = signInAs(Role.ADMIN);
        callerOwn = admin.userId();

        Resp second = get(INBOX + "?kind=TEST_KIND&page=1&size=2", admin.token());

        assertThat(ids(second)).containsExactly(NEWEST.toString());
        assertThat(((Number) second.map().get("total")).intValue()).isEqualTo(3);
        assertThat(((Number) totals(second).get("TEST_KIND")).intValue()).isEqualTo(3);
    }

    @Test
    void aManagerOnlyCallerGets403AndTeacherSystemAndAnonymousAreRefused() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin, zone(admin));
        assertThat(get(INBOX, manager.token()).status()).isEqualTo(403);
        assertThat(get(INBOX, signInAs(Role.TEACHER).token()).status()).isEqualTo(403);
        assertThat(get(INBOX, signInAs(Role.SYSTEM).token()).status()).isEqualTo(403);
        assertThat(get(INBOX, null).status()).isEqualTo(401);
        assertThat(get(INBOX + "?kind=NOPE", admin).status()).isEqualTo(400);
        assertThat(get("/api/v1/dashboard", admin).map().get("inbox")).isEqualTo(true);
    }
}
