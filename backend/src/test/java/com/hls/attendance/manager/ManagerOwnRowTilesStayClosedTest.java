package com.hls.attendance.manager;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spec 028: letting a Manager see their own row on the Dashboard must not open the Manager Attendance and Manager Leave
 * endpoints, which stay for Admin and Director.
 */
class ManagerOwnRowTilesStayClosedTest extends ManagerAttendanceTestBase {

    @Test
    void aManagerSeesTheOwnRowTilesButEveryExistingManagerEndpointStaysClosed() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = longJoinedManager(admin);
        String token = manager.token();
        String tiles = "/api/v1/dashboard/areas/MANAGERS/tiles/";

        assertThat(get(tiles + "managers.noCheckIn", token).status()).isIn(200);
        assertThat(get(tiles + "managers.onLeaveToday", token).status()).isEqualTo(200);
        assertThat(get(tiles + "managers.pendingRequests", token).status()).isEqualTo(200);
        assertThat(get("/api/v1/dashboard/managers", token).status()).isEqualTo(403);

        LocalDate today = today();
        List<String> closed = List.of(
                ATT + "/grid?month=" + java.time.YearMonth.from(today),
                ATT + "/missed",
                ATT + "/on-leave",
                ATT + "/regularizations",
                ATT + "/leave");
        for (String url : closed) {
            assertThat(get(url, token).status()).as(url).isIn(403, 404);
        }
    }
}
