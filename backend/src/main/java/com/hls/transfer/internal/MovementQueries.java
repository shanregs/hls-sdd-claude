package com.hls.transfer.internal;

import com.hls.school.api.NotFoundException;
import com.hls.school.api.PageResponse;
import com.hls.schoolbilling.api.PlacementMoves;
import com.hls.schoolbilling.api.PlacementMoves.VacantPosition;
import com.hls.schoolbilling.api.SchoolContracts;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.teacher.api.TeacherPlacementSource;
import com.hls.teacher.api.TeacherPlacementSource.Assignment;
import com.hls.transfer.internal.MovementChecks.Spec;
import com.hls.transfer.internal.RequestView.CheckView;
import com.hls.transfer.internal.RequestView.HistoryView;
import com.hls.transfer.internal.RequestView.PositionRef;
import com.hls.transfer.internal.RequestView.Ref;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads for spec 027: request detail and list, the derived status, vacancies and replacement candidates. */
@Service
public class MovementQueries {

    static final String STALE_NOTE = "Teacher no longer placed there";
    private static final LocalDate FOREVER = LocalDate.of(9999, 12, 31);

    public record Shown(String status, String note) {}

    public record Vacancy(UUID positionId, int number, java.math.BigDecimal salary) {}

    public record Candidate(UUID id, String name, String status) {}

    private final MovementRequestRepository requests;
    private final MovementAssignmentRepository links;
    private final MovementHistoryRepository history;
    private final MovementParticipantRepository participants;
    private final MovementChecks checks;
    private final MovementScope scope;
    private final MovementFacts facts;
    private final PlacementMoves moves;
    private final SchoolContracts contracts;
    private final TeacherDirectory teachers;
    private final TeacherPlacementSource placements;

    MovementQueries(
            MovementRequestRepository requests,
            MovementAssignmentRepository links,
            MovementHistoryRepository history,
            MovementParticipantRepository participants,
            MovementChecks checks,
            MovementScope scope,
            MovementFacts facts,
            PlacementMoves moves,
            SchoolContracts contracts,
            TeacherDirectory teachers,
            TeacherPlacementSource placements) {
        this.requests = requests;
        this.links = links;
        this.history = history;
        this.participants = participants;
        this.checks = checks;
        this.scope = scope;
        this.facts = facts;
        this.moves = moves;
        this.contracts = contracts;
        this.teachers = teachers;
        this.placements = placements;
    }

    // ------------------------------------------------------------------ derived status

    /** APPLIED is never stored: an Approved request whose date has arrived with every linked row still ACTIVE. */
    Shown shown(MovementRequest r) {
        if (!"APPROVED".equals(r.getStatus())) {
            return new Shown(r.getStatus(), "CANCELLED".equals(r.getStatus()) || "REJECTED".equals(r.getStatus()) ? r.getDecisionReason() : null);
        }
        Map<UUID, String> rowStatus = new HashMap<>();
        for (UUID teacher : new HashSet<>(links.findByRequestId(r.getId()).stream().map(MovementAssignment::getTeacherId).toList())) {
            facts.placementHistory(teacher).forEach(a -> rowStatus.put(a.id(), a.status()));
        }
        boolean stale = links.findByRequestId(r.getId()).stream()
                .anyMatch(l -> !"ACTIVE".equals(rowStatus.getOrDefault(l.getAssignmentId(), "ACTIVE")));
        if (stale) {
            return new Shown("CANCELLED", STALE_NOTE);
        }
        return new Shown(r.getEffectiveOn().isAfter(facts.today()) ? "APPROVED" : "APPLIED", null);
    }

    // ------------------------------------------------------------------ detail

    @Transactional(readOnly = true)
    public RequestView view(Caller caller, UUID id) {
        MovementRequest r = requests.findById(id).orElseThrow(() -> new NotFoundException("Request not found."));
        scope.requireRead(caller, r);
        return toViews(caller, List.of(r), true).get(0);
    }

    // ------------------------------------------------------------------ list

    @Transactional(readOnly = true)
    public PageResponse<RequestView> list(
            Caller caller, Set<String> statuses, UUID schoolId, LocalDate from, LocalDate to, int page, int size) {
        Set<String> wanted = statuses == null || statuses.isEmpty() ? Set.of("PENDING", "APPROVED") : statuses;
        Set<String> stored = new HashSet<>();
        for (String s : wanted) {
            switch (s) {
                case "PENDING", "REJECTED" -> stored.add(s);
                case "CANCELLED" -> {
                    stored.add("CANCELLED");
                    stored.add("APPROVED");
                }
                case "APPROVED", "APPLIED" -> stored.add("APPROVED");
                default -> throw new com.hls.school.api.InvalidInputException("Unknown status " + s + ".");
            }
        }
        List<MovementRequest> matching = requests.findByStatusIn(stored).stream()
                .filter(r -> scope.canRead(caller, r))
                .filter(r -> schoolId == null
                        || schoolId.equals(r.getFromSchoolId())
                        || schoolId.equals(r.getToSchoolId())
                        || schoolId.equals(r.getOutgoingDestinationSchoolId()))
                .filter(r -> from == null || !r.getEffectiveOn().isBefore(from))
                .filter(r -> to == null || !r.getEffectiveOn().isAfter(to))
                .filter(r -> wanted.contains(shown(r).status()))
                .sorted(Comparator.comparing(MovementRequest::getRequestedAt).reversed())
                .toList();
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int start = Math.min(safePage * safeSize, matching.size());
        List<MovementRequest> slice = matching.subList(start, Math.min(start + safeSize, matching.size()));
        return new PageResponse<>(toViews(caller, slice, false), safePage, safeSize, matching.size());
    }

    // ------------------------------------------------------------------ vacancies and candidates

    @Transactional(readOnly = true)
    public List<Vacancy> vacancies(UUID schoolId, LocalDate on) {
        if (schoolId == null || on == null) {
            throw new com.hls.school.api.InvalidInputException("School and date are required.");
        }
        return moves.vacantPositions(schoolId, on).stream()
                .map((VacantPosition v) -> new Vacancy(v.positionId(), v.number(), v.salary()))
                .toList();
    }

    /** ACTIVE Teachers not placed on or after the date and holding no open request. */
    @Transactional(readOnly = true)
    public List<Candidate> candidates(Caller caller, UUID schoolId, LocalDate on) {
        if (schoolId == null || on == null) {
            throw new com.hls.school.api.InvalidInputException("School and date are required.");
        }
        scope.requireSchool(caller, schoolId, "School");
        Set<UUID> placed = placements.teachersAssignedDuring(on, FOREVER);
        List<TeacherInfo> active = teachers.activeTeachers().stream()
                .filter(t -> !placed.contains(t.id()))
                .toList();
        Set<UUID> busy = participants
                .findByTeacherIdInAndOpenTrue(active.stream().map(TeacherInfo::id).toList())
                .stream()
                .map(MovementParticipant::getTeacherId)
                .collect(Collectors.toSet());
        return active.stream()
                .filter(t -> !busy.contains(t.id()))
                .map(t -> new Candidate(t.id(), t.name(), t.status()))
                .sorted(Comparator.comparing(Candidate::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ------------------------------------------------------------------ building views

    private List<RequestView> toViews(Caller caller, List<MovementRequest> rows, boolean detail) {
        Set<UUID> teacherIds = new HashSet<>();
        Set<UUID> schoolIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        for (MovementRequest r : rows) {
            teacherIds.add(r.getTeacherId());
            if (r.getReplacementTeacherId() != null) {
                teacherIds.add(r.getReplacementTeacherId());
            }
            schoolIds.add(r.getFromSchoolId());
            schoolIds.add(r.getToSchoolId());
            if (r.getOutgoingDestinationSchoolId() != null) {
                schoolIds.add(r.getOutgoingDestinationSchoolId());
            }
            userIds.add(r.getRequestedBy());
            if (r.getDecidedBy() != null) {
                userIds.add(r.getDecidedBy());
            }
        }
        Map<UUID, String> teacherNames = facts.teacherNames(teacherIds);
        Map<UUID, String> schoolNames = facts.schoolNames(schoolIds);
        Map<UUID, String> userNames = new HashMap<>();
        userIds.forEach(u -> userNames.put(u, facts.userName(u)));
        List<RequestView> views = new ArrayList<>();
        for (MovementRequest r : rows) {
            Shown shown = shown(r);
            List<CheckView> lines = List.of();
            List<HistoryView> entries = List.of();
            if (detail) {
                if ("PENDING".equals(r.getStatus())) {
                    lines = checks.evaluate(Spec.of(r), r.getId()).stream().map(l -> new CheckView(l.ok(), l.text())).toList();
                }
                entries = history.findByRequestIdOrderByAtAsc(r.getId()).stream()
                        .map(h -> new HistoryView(
                                h.getAction(),
                                h.getFromStatus(),
                                h.getToStatus(),
                                h.getActorUserId() == null ? "System" : name(userNames, h.getActorUserId()),
                                h.getNote(),
                                h.getAt()))
                        .toList();
            }
            views.add(new RequestView(
                    r.getId(),
                    r.getKind(),
                    shown.status(),
                    shown.note(),
                    ref(r.getTeacherId(), teacherNames),
                    ref(r.getFromSchoolId(), schoolNames),
                    ref(r.getToSchoolId(), schoolNames),
                    position(r),
                    r.getReplacementTeacherId() == null ? null : ref(r.getReplacementTeacherId(), teacherNames),
                    r.getOutgoingDestinationSchoolId() == null ? null : ref(r.getOutgoingDestinationSchoolId(), schoolNames),
                    r.getEffectiveOn(),
                    r.getReasonCategory(),
                    r.getNote(),
                    new Ref(r.getRequestedBy(), userNames.get(r.getRequestedBy())),
                    r.getRequestedAt(),
                    r.getDecidedBy() == null ? null : new Ref(r.getDecidedBy(), userNames.get(r.getDecidedBy())),
                    r.getDecidedAt(),
                    r.getDecisionReason(),
                    r.getVersion(),
                    actions(caller, r, shown),
                    lines,
                    entries));
        }
        return views;
    }

    private String name(Map<UUID, String> names, UUID id) {
        String name = names.get(id);
        return name == null ? facts.userName(id) : name;
    }

    private static Ref ref(UUID id, Map<UUID, String> names) {
        return new Ref(id, names.get(id));
    }

    private PositionRef position(MovementRequest r) {
        UUID wanted = r.getPositionId();
        UUID school = "TRANSFER".equals(r.getKind()) ? r.getToSchoolId() : r.getFromSchoolId();
        if (wanted != null) {
            Integer number = contracts.contractOf(school, r.getEffectiveOn()).flatMap(c -> c.positions().stream()
                            .filter(p -> p.id().equals(wanted))
                            .map(p -> p.number())
                            .findFirst())
                    .orElse(null);
            if (number == null) {
                // the contract may have changed since; fall back to the Teacher's own placement rows
                number = facts.placementHistory(r.getTeacherId()).stream()
                        .filter(a -> wanted.equals(a.positionId()))
                        .map(Assignment::positionNumber)
                        .findFirst()
                        .orElse(null);
            }
            return new PositionRef(wanted, number);
        }
        return null;
    }

    /** What this caller may do with the request, never offering Approve to the requester or to a role without the grant. */
    private List<String> actions(Caller caller, MovementRequest r, Shown shown) {
        List<String> actions = new ArrayList<>();
        boolean writes = scope.canWrite(caller, r.getFromSchoolId());
        boolean requester = r.getRequestedBy().equals(caller.userId());
        boolean canChange = caller.canEdit() && writes && (requester || caller.canApprove());
        boolean canDecide = caller.canApprove() && writes && !requester;
        if ("PENDING".equals(r.getStatus())) {
            if (canDecide) {
                actions.add("APPROVE");
                actions.add("REJECT");
            }
            if (canChange) {
                actions.add("CANCEL");
                actions.add("CHANGE");
            }
        } else if ("APPROVED".equals(shown.status()) && canChange) {
            actions.add("CANCEL");
        }
        return actions;
    }
}
