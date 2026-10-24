package com.hls.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hls.support.MasterDataTestBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/** Spec 027 T009: the schema itself refuses what the rules forbid. */
class MovementSchemaTest extends MasterDataTestBase {

    private UUID insertRequest(String kind, UUID from, UUID to, UUID replacement, UUID destination, String status, UUID requestedBy, UUID decidedBy) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into movement_request (id, kind, teacher_id, from_school_id, to_school_id, replacement_teacher_id,"
                        + " outgoing_destination_school_id, effective_on, reason_category, status, requested_by, requested_at,"
                        + " decided_by, version, created_at, updated_at)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?, 'OTHER', ?, ?, now(), ?, 0, now(), now())",
                id, kind, UUID.randomUUID(), from, to, replacement, destination, LocalDate.now().plusDays(3), status, requestedBy, decidedBy);
        return id;
    }

    @Test
    void replacementNeedsAReplacementTeacherAndTheSameSchool() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        assertThatThrownBy(() -> insertRequest("REPLACEMENT", a, a, null, null, "PENDING", user, null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRequest("REPLACEMENT", a, b, UUID.randomUUID(), null, "PENDING", user, null))
                .isInstanceOf(DataAccessException.class);
        assertThat(insertRequest("REPLACEMENT", a, a, UUID.randomUUID(), null, "PENDING", user, null)).isNotNull();
    }

    @Test
    void transferNeedsADifferentSchoolAndNoReplacementOrDestination() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        assertThatThrownBy(() -> insertRequest("TRANSFER", a, a, null, null, "PENDING", user, null)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRequest("TRANSFER", a, b, UUID.randomUUID(), null, "PENDING", user, null))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRequest("TRANSFER", a, b, null, UUID.randomUUID(), "PENDING", user, null))
                .isInstanceOf(DataAccessException.class);
        assertThat(insertRequest("TRANSFER", a, b, null, null, "PENDING", user, null)).isNotNull();
    }

    @Test
    void selfDecisionIsRefused() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        assertThatThrownBy(() -> insertRequest("TRANSFER", a, b, null, null, "APPROVED", user, user)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertRequest("TRANSFER", a, b, null, null, "REJECTED", user, user)).isInstanceOf(DataAccessException.class);
        assertThat(insertRequest("TRANSFER", a, b, null, null, "APPROVED", user, UUID.randomUUID())).isNotNull();
    }

    @Test
    void onlyOneOpenParticipationPerTeacher() {
        UUID teacher = UUID.randomUUID();
        jdbc.update("insert into movement_participant (id, request_id, teacher_id, role, open) values (?, ?, ?, 'MOVING', true)",
                UUID.randomUUID(), UUID.randomUUID(), teacher);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into movement_participant (id, request_id, teacher_id, role, open) values (?, ?, ?, 'MOVING', true)",
                        UUID.randomUUID(), UUID.randomUUID(), teacher))
                .isInstanceOf(DataAccessException.class);
        jdbc.update("insert into movement_participant (id, request_id, teacher_id, role, open) values (?, ?, ?, 'MOVING', false)",
                UUID.randomUUID(), UUID.randomUUID(), teacher);
    }

    @Test
    void aLinkedAssignmentIsUniquePerRequest() {
        UUID request = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        String sql = "insert into movement_assignment (id, request_id, assignment_id, teacher_id, effect) values (?, ?, ?, ?, 'CREATED')";
        jdbc.update(sql, UUID.randomUUID(), request, assignment, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update(sql, UUID.randomUUID(), request, assignment, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void historyRowsCannotBeChangedOrDeleted() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into movement_request_history (id, request_id, action, to_status, at) values (?, ?, 'RAISED', 'PENDING', now())",
                id, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("update movement_request_history set note = 'x' where id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from movement_request_history where id = ?", id)).isInstanceOf(DataAccessException.class);
    }
}
