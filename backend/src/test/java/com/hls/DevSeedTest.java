package com.hls;

import static org.assertj.core.api.Assertions.assertThat;

import com.hls.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** The dev-only demo data (hls.seed.demo-data=true) builds a consistent Zone/School/Manager/Teacher set. */
@TestPropertySource(properties = "hls.seed.demo-data=true")
class DevSeedTest extends IntegrationTestBase {

    @Test
    void demoDataIsSeededConsistentlyAndSignInStillWorks() {
        Signed admin = signIn(null, "9800000001", "Password123!");

        assertThat(get("/api/v1/zones?query=Demo Zone", admin.token()).body())
                .contains("Demo Zone")
                .contains("\"placeCount\":2")
                .contains("\"schoolCount\":2")
                .contains("\"managerCount\":1");
        assertThat(get("/api/v1/managers", admin.token()).body()).contains("Manoj Manager");
        String teachers = get("/api/v1/teachers?query=Tara", admin.token()).body();
        assertThat(teachers).contains("Tara Teacher").contains("Demo School One").contains("Manoj Manager");

        Signed manoj = signIn(null, "9800000003", "Password123!");
        assertThat(total(get("/api/v1/schools", manoj.token()))).isEqualTo(1);
        // Tara plus the two other demo Teachers placed in Demo School One by the Teacher seeder.
        assertThat(total(get("/api/v1/teachers", manoj.token()))).isEqualTo(3);
    }
}
