package com.hls.organization.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.designation.api.DesignationDirectory;
import com.hls.designation.api.DesignationDirectory.DesignationInfo;
import com.hls.designation.api.DesignationDirectory.Kind;
import com.hls.designation.api.EmployeeIds;
import com.hls.designation.api.EmployeeIds.PersonKind;
import com.hls.identity.user.UserAdminService;
import com.hls.organization.api.ManagerQueries.ManagerEmployment;
import com.hls.organization.api.ManagerView.DesignationRef;
import com.hls.organization.api.ManagerView.DesignationRow;
import com.hls.organization.api.ManagerView.Employment;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A Manager's employment details (spec 005a US2): employee id, joining and exit dates, and the append-only
 * designation history. Every write takes the Manager row lock, checks the version and audits each changed field.
 */
@Service
public class ManagerEmploymentService {

    static final int MAX_NOTICE_DAYS = 90;

    /** Insertion order within one effective date; a row not yet re-read from the database counts as the latest. */
    private static final Comparator<ManagerDesignation> IN_EFFECT_ORDER = Comparator.comparing(
                    ManagerDesignation::getEffectiveOn)
            .thenComparing(ManagerDesignation::getSeq, Comparator.nullsLast(Comparator.naturalOrder()));

    private final ManagerRepository managerRepository;
    private final ManagerDesignationRepository designationRows;
    private final DesignationDirectory designations;
    private final EmployeeIds employeeIds;
    private final BusinessDate businessDate;
    private final UserAdminService userAdminService;
    private final ChangeRecorder changes;
    private final Clock clock;

    public ManagerEmploymentService(
            ManagerRepository managerRepository,
            ManagerDesignationRepository designationRows,
            DesignationDirectory designations,
            EmployeeIds employeeIds,
            BusinessDate businessDate,
            UserAdminService userAdminService,
            ChangeRecorder changes,
            Clock clock) {
        this.managerRepository = managerRepository;
        this.designationRows = designationRows;
        this.designations = designations;
        this.employeeIds = employeeIds;
        this.businessDate = businessDate;
        this.userAdminService = userAdminService;
        this.changes = changes;
        this.clock = clock;
    }

    // ----------------------------------------------------------------- pure rule

    /** The row in effect on {@code date}: greatest (effective date, insertion order) with effective date on or before it. */
    static Optional<ManagerDesignation> inEffectOn(Collection<ManagerDesignation> rows, LocalDate date) {
        return rows.stream().filter(r -> !r.getEffectiveOn().isAfter(date)).max(IN_EFFECT_ORDER);
    }

    // -------------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public Optional<UUID> designationOn(UUID managerId, LocalDate date) {
        return inEffectOn(designationRows.findByManagerId(managerId), date).map(ManagerDesignation::getDesignationId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UUID> designationsOn(Collection<UUID> managerIds, LocalDate date) {
        if (managerIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> result = new HashMap<>();
        rowsByManager(managerIds).forEach((id, rows) ->
                inEffectOn(rows, date).ifPresent(r -> result.put(id, r.getDesignationId())));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<UUID, ManagerEmployment> employment(Collection<UUID> managerIds) {
        if (managerIds.isEmpty()) {
            return Map.of();
        }
        return managerRepository.findAllById(managerIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(
                        Manager::getId,
                        m -> new ManagerEmployment(
                                m.getId(), m.getEmployeeId(), m.getJoiningDate(), m.getExitDate(), m.isActive())));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> holderCountsByDesignation(Collection<UUID> designationIds) {
        Map<UUID, Long> all = holdersToday();
        if (designationIds == null || designationIds.isEmpty()) {
            return all;
        }
        Map<UUID, Long> result = new HashMap<>();
        for (UUID id : designationIds) {
            Long n = all.get(id);
            if (n != null) {
                result.put(id, n);
            }
        }
        return result;
    }

    /** Managers whose designation today is the key, over every Manager. */
    Map<UUID, Long> holdersToday() {
        LocalDate today = businessDate.today();
        Map<UUID, List<ManagerDesignation>> byManager = designationRows.findAll().stream()
                .collect(Collectors.groupingBy(ManagerDesignation::getManagerId));
        Map<UUID, Long> counts = new HashMap<>();
        byManager.values().forEach(rows -> inEffectOn(rows, today)
                .ifPresent(r -> counts.merge(r.getDesignationId(), 1L, Long::sum)));
        return counts;
    }

    /** Counts for the missing-details summary. */
    record Missing(long noDesignation, long noJoiningDate, long inactiveNoExit) {}

    Missing missing() {
        List<Manager> managers = managerRepository.findAll();
        Map<UUID, Long> withRows = designationRows.findAll().stream()
                .collect(Collectors.groupingBy(ManagerDesignation::getManagerId, Collectors.counting()));
        long noDesignation = managers.stream().filter(m -> !withRows.containsKey(m.getId())).count();
        long noJoining = managers.stream().filter(m -> m.getJoiningDate() == null).count();
        long noExit = managers.stream().filter(m -> !m.isActive() && m.getExitDate() == null).count();
        return new Missing(noDesignation, noJoining, noExit);
    }

    /** The employment part of the views of the given Managers, in a fixed number of queries. */
    @Transactional(readOnly = true)
    public Map<UUID, Employment> employmentViews(Collection<Manager> managers, boolean withHistory) {
        if (managers.isEmpty()) {
            return Map.of();
        }
        LocalDate today = businessDate.today();
        List<UUID> ids = managers.stream().map(Manager::getId).toList();
        Map<UUID, List<ManagerDesignation>> rows = rowsByManager(ids);
        Map<UUID, DesignationInfo> infos = designations.findAll(rows.values().stream()
                .flatMap(List::stream)
                .map(ManagerDesignation::getDesignationId)
                .collect(Collectors.toSet()));
        Map<UUID, Employment> result = new HashMap<>();
        for (Manager m : managers) {
            List<ManagerDesignation> mine = rows.getOrDefault(m.getId(), List.of());
            DesignationRef current = inEffectOn(mine, today)
                    .map(r -> ref(infos.get(r.getDesignationId()), r.getDesignationId()))
                    .orElse(null);
            List<String> missing = new ArrayList<>();
            if (mine.isEmpty()) {
                missing.add("DESIGNATION");
            }
            if (m.getJoiningDate() == null) {
                missing.add("JOINING_DATE");
            }
            if (!m.isActive() && m.getExitDate() == null) {
                missing.add("EXIT_DATE");
            }
            List<DesignationRow> history = null;
            if (withHistory) {
                history = mine.stream()
                        .sorted(IN_EFFECT_ORDER.reversed())
                        .map(r -> new DesignationRow(
                                r.getDesignationId(),
                                nameOf(infos.get(r.getDesignationId())),
                                r.getEffectiveOn(),
                                r.getRecordedAt()))
                        .toList();
            }
            result.put(
                    m.getId(),
                    new Employment(m.getEmployeeId(), m.getJoiningDate(), m.getExitDate(), current, history, missing));
        }
        return result;
    }

    // ----------------------------------------------------------------- commands

    /** Replaces employee id, joining date and exit date (null clears), checking every rule of FR-005 to FR-009. */
    @Transactional
    public void updateEmployment(
            UUID actor, UUID managerId, String employeeId, LocalDate joiningDate, LocalDate exitDate, Long version) {
        Manager manager = lock(managerId);
        StaleVersion.check(Manager.class, managerId, manager.getVersion(), version);
        if (joiningDate != null && exitDate != null && joiningDate.isAfter(exitDate)) {
            throw new InvalidInputException("The joining date cannot be after the exit date.");
        }
        if (exitDate != null) {
            if (manager.isActive()) {
                throw new InvalidInputException("An exit date can be recorded only for an inactive Manager.");
            }
            if (!Objects.equals(exitDate, manager.getExitDate())
                    && exitDate.isAfter(businessDate.today().plusDays(MAX_NOTICE_DAYS))) {
                throw new InvalidInputException(
                        "The exit date cannot be more than " + MAX_NOTICE_DAYS + " days after today.");
            }
        }
        String newId = employeeIds.claim(PersonKind.MANAGER, managerId, displayName(manager), employeeId);
        changes.record(actor, "MANAGER", managerId, "employeeId", manager.getEmployeeId(), newId);
        changes.record(actor, "MANAGER", managerId, "joiningDate", manager.getJoiningDate(), joiningDate);
        changes.record(actor, "MANAGER", managerId, "exitDate", manager.getExitDate(), exitDate);
        manager.setEmployment(newId, joiningDate, exitDate, clock.instant());
        managerRepository.saveAndFlush(manager);
    }

    /** Appends a designation row effective on {@code effectiveOn}; earlier rows are never changed. */
    @Transactional
    public void appendDesignation(UUID actor, UUID managerId, UUID designationId, LocalDate effectiveOn) {
        Manager manager = lock(managerId);
        if (designationId == null) {
            throw new InvalidInputException("A designation is required; a Manager's designation cannot be cleared.");
        }
        if (effectiveOn == null) {
            throw new InvalidInputException("The effective date is required.");
        }
        LocalDate today = businessDate.today();
        List<ManagerDesignation> rows = designationRows.findByManagerId(managerId);
        if (rows.isEmpty()) {
            if (manager.getJoiningDate() != null) {
                if (effectiveOn.isBefore(manager.getJoiningDate())) {
                    throw new InvalidInputException("The first designation cannot start before the joining date.");
                }
            } else if (effectiveOn.isAfter(today)) {
                throw new InvalidInputException(
                        "Without a joining date, the first designation cannot start after today.");
            }
        } else if (effectiveOn.isBefore(today.withDayOfMonth(1))) {
            throw new InvalidInputException("A change cannot take effect before the first day of the current month.");
        }
        Optional<ManagerDesignation> inEffect = inEffectOn(rows, effectiveOn);
        if (inEffect.isPresent() && inEffect.get().getDesignationId().equals(designationId)) {
            throw new InvalidInputException("The Manager already holds that designation on that date.");
        }
        DesignationInfo chosen = designations.assign(designationId, Kind.MANAGER);
        Optional<ManagerDesignation> latest = rows.stream().max(IN_EFFECT_ORDER);
        String before = latest.map(r -> nameOf(designations.find(r.getDesignationId()).orElse(null))).orElse(null);
        designationRows.saveAndFlush(
                new ManagerDesignation(managerId, designationId, effectiveOn, actor, clock.instant()));
        changes.record(actor, "MANAGER", managerId, "designation", before, chosen.name());
        changes.record(actor, "MANAGER", managerId, "designationEffectiveOn", null, effectiveOn);
        manager.touch(clock.instant());
        managerRepository.saveAndFlush(manager);
    }

    /**
     * The Manager became inactive or active again (account events): an inactive Manager without an exit date gets
     * today's date, an active one loses it; the previous value stays in the audit log.
     */
    @Transactional
    public void accountStateChanged(UUID actor, Manager manager) {
        LocalDate before = manager.getExitDate();
        LocalDate today = businessDate.today();
        // never before the joining date, which the database also refuses (a planned joiner who leaves early)
        LocalDate defaultExit = manager.getJoiningDate() != null && manager.getJoiningDate().isAfter(today)
                ? manager.getJoiningDate()
                : today;
        LocalDate after = manager.isActive() ? null : (before != null ? before : defaultExit);
        if (Objects.equals(before, after)) {
            return;
        }
        changes.record(actor, "MANAGER", manager.getId(), "exitDate", before, after);
        manager.setEmployment(manager.getEmployeeId(), manager.getJoiningDate(), after, clock.instant());
        managerRepository.save(manager);
    }

    // ------------------------------------------------------------------ helpers

    private Manager lock(UUID managerId) {
        List<Manager> locked = managerRepository.lockAll(List.of(managerId));
        if (locked.isEmpty()) {
            throw new NotFoundException("Manager not found.");
        }
        return locked.get(0);
    }

    private Map<UUID, List<ManagerDesignation>> rowsByManager(Collection<UUID> managerIds) {
        return designationRows.findByManagerIdIn(managerIds.stream().distinct().toList()).stream()
                .collect(Collectors.groupingBy(ManagerDesignation::getManagerId));
    }

    private String displayName(Manager manager) {
        return userAdminService
                .find(manager.getUserId())
                .map(u -> u.user().getDisplayName())
                .orElse("A Manager");
    }

    private static DesignationRef ref(DesignationInfo info, UUID id) {
        return info == null ? new DesignationRef(id, "?", false) : new DesignationRef(id, info.name(), info.retired());
    }

    private static String nameOf(DesignationInfo info) {
        return info == null ? "?" : info.name();
    }
}
