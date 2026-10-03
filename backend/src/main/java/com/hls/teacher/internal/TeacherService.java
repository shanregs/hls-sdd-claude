package com.hls.teacher.internal;

import com.hls.identity.user.Role;
import com.hls.identity.user.UserAdminService;
import com.hls.identity.user.UserAdminService.UserWithRoles;
import com.hls.organization.api.ManagerQueries;
import com.hls.organization.api.ManagerQueries.ManagerRef;
import com.hls.organization.api.ScopeQueries;
import com.hls.organization.api.ScopeView;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.school.api.StaleVersion;
import com.hls.teacher.api.TeacherScopeQueries.TeacherScope;
import com.hls.teacher.api.TeacherView;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Teacher CRUD, status machine and account link (spec 005 US4), always through the caller's scope. */
@Service
public class TeacherService {

    public record Contact(String name, String phone, String email, String address, Long version) {}

    public record NewTeacher(
            String name, String phone, String email, String address, TeacherStatus status, UUID userId) {}

    private static final UUID NO_ID = new UUID(0L, 0L);

    private final TeacherRepository teacherRepository;
    private final TeacherPlacementRepository placementRepository;
    private final TeacherPlacementService placementService;
    private final TeacherScopeService scopeService;
    private final ScopeQueries scopeQueries;
    private final ManagerQueries managerQueries;
    private final SchoolDirectory schoolDirectory;
    private final UserAdminService userAdminService;
    private final ChangeRecorder changes;
    private final Clock clock;

    public TeacherService(
            TeacherRepository teacherRepository,
            TeacherPlacementRepository placementRepository,
            TeacherPlacementService placementService,
            TeacherScopeService scopeService,
            ScopeQueries scopeQueries,
            ManagerQueries managerQueries,
            SchoolDirectory schoolDirectory,
            UserAdminService userAdminService,
            ChangeRecorder changes,
            Clock clock) {
        this.teacherRepository = teacherRepository;
        this.placementRepository = placementRepository;
        this.placementService = placementService;
        this.scopeService = scopeService;
        this.scopeQueries = scopeQueries;
        this.managerQueries = managerQueries;
        this.schoolDirectory = schoolDirectory;
        this.userAdminService = userAdminService;
        this.changes = changes;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public Page<TeacherView> list(
            UUID userId,
            Set<Role> roles,
            String query,
            TeacherStatus status,
            UUID schoolId,
            Pageable pageable) {
        TeacherScope scope = scopeService.teacherScope(userId, roles);
        Set<UUID> restrict = scope.orgWide() ? null : new HashSet<>(scope.teacherIds());
        if (schoolId != null) {
            Set<UUID> atSchool =
                    new HashSet<>(placementRepository.teacherIdsAtSchoolsOn(List.of(schoolId), LocalDate.now(clock)));
            if (restrict == null) {
                restrict = atSchool;
            } else {
                restrict.retainAll(atSchool);
            }
        }
        if (restrict != null && restrict.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Page<Teacher> page = teacherRepository.search(
                query == null ? "" : query.trim(),
                status != null,
                status == null ? TeacherStatus.ACTIVE : status,
                restrict != null,
                restrict == null ? Set.of(NO_ID) : restrict,
                pageable);
        List<TeacherView> views = viewsOf(page.getContent(), null);
        return new PageImpl<>(views, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TeacherView get(UUID userId, Set<Role> roles, UUID id) {
        Teacher teacher = visible(userId, roles, id);
        Set<UUID> schoolFilter = null;
        if (!CallerContext.isOrgWide(roles)) {
            ScopeView scope = scopeQueries.scopeOf(userId, roles);
            schoolFilter = scope.schoolIds();
        }
        return viewsOf(List.of(teacher), schoolFilter).get(0);
    }

    /** The caller's own record, without placement history of other people; 404 when not set up yet. */
    @Transactional(readOnly = true)
    public TeacherView mine(UUID userId) {
        Teacher teacher = teacherRepository
                .findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Your profile has not been set up yet."));
        TeacherView full = viewsOf(List.of(teacher), Set.of()).get(0);
        // a Teacher sees their own details and current School, not the placement history or Manager
        return new TeacherView(
                full.id(),
                full.name(),
                full.phone(),
                full.email(),
                full.address(),
                full.status(),
                full.statusEffectiveOn(),
                List.of(),
                full.userId(),
                full.version(),
                full.school(),
                null,
                null,
                null);
    }

    // ----------------------------------------------------------------- commands

    @Transactional
    public TeacherView create(UUID actor, NewTeacher request) {
        String name = requireName(request.name());
        TeacherStatus status = request.status() == null ? TeacherStatus.IN_TRAINING : request.status();
        if (status == TeacherStatus.EXITED) {
            throw new InvalidInputException("A new Teacher cannot start as exited.");
        }
        Teacher teacher = new Teacher(
                name,
                clean(request.phone(), 20, "Phone"),
                clean(request.email(), 200, "Email"),
                clean(request.address(), 2000, "Address"),
                status,
                LocalDate.now(clock),
                clock.instant());
        if (request.userId() != null) {
            checkLinkable(request.userId(), null);
            teacher.setUserId(request.userId());
        }
        teacher = teacherRepository.saveAndFlush(teacher);
        changes.recordLifecycle(actor, "TEACHER", teacher.getId(), "created", name);
        return viewsOf(List.of(teacher), null).get(0);
    }

    /** Edits contact details. A caller who is not Admin/Director (a Manager) cannot change the name. */
    @Transactional
    public TeacherView update(UUID actor, Set<Role> roles, UUID id, Contact contact) {
        Teacher teacher = visible(actor, roles, id);
        StaleVersion.check(Teacher.class, id, teacher.getVersion(), contact.version());
        String name = requireName(contact.name());
        if (!CallerContext.isOrgWide(roles) && !Objects.equals(name, teacher.getName())) {
            throw new ForbiddenFieldException("Managers can edit only a Teacher's contact details.");
        }
        String phone = clean(contact.phone(), 20, "Phone");
        String email = clean(contact.email(), 200, "Email");
        String address = clean(contact.address(), 2000, "Address");
        changes.record(actor, "TEACHER", id, "name", teacher.getName(), name);
        changes.record(actor, "TEACHER", id, "phone", teacher.getPhone(), phone);
        changes.record(actor, "TEACHER", id, "email", teacher.getEmail(), email);
        changes.record(actor, "TEACHER", id, "address", teacher.getAddress(), address);
        teacher.setName(name);
        teacher.setPhone(phone);
        teacher.setEmail(email);
        teacher.setAddress(address);
        teacher.touch(clock.instant());
        teacherRepository.saveAndFlush(teacher);
        return viewsOf(List.of(teacher), null).get(0);
    }

    @Transactional
    public TeacherView changeStatus(UUID actor, UUID id, TeacherStatus next, LocalDate effectiveOn) {
        Teacher teacher = teacherRepository.findById(id).orElseThrow(() -> new NotFoundException("Teacher not found."));
        if (next == null) {
            throw new InvalidInputException("A status is required.");
        }
        TeacherStatus before = teacher.getStatus();
        if (!before.canMoveTo(next)) {
            throw new ConflictException("A Teacher cannot move from " + before + " to " + next + ".");
        }
        LocalDate on = effectiveOn == null ? LocalDate.now(clock) : effectiveOn;
        if (next == TeacherStatus.EXITED) {
            placementService.endForExit(id, on);
            if (teacher.getUserId() != null) {
                changes.record(actor, "TEACHER", id, "userId", teacher.getUserId(), null);
                teacher.setUserId(null);
            }
        }
        teacher.moveTo(next, on);
        teacher.touch(clock.instant());
        teacherRepository.saveAndFlush(teacher);
        changes.record(actor, "TEACHER", id, "status", before, next);
        return viewsOf(List.of(teacher), null).get(0);
    }

    @Transactional
    public TeacherView linkUser(UUID actor, UUID id, UUID userId) {
        Teacher teacher = teacherRepository.findById(id).orElseThrow(() -> new NotFoundException("Teacher not found."));
        if (teacher.getStatus() == TeacherStatus.EXITED) {
            throw new ConflictException("An exited Teacher cannot be linked to an account.");
        }
        UUID before = teacher.getUserId();
        if (userId != null) {
            checkLinkable(userId, id);
        }
        teacher.setUserId(userId);
        teacher.touch(clock.instant());
        teacherRepository.saveAndFlush(teacher);
        changes.record(actor, "TEACHER", id, "userId", before, userId);
        return viewsOf(List.of(teacher), null).get(0);
    }

    // ------------------------------------------------------------------ helpers

    private Teacher visible(UUID userId, Set<Role> roles, UUID id) {
        Teacher teacher = teacherRepository.findById(id).orElseThrow(() -> new NotFoundException("Teacher not found."));
        if (!scopeService.teacherScope(userId, roles).allows(id)) {
            throw new NotFoundException("Teacher not found.");
        }
        return teacher;
    }

    private void checkLinkable(UUID userId, UUID teacherId) {
        UserWithRoles user =
                userAdminService.find(userId).orElseThrow(() -> new InvalidInputException("User not found."));
        if (!user.roles().contains(Role.TEACHER)) {
            throw new InvalidInputException("Only a user who holds the Teacher role can be linked to a Teacher.");
        }
        teacherRepository.findByUserId(userId).ifPresent(other -> {
            if (!other.getId().equals(teacherId)) {
                throw new ConflictException("This user is already linked to another Teacher.");
            }
        });
    }

    /** Builds views; {@code schoolFilter} (non-null for a Manager) limits the placement history shown. */
    List<TeacherView> viewsOf(Collection<Teacher> teachers, Set<UUID> schoolFilter) {
        if (teachers.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        List<UUID> ids = teachers.stream().map(Teacher::getId).toList();
        Map<UUID, TeacherPlacement> current = placementRepository.inEffectOn(ids, today).stream()
                .collect(Collectors.toMap(TeacherPlacement::getTeacherId, p -> p, (a, b) -> a));
        Map<UUID, TeacherPlacement> pending = placementRepository.scheduledAfter(ids, today).stream()
                .collect(Collectors.toMap(TeacherPlacement::getTeacherId, p -> p, (a, b) -> a));
        Map<UUID, List<TeacherPlacement>> history = new HashMap<>();
        if (schoolFilter != null || teachers.size() == 1) {
            for (UUID id : ids) {
                history.put(id, placementRepository.findByTeacherIdOrderByStartsOnDesc(id));
            }
        }
        Set<UUID> schoolIds = new HashSet<>();
        current.values().forEach(p -> schoolIds.add(p.getSchoolId()));
        pending.values().forEach(p -> schoolIds.add(p.getSchoolId()));
        history.values().forEach(rows -> rows.forEach(p -> schoolIds.add(p.getSchoolId())));
        Map<UUID, String> schoolNames = schoolDirectory.schools(schoolIds).stream()
                .collect(Collectors.toMap(SchoolInfo::id, SchoolInfo::name));
        Map<UUID, ManagerRef> managers = managerQueries.managersOfSchools(
                current.values().stream().map(TeacherPlacement::getSchoolId).collect(Collectors.toSet()));

        return teachers.stream()
                .map(t -> {
                    TeacherPlacement cur = current.get(t.getId());
                    TeacherPlacement pen = pending.get(t.getId());
                    ManagerRef manager = cur == null ? null : managers.get(cur.getSchoolId());
                    List<TeacherView.PlacementRow> rows = history.containsKey(t.getId())
                            ? history.get(t.getId()).stream()
                                    .filter(p -> schoolFilter == null || schoolFilter.contains(p.getSchoolId()))
                                    .map(p -> new TeacherView.PlacementRow(
                                            p.getSchoolId(),
                                            schoolNames.getOrDefault(p.getSchoolId(), "?"),
                                            p.getStartsOn(),
                                            p.getEndsOn(),
                                            p.getStatus().name(),
                                            true))
                                    .toList()
                            : null;
                    return new TeacherView(
                            t.getId(),
                            t.getName(),
                            t.getPhone(),
                            t.getEmail(),
                            t.getAddress(),
                            t.getStatus().name(),
                            t.getStatusEffectiveOn(),
                            t.getStatus().allowedNext().stream().map(Enum::name).sorted().toList(),
                            t.getUserId(),
                            t.getVersion(),
                            cur == null
                                    ? null
                                    : new TeacherView.SchoolRef(
                                            cur.getSchoolId(), schoolNames.getOrDefault(cur.getSchoolId(), "?")),
                            manager == null ? null : new TeacherView.ManagerRef(manager.id(), manager.displayName()),
                            pen == null
                                    ? null
                                    : new TeacherView.PendingPlacement(
                                            pen.getSchoolId(),
                                            schoolNames.getOrDefault(pen.getSchoolId(), "?"),
                                            pen.getStartsOn()),
                            rows);
                })
                .toList();
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidInputException("Teacher name is required.");
        }
        return clean(name, 160, "Teacher name");
    }

    private static String clean(String value, int max, String label) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new InvalidInputException(label + " must be at most " + max + " characters.");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
