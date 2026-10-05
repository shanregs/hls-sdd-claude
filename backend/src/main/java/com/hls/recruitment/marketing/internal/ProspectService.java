package com.hls.recruitment.marketing.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.PageResponse;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * School prospects: add, list, search, change and re-assign. Everything is limited to the Zones the caller manages
 * (Admin and Director see all); a prospect outside the scope is answered as not found.
 */
@Service
public class ProspectService {

    public record ProspectRequest(
            String name,
            String board,
            String address,
            UUID zoneId,
            String contactPerson,
            String designation,
            String phone,
            String email,
            Integer expectedTeachers,
            UUID ownerUserId,
            Long version) {}

    public record OwnerRequest(UUID ownerUserId) {}

    public record PersonRef(UUID userId, String name) {}

    public record ProspectRow(
            UUID id,
            String name,
            String board,
            UUID zoneId,
            String zoneName,
            PersonRef owner,
            String stage,
            String effectiveStage,
            Integer expectedTeachers,
            boolean won,
            boolean followUpOverdue,
            UUID schoolId,
            String contactPerson,
            Long version) {}

    public record HistoryRow(String kind, String from, String to, String reason, PersonRef by, Instant at) {}

    public record OwnerChange(PersonRef from, PersonRef to, PersonRef by, Instant at) {}

    public record ProspectDto(
            ProspectRow row,
            String address,
            String designation,
            String phone,
            String email,
            String lostReason,
            Instant wonAt,
            UUID placeId,
            List<HistoryRow> stageHistory,
            List<OwnerChange> ownerHistory) {}

    private final ProspectRepository prospects;
    private final StageHistoryRepository stageHistory;
    private final OwnerHistoryRepository ownerHistory;
    private final AppUserRepository users;
    private final SchoolDirectory schools;
    private final MarketingScope scope;
    private final EffectiveStageResolver stages;
    private final FollowUps followUps;
    private final ChangeRecorder changes;
    private final Clock clock;

    public ProspectService(
            ProspectRepository prospects,
            StageHistoryRepository stageHistory,
            OwnerHistoryRepository ownerHistory,
            AppUserRepository users,
            SchoolDirectory schools,
            MarketingScope scope,
            EffectiveStageResolver stages,
            FollowUps followUps,
            ChangeRecorder changes,
            Clock clock) {
        this.prospects = prospects;
        this.stageHistory = stageHistory;
        this.ownerHistory = ownerHistory;
        this.users = users;
        this.schools = schools;
        this.scope = scope;
        this.stages = stages;
        this.followUps = followUps;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProspectRow> list(
            UUID userId, Set<Role> roles, UUID zone, UUID owner, String stage, String query, int page, int size) {
        ScopeView view = scope.of(userId, roles);
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Prospect> found = prospects.findAll().stream()
                .filter(p -> scope.allowsZone(view, p.getZoneId()))
                .filter(p -> zone == null || p.getZoneId().equals(zone))
                .filter(p -> owner == null || p.getOwnerUserId().equals(owner))
                .filter(p -> term.isEmpty() || p.getName().toLowerCase(Locale.ROOT).contains(term))
                .toList();
        Map<UUID, String> effective = stages.of(found);
        List<Prospect> filtered = found.stream()
                .filter(p -> stage == null || stage.isBlank() || effective.get(p.getId()).equalsIgnoreCase(stage))
                .sorted(Comparator.comparing(Prospect::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int from = Math.min(filtered.size(), Math.max(0, page) * size);
        List<ProspectRow> rows = rowsOf(filtered.subList(from, Math.min(filtered.size(), from + size)), effective);
        return new PageResponse<>(rows, page, size, filtered.size());
    }

    @Transactional(readOnly = true)
    public ProspectDto get(UUID userId, Set<Role> roles, UUID id) {
        Prospect prospect = visible(userId, roles, id);
        return dtoOf(prospect);
    }

    @Transactional
    public ProspectDto create(UUID actor, Set<Role> roles, ProspectRequest request) {
        ScopeView view = scope.of(actor, roles);
        if (request.zoneId() == null || schools.zone(request.zoneId()).isEmpty()) {
            throw new InvalidInputException("Choose the Zone of the School.");
        }
        scope.requireZone(view, request.zoneId(), "Zone");
        Fields fields = fieldsOf(request, request.zoneId());
        if (prospects.existsByNameKey(fields.nameKey())) {
            UUID existing = prospects.findByNameKey(fields.nameKey()).map(Prospect::getId).orElse(null);
            throw new ConflictException("This School is already a prospect (existing prospect " + existing + ").");
        }
        UUID owner = actor;
        if (request.ownerUserId() != null && !request.ownerUserId().equals(actor)) {
            if (!CallerContext.isOrgWide(roles)) {
                throw new ForbiddenFieldException("Only Admin or Director can give a prospect to someone else.");
            }
            owner = requireUser(request.ownerUserId());
        }
        Prospect prospect = prospects.saveAndFlush(new Prospect(
                fields.name(),
                fields.board(),
                fields.address(),
                request.zoneId(),
                fields.nameKey(),
                fields.contactPerson(),
                fields.designation(),
                fields.phone(),
                fields.email(),
                fields.expectedTeachers(),
                owner,
                actor,
                clock.instant()));
        changes.recordLifecycle(actor, "PROSPECT", prospect.getId(), "created", fields.name());
        return dtoOf(prospect);
    }

    @Transactional
    public ProspectDto update(UUID actor, Set<Role> roles, UUID id, ProspectRequest request) {
        Prospect prospect = visible(actor, roles, id);
        StaleVersion.check(Prospect.class, id, prospect.getVersion(), request.version());
        Fields fields = fieldsOf(request, prospect.getZoneId());
        prospects.findByNameKey(fields.nameKey()).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
            throw new ConflictException("Another prospect has this name, board and Zone (existing prospect " + other.getId() + ").");
        });
        changes.record(actor, "PROSPECT", id, "name", prospect.getName(), fields.name());
        changes.record(actor, "PROSPECT", id, "board", prospect.getBoard(), fields.board());
        changes.record(actor, "PROSPECT", id, "address", prospect.getAddress(), fields.address());
        changes.record(actor, "PROSPECT", id, "contactPerson", prospect.getContactPerson(), fields.contactPerson());
        changes.record(actor, "PROSPECT", id, "expectedTeachers", prospect.getExpectedTeachers(), fields.expectedTeachers());
        prospect.change(
                fields.name(),
                fields.board(),
                fields.address(),
                fields.nameKey(),
                fields.contactPerson(),
                fields.designation(),
                fields.phone(),
                fields.email(),
                fields.expectedTeachers());
        prospects.saveAndFlush(prospect);
        return dtoOf(prospect);
    }

    /** Admin and Director only: the owner is who the incentive of a won School would later go to. */
    @Transactional
    public ProspectDto changeOwner(UUID actor, Set<Role> roles, UUID id, OwnerRequest request) {
        if (!CallerContext.isOrgWide(roles)) {
            throw new ForbiddenFieldException("Only Admin or Director can change the owner of a prospect.");
        }
        Prospect prospect = visible(actor, roles, id);
        UUID next = requireUser(request.ownerUserId());
        UUID before = prospect.getOwnerUserId();
        if (before.equals(next)) {
            throw new ConflictException("The prospect already belongs to this person.");
        }
        prospect.assignTo(next);
        prospects.saveAndFlush(prospect);
        ownerHistory.save(new OwnerHistory(id, before, next, actor, clock.instant()));
        changes.record(actor, "PROSPECT", id, "owner", before, next);
        return dtoOf(prospect);
    }

    Prospect visible(UUID userId, Set<Role> roles, UUID id) {
        Prospect prospect = prospects.findById(id).orElseThrow(() -> new NotFoundException("Prospect not found."));
        scope.requireZone(scope.of(userId, roles), prospect.getZoneId(), "Prospect");
        return prospect;
    }

    private UUID requireUser(UUID userId) {
        if (userId == null) {
            throw new InvalidInputException("Choose an owner.");
        }
        return users.findById(userId).filter(AppUser::isActive).map(AppUser::getId)
                .orElseThrow(() -> new InvalidInputException("The owner is not an active user."));
    }

    private record Fields(
            String name,
            String board,
            String address,
            String contactPerson,
            String designation,
            String phone,
            String email,
            Integer expectedTeachers,
            String nameKey) {}

    private static Fields fieldsOf(ProspectRequest r, UUID zoneId) {
        String name = Texts.required(r.name(), 200, "School name");
        String board = Texts.clean(r.board(), 60, "Board");
        if (r.expectedTeachers() != null && (r.expectedTeachers() < 1 || r.expectedTeachers() > 500)) {
            throw new InvalidInputException("The expected number of Teachers must be between 1 and 500.");
        }
        return new Fields(
                name,
                board,
                Texts.clean(r.address(), 300, "Address"),
                Texts.clean(r.contactPerson(), 160, "Contact person"),
                Texts.clean(r.designation(), 160, "Designation"),
                Texts.clean(r.phone(), 20, "Phone"),
                Texts.email(r.email()),
                r.expectedTeachers(),
                Texts.key(name) + "|" + Texts.key(board) + "|" + zoneId);
    }

    List<ProspectRow> rowsOf(Collection<Prospect> rows, Map<UUID, String> effective) {
        Set<UUID> userIds = rows.stream().map(Prospect::getOwnerUserId).collect(Collectors.toSet());
        Set<UUID> overdue = followUps.overdueProspects(rows.stream().map(Prospect::getId).toList());
        Map<UUID, String> userNames = namesOf(userIds);
        Map<UUID, String> zoneNames = schools.zones(rows.stream().map(Prospect::getZoneId).distinct().toList()).stream()
                .collect(Collectors.toMap(SchoolDirectory.ZoneInfo::id, SchoolDirectory.ZoneInfo::name));
        return rows.stream()
                .map(p -> new ProspectRow(
                        p.getId(),
                        p.getName(),
                        p.getBoard(),
                        p.getZoneId(),
                        zoneNames.getOrDefault(p.getZoneId(), "-"),
                        new PersonRef(p.getOwnerUserId(), userNames.getOrDefault(p.getOwnerUserId(), "Unknown user")),
                        p.getStage().name(),
                        effective.getOrDefault(p.getId(), p.getStage().name()),
                        p.getExpectedTeachers(),
                        p.isWon(),
                        overdue.contains(p.getId()),
                        p.getSchoolId(),
                        p.getContactPerson(),
                        p.getVersion()))
                .toList();
    }

    ProspectDto dtoOf(Prospect p) {
        Map<UUID, String> effective = stages.of(List.of(p));
        ProspectRow row = rowsOf(List.of(p), effective).get(0);
        List<StageHistory> stage = stageHistory.findByProspectIdOrderByChangedAtAsc(p.getId());
        List<OwnerHistory> owner = ownerHistory.findByProspectIdOrderByChangedAtAsc(p.getId());
        Set<UUID> ids = new java.util.HashSet<>();
        stage.forEach(h -> ids.add(h.getChangedBy()));
        owner.forEach(h -> {
            ids.add(h.getChangedBy());
            ids.add(h.getToOwner());
            if (h.getFromOwner() != null) ids.add(h.getFromOwner());
        });
        Map<UUID, String> names = namesOf(ids);
        return new ProspectDto(
                row,
                p.getAddress(),
                p.getDesignation(),
                p.getPhone(),
                p.getEmail(),
                p.getLostReason(),
                p.getWonAt(),
                p.getPlaceId(),
                stage.stream()
                        .map(h -> new HistoryRow(
                                h.getKind(),
                                h.getFromStage() == null ? null : h.getFromStage().name(),
                                h.getToStage().name(),
                                h.getReason(),
                                person(h.getChangedBy(), names),
                                h.getChangedAt()))
                        .toList(),
                owner.stream()
                        .map(h -> new OwnerChange(
                                h.getFromOwner() == null ? null : person(h.getFromOwner(), names),
                                person(h.getToOwner(), names),
                                person(h.getChangedBy(), names),
                                h.getChangedAt()))
                        .toList());
    }

    private static PersonRef person(UUID id, Map<UUID, String> names) {
        return new PersonRef(id, names.getOrDefault(id, "Unknown user"));
    }

    Map<UUID, String> namesOf(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
    }
}
