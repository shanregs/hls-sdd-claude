package com.hls.transfer.internal;

import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherPlacementSource.Assignment;
import com.hls.teacher.api.TeacherScopeQueries;
import com.hls.transfer.internal.RequestView.Ref;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Teacher's and a School's movement history (spec 027 US4). Placement rows are read live through
 * {@code TeacherPlacementSource.historyOf} and joined to {@code movement_assignment}; no placement data is copied.
 */
@Service
public class MovementHistoryQueries {

    /** One placement line; {@code to} is null while the placement is open. */
    public record MovementLine(
            Ref teacher,
            Ref school,
            Integer positionNumber,
            LocalDate from,
            LocalDate to,
            String kind,
            UUID requestId,
            String reason,
            String requestedBy,
            String approvedBy,
            Ref otherTeacher,
            Ref otherSchool) {}

    private final MovementRequestRepository requests;
    private final MovementAssignmentRepository links;
    private final MovementQueries queries;
    private final MovementFacts facts;
    private final MovementScope scope;
    private final TeacherScopeQueries teacherScopes;

    MovementHistoryQueries(
            MovementRequestRepository requests,
            MovementAssignmentRepository links,
            MovementQueries queries,
            MovementFacts facts,
            MovementScope scope,
            TeacherScopeQueries teacherScopes) {
        this.requests = requests;
        this.links = links;
        this.queries = queries;
        this.facts = facts;
        this.scope = scope;
        this.teacherScopes = teacherScopes;
    }

    @Transactional(readOnly = true)
    public List<MovementLine> ofTeacher(Caller caller, UUID teacherId) {
        if (facts.teacher(teacherId).isEmpty()) {
            throw new NotFoundException("Teacher not found.");
        }
        List<MovementRequest> involving = requests.involving(teacherId);
        boolean visible = caller.orgWide()
                || teacherScopes.isTeacherInScope(caller.userId(), caller.roles(), teacherId)
                || involving.stream().anyMatch(r -> scope.canRead(caller, r));
        if (!visible) {
            throw new NotFoundException("Teacher not found.");
        }
        List<Assignment> rows = facts.placementHistory(teacherId).stream()
                .filter(a -> "ACTIVE".equals(a.status()))
                .sorted(Comparator.comparing(Assignment::startsOn))
                .toList();
        Map<UUID, MovementAssignment> created = new HashMap<>();
        links.findByAssignmentIdIn(rows.stream().map(Assignment::id).toList()).stream()
                .filter(l -> "CREATED".equals(l.getEffect()))
                .forEach(l -> created.put(l.getAssignmentId(), l));
        Map<UUID, MovementRequest> byId = new HashMap<>();
        requests.findAllById(created.values().stream().map(MovementAssignment::getRequestId).collect(java.util.stream.Collectors.toSet()))
                .forEach(r -> byId.put(r.getId(), r));
        Set<UUID> schoolIds = new HashSet<>();
        Set<UUID> teacherIds = new HashSet<>();
        rows.forEach(a -> schoolIds.add(a.schoolId()));
        byId.values().forEach(r -> {
            teacherIds.add(r.getTeacherId());
            if (r.getReplacementTeacherId() != null) {
                teacherIds.add(r.getReplacementTeacherId());
            }
        });
        teacherIds.add(teacherId);
        Map<UUID, String> schoolNames = facts.schoolNames(schoolIds);
        Map<UUID, String> teacherNames = facts.teacherNames(teacherIds);
        List<MovementLine> lines = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Assignment a = rows.get(i);
            MovementAssignment link = created.get(a.id());
            MovementRequest r = link == null ? null : byId.get(link.getRequestId());
            String kind = r != null ? r.getKind() : i == 0 ? "INITIAL" : "DIRECT";
            Ref other = null;
            if (r != null && "REPLACEMENT".equals(r.getKind())) {
                UUID otherId = teacherId.equals(r.getTeacherId()) ? r.getReplacementTeacherId() : r.getTeacherId();
                other = new Ref(otherId, teacherNames.get(otherId));
            }
            lines.add(new MovementLine(
                    new Ref(teacherId, teacherNames.get(teacherId)),
                    new Ref(a.schoolId(), schoolNames.get(a.schoolId())),
                    a.positionNumber(),
                    a.startsOn(),
                    a.endsOn(),
                    kind,
                    r == null ? null : r.getId(),
                    r == null ? null : reason(r),
                    r == null ? null : facts.userName(r.getRequestedBy()),
                    r == null || r.getDecidedBy() == null ? null : facts.userName(r.getDecidedBy()),
                    other,
                    null));
        }
        return lines;
    }

    /** Arrivals and departures caused by Approved or Applied requests at the School, newest first. */
    @Transactional(readOnly = true)
    public List<MovementLine> ofSchool(Caller caller, UUID schoolId) {
        if (!scope.scope(caller).allowsSchool(schoolId)) {
            throw new NotFoundException("School not found.");
        }
        List<MovementRequest> rows = requests.atSchool(schoolId).stream()
                .filter(r -> {
                    String shown = queries.shown(r).status();
                    return "APPROVED".equals(shown) || "APPLIED".equals(shown);
                })
                .toList();
        Set<UUID> teacherIds = new HashSet<>();
        Set<UUID> schoolIds = new HashSet<>();
        rows.forEach(r -> {
            teacherIds.add(r.getTeacherId());
            if (r.getReplacementTeacherId() != null) {
                teacherIds.add(r.getReplacementTeacherId());
            }
            schoolIds.add(r.getFromSchoolId());
            schoolIds.add(r.getToSchoolId());
            if (r.getOutgoingDestinationSchoolId() != null) {
                schoolIds.add(r.getOutgoingDestinationSchoolId());
            }
        });
        Map<UUID, String> teacherNames = facts.teacherNames(teacherIds);
        Map<UUID, String> schoolNames = facts.schoolNames(schoolIds);
        Optional<String> thisSchool = Optional.ofNullable(schoolNames.get(schoolId));
        List<MovementLine> lines = new ArrayList<>();
        for (MovementRequest r : rows) {
            String reason = reason(r);
            String requestedBy = facts.userName(r.getRequestedBy());
            String approvedBy = r.getDecidedBy() == null ? null : facts.userName(r.getDecidedBy());
            Ref school = new Ref(schoolId, thisSchool.orElse(null));
            if ("TRANSFER".equals(r.getKind())) {
                boolean arrival = schoolId.equals(r.getToSchoolId());
                UUID otherSchool = arrival ? r.getFromSchoolId() : r.getToSchoolId();
                lines.add(new MovementLine(
                        new Ref(r.getTeacherId(), teacherNames.get(r.getTeacherId())),
                        school,
                        null,
                        arrival ? r.getEffectiveOn() : null,
                        arrival ? null : r.getEffectiveOn().minusDays(1),
                        "TRANSFER",
                        r.getId(),
                        reason,
                        requestedBy,
                        approvedBy,
                        null,
                        new Ref(otherSchool, schoolNames.get(otherSchool))));
            } else {
                if (schoolId.equals(r.getFromSchoolId())) {
                    lines.add(replacementLine(r, r.getTeacherId(), r.getReplacementTeacherId(), school, null, r.getEffectiveOn().minusDays(1),
                            reason, requestedBy, approvedBy, teacherNames, null));
                    lines.add(replacementLine(r, r.getReplacementTeacherId(), r.getTeacherId(), school, r.getEffectiveOn(), null,
                            reason, requestedBy, approvedBy, teacherNames, null));
                }
                UUID destination = r.getOutgoingDestinationSchoolId();
                if (schoolId.equals(destination)) {
                    lines.add(replacementLine(r, r.getTeacherId(), r.getReplacementTeacherId(), school, r.getEffectiveOn(), null,
                            reason, requestedBy, approvedBy, teacherNames,
                            new Ref(r.getFromSchoolId(), schoolNames.get(r.getFromSchoolId()))));
                }
            }
        }
        lines.sort(Comparator.comparing((MovementLine l) -> l.from() != null ? l.from() : l.to()).reversed());
        return lines;
    }

    private MovementLine replacementLine(
            MovementRequest r,
            UUID teacher,
            UUID other,
            Ref school,
            LocalDate from,
            LocalDate to,
            String reason,
            String requestedBy,
            String approvedBy,
            Map<UUID, String> names,
            Ref otherSchool) {
        return new MovementLine(
                new Ref(teacher, names.get(teacher)),
                school,
                null,
                from,
                to,
                "REPLACEMENT",
                r.getId(),
                reason,
                requestedBy,
                approvedBy,
                new Ref(other, names.get(other)),
                otherSchool);
    }

    private static String reason(MovementRequest r) {
        return r.getNote() == null ? r.getReasonCategory() : r.getReasonCategory() + ": " + r.getNote();
    }
}
