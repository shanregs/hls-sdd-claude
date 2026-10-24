package com.hls.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.identity.user.Role;
import com.hls.support.MasterDataTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 028 SC-007: without Payroll View nothing about payroll reaches the Dashboard; a Manager never sees the Payroll area. */
class PayrollPermissionTest extends MasterDataTestBase {

    private static final String BASE = "/api/v1/dashboard";
    private static final String GRANT = "/api/v1/identity/permission-matrix/ADMIN/PAYROLL/VIEW";

    @Test
    void removingPayrollViewRemovesEveryTraceOfPayrollFromTheDashboard() {
        String admin = signInAs(Role.ADMIN).token();
        assertThat(get(BASE, admin).body()).contains("PAYROLL");
        assertThat(get(BASE + "/areas/PAYROLL", admin).status()).isEqualTo(200);

        Resp revoked = put(GRANT, admin, Map.of("granted", false));
        assertThat(revoked.status()).as(revoked.body()).isEqualTo(200);
        try {
            Resp manifest = get(BASE, admin);
            assertThat(manifest.body()).doesNotContain("PAYROLL").doesNotContain("payroll.");
            assertThat(get(BASE + "/areas/PAYROLL", admin).status()).isEqualTo(404);
            assertThat(get(BASE + "/areas/PAYROLL/tiles/payroll.run", admin).status()).isEqualTo(404);
            for (String area : new String[] {"MANAGERS", "SCHOOLS", "RECRUITMENT", "TEACHERS", "ATTENDANCE_LEAVE"}) {
                Resp resp = get(BASE + "/areas/" + area, admin);
                if (resp.status() == 200) {
                    assertThat(resp.body().toLowerCase()).doesNotContain("payroll").doesNotContain("rupees");
                }
            }
            Resp table = get(BASE + "/managers", admin);
            assertThat(table.body().toLowerCase()).doesNotContain("payroll");
        } finally {
            assertThat(put(GRANT, admin, Map.of("granted", true)).status()).isEqualTo(200);
        }
        assertThat(get(BASE + "/areas/PAYROLL", admin).status()).isEqualTo(200);
    }

    @Test
    void aManagerOnlyCallerGets404ForThePayrollArea() {
        String admin = signInAs(Role.ADMIN).token();
        ManagerCtx manager = newManager(admin, zone(admin));
        assertThat(get(BASE, manager.token()).body()).doesNotContain("PAYROLL");
        assertThat(get(BASE + "/areas/PAYROLL", manager.token()).status()).isEqualTo(404);
        assertThat(get(BASE + "/areas/PAYROLL/tiles/payroll.run", manager.token()).status()).isEqualTo(404);
    }
}
