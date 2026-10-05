package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Spec 012 FR-009 and FR-010: V20 carries every interim placement over unchanged (same id, dates, status)
 * with no position, and gives each School that has live placements one "MoU pending" contract starting on its
 * earliest live placement. Runs on its own database so the shared test database keeps its schema version.
 */
class PlacementMigrationTest {

    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void start() {
        DB.start();
    }

    @AfterAll
    static void stop() {
        DB.stop();
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())
                .target(MigrationVersion.fromVersion(target))
                .load();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }

    private static void teacher(Connection c, UUID id, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "insert into teacher (id, name, status, status_effective_on, version, created_at, updated_at)"
                        + " values (?, ?, 'ACTIVE', date '2026-01-01', 0, now(), now())")) {
            ps.setObject(1, id);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    private static void placement(
            Connection c, UUID id, UUID teacher, UUID school, String starts, String ends, String status)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "insert into teacher_placement (id, teacher_id, school_id, starts_on, ends_on, status, created_at)"
                        + " values (?, ?, ?, ?, ?, ?, now())")) {
            ps.setObject(1, id);
            ps.setObject(2, teacher);
            ps.setObject(3, school);
            ps.setObject(4, LocalDate.parse(starts));
            ps.setObject(5, ends == null ? null : LocalDate.parse(ends));
            ps.setString(6, status);
            ps.executeUpdate();
        }
    }

    @Test
    void everyPlacementIsCarriedOverAndEachPlacedSchoolGetsAPendingContract() throws SQLException {
        flyway("19").migrate();

        UUID schoolA = UUID.randomUUID();
        UUID schoolB = UUID.randomUUID();
        UUID schoolC = UUID.randomUUID();
        UUID t1 = UUID.randomUUID();
        UUID t2 = UUID.randomUUID();
        UUID t3 = UUID.randomUUID();
        List<UUID> placementIds = new ArrayList<>();
        try (Connection c = connect()) {
            teacher(c, t1, "One");
            teacher(c, t2, "Two");
            teacher(c, t3, "Three");
            // School A: an open placement, and an ended one starting earlier (earliest live start is 2026-01-01)
            UUID a1 = UUID.randomUUID();
            UUID a2 = UUID.randomUUID();
            placement(c, a1, t1, schoolA, "2026-03-01", null, "ACTIVE");
            placement(c, a2, t2, schoolA, "2026-01-01", "2026-06-30", "ACTIVE");
            // School B: only a cancelled and a corrected row, so it has no live placement
            UUID b1 = UUID.randomUUID();
            UUID b2 = UUID.randomUUID();
            placement(c, b1, t3, schoolB, "2026-02-01", null, "CANCELLED");
            placement(c, b2, t3, schoolB, "2026-02-05", "2026-02-10", "CORRECTED");
            // School C: a scheduled move for t2, starting later
            UUID c1 = UUID.randomUUID();
            placement(c, c1, t2, schoolC, "2026-12-01", null, "ACTIVE");
            placementIds.addAll(List.of(a1, a2, b1, b2, c1));
        }

        flyway("20").migrate();

        try (Connection c = connect(); Statement st = c.createStatement()) {
            Set<UUID> copied = new HashSet<>();
            try (ResultSet rs = st.executeQuery(
                    "select a.id, a.starts_on, a.ends_on, a.status, a.position_id, p.starts_on p_start, p.ends_on p_end,"
                            + " p.status p_status, p.teacher_id pt, a.teacher_id at, p.school_id ps, a.school_id as_"
                            + " from contract_assignment a join teacher_placement p on p.id = a.id")) {
                while (rs.next()) {
                    copied.add(rs.getObject("id", UUID.class));
                    assertThat(rs.getObject("starts_on")).isEqualTo(rs.getObject("p_start"));
                    assertThat(rs.getObject("ends_on")).isEqualTo(rs.getObject("p_end"));
                    assertThat(rs.getString("status")).isEqualTo(rs.getString("p_status"));
                    assertThat(rs.getObject("at", UUID.class)).isEqualTo(rs.getObject("pt", UUID.class));
                    assertThat(rs.getObject("as_", UUID.class)).isEqualTo(rs.getObject("ps", UUID.class));
                    assertThat(rs.getObject("position_id")).isNull();
                }
            }
            assertThat(copied).containsExactlyInAnyOrderElementsOf(placementIds);
            try (ResultSet rs = st.executeQuery("select count(*) from contract_assignment")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(placementIds.size());
            }

            try (ResultSet rs = st.executeQuery(
                    "select school_id, state, starts_on, ends_on, salary_mode, teacher_count, rate, signed_on"
                            + " from contract order by starts_on")) {
                List<UUID> schools = new ArrayList<>();
                while (rs.next()) {
                    UUID school = rs.getObject("school_id", UUID.class);
                    schools.add(school);
                    assertThat(rs.getString("state")).isEqualTo("RATE_PENDING");
                    assertThat(rs.getObject("ends_on")).isNull();
                    assertThat(rs.getObject("salary_mode")).isNull();
                    assertThat(rs.getObject("teacher_count")).isNull();
                    assertThat(rs.getObject("rate")).isNull();
                    assertThat(rs.getObject("signed_on")).isNull();
                    if (school.equals(schoolA)) {
                        assertThat(rs.getObject("starts_on", LocalDate.class)).isEqualTo(LocalDate.of(2026, 1, 1));
                    }
                    if (school.equals(schoolC)) {
                        assertThat(rs.getObject("starts_on", LocalDate.class)).isEqualTo(LocalDate.of(2026, 12, 1));
                    }
                }
                assertThat(schools).containsExactlyInAnyOrder(schoolA, schoolC).doesNotContain(schoolB);
            }
        }

        flyway("21").migrate();
        try (Connection c = connect(); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("select to_regclass('teacher_placement')")) {
            rs.next();
            assertThat(rs.getObject(1)).isNull();
        }
    }
}
