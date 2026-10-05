package com.hls.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.permissions.PermissionAction;
import com.hls.identity.permissions.PermissionMatrixService;
import com.hls.identity.permissions.PermissionModule;
import com.hls.identity.user.Role;
import com.hls.notification.api.NotificationType;
import com.hls.notification.internal.MessageFactory;
import com.hls.notification.internal.NotificationService;
import com.hls.support.MasterDataTestBase;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Spec 010 US3: the own-scope API, every role, and System. */
class NotificationApiTest extends MasterDataTestBase {

    private static final String API = "/api/v1/me/notifications";

    @Autowired
    private NotificationService service;

    @Autowired
    private PermissionMatrixService permissions;

    private UUID add(UUID user, String title) {
        return service.create(user, NotificationType.LEAVE_DECIDED, new MessageFactory.Text(title, "message", "/leave/history"))
                .getId();
    }

    private static String field(String body, String name) {
        var m = java.util.regex.Pattern.compile("\"" + name + "\":\"?([^\",}\\]]*)\"?").matcher(body);
        assertThat(m.find()).as(name + " in " + body).isTrue();
        return m.group(1);
    }

    @Test
    void aUserListsCountsReadsAndDeletesTheirOwnNotifications() {
        Signed me = signInAs(Role.TEACHER);
        UUID first = add(me.userId(), "First");
        add(me.userId(), "Second");
        add(me.userId(), "Third");

        Resp list = get(API + "?size=2", me.token());
        assertThat(list.status()).as(list.body()).isEqualTo(200);
        assertThat(list.body()).contains("\"totalElements\":3").contains("\"unread\":3").contains("\"channel\":\"IN_APP\"");
        assertThat(field(get(API + "/unread-count", me.token()).body(), "unread")).isEqualTo("3");

        Resp read = post(API + "/" + first + "/read", me.token(), Map.of());
        assertThat(read.status()).isEqualTo(200);
        assertThat(field(read.body(), "read")).isEqualTo("true");
        assertThat(field(post(API + "/" + first + "/read", me.token(), Map.of()).body(), "read")).isEqualTo("true");
        assertThat(field(get(API + "/unread-count", me.token()).body(), "unread")).isEqualTo("2");
        assertThat(get(API + "?unread=true", me.token()).body()).contains("\"totalElements\":2");

        Resp all = post(API + "/read-all", me.token(), Map.of());
        assertThat(field(all.body(), "marked")).isEqualTo("2");
        assertThat(field(get(API + "/unread-count", me.token()).body(), "unread")).isEqualTo("0");

        assertThat(delete(API + "/" + first, me.token()).status()).isEqualTo(204);
        Resp cleared = delete(API + "/read", me.token());
        assertThat(field(cleared.body(), "deleted")).isEqualTo("2");
        assertThat(get(API, me.token()).body()).contains("\"totalElements\":0");
    }

    @Test
    void twoUsersNeverSeeOrChangeEachOthersNotifications() {
        Signed alice = signInAs(Role.MANAGER);
        Signed bob = signInAs(Role.MANAGER);
        UUID aliceOne = add(alice.userId(), "For Alice");
        add(bob.userId(), "For Bob");

        assertThat(get(API, alice.token()).body()).contains("For Alice").doesNotContain("For Bob");
        assertThat(get(API, bob.token()).body()).contains("For Bob").doesNotContain("For Alice");
        assertThat(post(API + "/" + aliceOne + "/read", bob.token(), Map.of()).status()).isEqualTo(404);
        assertThat(delete(API + "/" + aliceOne, bob.token()).status()).isEqualTo(404);
        assertThat(post(API + "/read-all", bob.token(), Map.of()).status()).isEqualTo(200);
        assertThat(field(get(API + "/unread-count", alice.token()).body(), "unread")).isEqualTo("1");
        assertThat(delete(API + "/" + UUID.randomUUID(), alice.token()).status()).isEqualTo(404);
    }

    @Test
    void everyBusinessRoleMayUseItAndSystemMayNot() {
        for (Role role : new Role[] {Role.ADMIN, Role.DIRECTOR, Role.MANAGER, Role.TEACHER}) {
            Signed user = signInAs(role);
            UUID id = add(user.userId(), "Hello " + role);
            assertThat(get(API, user.token()).status()).as(role + " list").isEqualTo(200);
            assertThat(get(API + "/unread-count", user.token()).status()).as(role + " count").isEqualTo(200);
            assertThat(post(API + "/" + id + "/read", user.token(), Map.of()).status()).as(role + " read").isEqualTo(200);
            assertThat(post(API + "/read-all", user.token(), Map.of()).status()).as(role + " read-all").isEqualTo(200);
            assertThat(delete(API + "/" + id, user.token()).status()).as(role + " delete").isEqualTo(204);
            assertThat(delete(API + "/read", user.token()).status()).as(role + " clear").isEqualTo(200);
        }
        Signed system = signInAs(Role.SYSTEM);
        UUID id = add(system.userId(), "Never shown");
        assertThat(get(API, system.token()).status()).isEqualTo(403);
        assertThat(get(API + "/unread-count", system.token()).status()).isEqualTo(403);
        assertThat(post(API + "/" + id + "/read", system.token(), Map.of()).status()).isEqualTo(403);
        assertThat(post(API + "/read-all", system.token(), Map.of()).status()).isEqualTo(403);
        assertThat(delete(API + "/" + id, system.token()).status()).isEqualTo(403);
        assertThat(delete(API + "/read", system.token()).status()).isEqualTo(403);
    }

    @Test
    void aRoleWithoutDeleteCanReadButNotDelete() {
        Signed teacher = signInAs(Role.TEACHER);
        UUID id = add(teacher.userId(), "Keep me");
        var turnedOff = permissions.updateGrant(
                Role.TEACHER, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE, false, teacher.userId());
        assertThat(turnedOff.success()).isTrue();
        try {
            assertThat(get(API, teacher.token()).status()).isEqualTo(200);
            assertThat(delete(API + "/" + id, teacher.token()).status()).isEqualTo(403);
            assertThat(delete(API + "/read", teacher.token()).status()).isEqualTo(403);
        } finally {
            permissions.updateGrant(
                    Role.TEACHER, PermissionModule.NOTIFICATIONS, PermissionAction.DELETE, true, teacher.userId());
        }
        assertThat(delete(API + "/" + id, teacher.token()).status()).isEqualTo(204);
    }
}
