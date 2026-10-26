package com.hls.schoolbilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Spec 031 V100: the backfill (every ACTIVE contract becomes APPROVED_LEGACY, the others get no row), the table checks and
 * the triggers (document removal-only, history append-only, position salary only while PENDING). Runs on its own database
 * so the shared test database keeps its schema version.
 */
class MouMigrationTest {

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

    private static UUID contract(Connection c, String state, String starts) throws SQLException {
        UUID id = UUID.randomUUID();
        String sql = switch (state) {
            case "ACTIVE" -> "insert into contract (id, school_id, state, salary_mode, teacher_count, rate, signed_on,"
                    + " starts_on, version, created_at) values (?, ?, 'ACTIVE', 'SAME_FOR_ALL', 1, 1000, date '2026-01-01',"
                    + " date '" + starts + "', 0, now())";
            case "RATE_PENDING" -> "insert into contract (id, school_id, state, starts_on, version, created_at)"
                    + " values (?, ?, 'RATE_PENDING', date '" + starts + "', 0, now())";
            default -> "insert into contract (id, school_id, state, starts_on, version, created_at)"
                    + " values (?, ?, 'CANCELLED', date '" + starts + "', 0, now())";
        };
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, UUID.randomUUID());
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID position(Connection c, UUID contract, int number) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "insert into contract_position (id, contract_id, number, salary) values (?, ?, ?, 1000)")) {
            ps.setObject(1, id);
            ps.setObject(2, contract);
            ps.setInt(3, number);
            ps.executeUpdate();
        }
        return id;
    }

    private static long count(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }

    @Test
    void activeContractsAreBackfilledAsLegacyAndOthersGetNoRowAndTheGuardsHold() throws SQLException {
        flyway("90").migrate();
        UUID active1;
        UUID active2;
        UUID pending;
        UUID cancelled;
        UUID position;
        try (Connection c = connect()) {
            active1 = contract(c, "ACTIVE", "2026-01-01");
            active2 = contract(c, "ACTIVE", "2026-02-01");
            pending = contract(c, "RATE_PENDING", "2026-03-01");
            cancelled = contract(c, "CANCELLED", "2026-04-01");
            position = position(c, active1, 1);
        }
        flyway("100").migrate();

        try (Connection c = connect()) {
            for (UUID id : new UUID[] {active1, active2}) {
                assertThat(count(c, "select count(*) from mou_approval where contract_id = ? and status = 'APPROVED_LEGACY'"
                        + " and round = 0 and decided_by is null", id)).isEqualTo(1);
                assertThat(count(c, "select count(*) from mou_approval_event where contract_id = ? and action = 'LEGACY_MARKED'", id))
                        .isEqualTo(1);
            }
            for (UUID id : new UUID[] {pending, cancelled}) {
                assertThat(count(c, "select count(*) from mou_approval where contract_id = ?", id)).isZero();
                assertThat(count(c, "select count(*) from mou_approval_event where contract_id = ?", id)).isZero();
            }

            // check constraints reject bad combinations
            UUID extra = contract(c, "ACTIVE", "2027-01-01");
            exec(c, "delete from mou_approval where contract_id = ?", extra);
            assertThatThrownBy(() -> exec(c,
                    "insert into mou_approval (id, contract_id, status, round, version, created_at, updated_at)"
                            + " values (gen_random_uuid(), ?, 'PENDING', 1, 0, now(), now())", extra))
                    .hasMessageContaining("ck_mou_approval_pending");
            assertThatThrownBy(() -> exec(c,
                    "insert into mou_approval (id, contract_id, status, round, decided_by, decided_at, version, created_at,"
                            + " updated_at) values (gen_random_uuid(), ?, 'REJECTED', 1, gen_random_uuid(), now(), 0, now(), now())",
                    extra))
                    .hasMessageContaining("ck_mou_approval_rejected");
            assertThatThrownBy(() -> exec(c,
                    "insert into mou_approval (id, contract_id, status, round, decided_by, version, created_at, updated_at)"
                            + " values (gen_random_uuid(), ?, 'APPROVED_LEGACY', 0, gen_random_uuid(), 0, now(), now())", extra))
                    .hasMessageContaining("ck_mou_approval_legacy");

            // documents: written once, only the removal columns change, once, never deleted
            UUID doc = UUID.randomUUID();
            exec(c, "insert into mou_document (id, contract_id, version_no, file_id, file_name, content_type, size_bytes,"
                    + " added_by, added_at) values (?, ?, 1, gen_random_uuid(), 'a.pdf', 'application/pdf', 10, gen_random_uuid(), now())",
                    doc, active1);
            assertThatThrownBy(() -> exec(c, "update mou_document set file_name = 'b.pdf' where id = ?", doc))
                    .hasMessageContaining("can only be marked removed");
            assertThatThrownBy(() -> exec(c, "delete from mou_document where id = ?", doc))
                    .hasMessageContaining("cannot be deleted");
            exec(c, "update mou_document set removed_by = gen_random_uuid(), removed_at = now(), removal_reason = 'wrong file'"
                    + " where id = ?", doc);
            assertThatThrownBy(() -> exec(c, "update mou_document set removal_reason = 'other' where id = ?", doc))
                    .hasMessageContaining("can only be marked removed");

            // history is append-only
            assertThatThrownBy(() -> exec(c, "update mou_approval_event set note = 'x' where contract_id = ?", active1))
                    .hasMessageContaining("cannot be updated or deleted");
            assertThatThrownBy(() -> exec(c, "delete from mou_approval_event where contract_id = ?", active1))
                    .hasMessageContaining("cannot be updated or deleted");

            // a position salary changes only while the approval is PENDING, and nothing else ever does
            assertThatThrownBy(() -> exec(c, "update contract_position set salary = 2000 where id = ?", position))
                    .hasMessageContaining("cannot be updated or deleted");
            exec(c, "update mou_approval set status = 'PENDING', round = 1, submitted_by = gen_random_uuid(),"
                    + " submitted_at = now(), version = version + 1 where contract_id = ?", active1);
            exec(c, "update contract_position set salary = 2000 where id = ?", position);
            assertThat(count(c, "select count(*) from contract_position where id = ? and salary = 2000", position)).isEqualTo(1);
            assertThatThrownBy(() -> exec(c, "update contract_position set title = 'Maths' where id = ?", position))
                    .hasMessageContaining("cannot be updated or deleted");
            assertThatThrownBy(() -> exec(c, "update contract_position set number = 2 where id = ?", position))
                    .hasMessageContaining("cannot be updated or deleted");
            assertThatThrownBy(() -> exec(c, "update contract_position set salary = 0 where id = ?", position))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> exec(c, "delete from contract_position where id = ?", position))
                    .hasMessageContaining("cannot be updated or deleted");

            // signatories stay final
            UUID signatory = UUID.randomUUID();
            exec(c, "insert into contract_signatory (id, contract_id, party, name, designation) values (?, ?, 'SCHOOL', 'A', 'B')",
                    signatory, active1);
            assertThatThrownBy(() -> exec(c, "update contract_signatory set name = 'C' where id = ?", signatory))
                    .hasMessageContaining("cannot be updated or deleted");
        }
    }
}
