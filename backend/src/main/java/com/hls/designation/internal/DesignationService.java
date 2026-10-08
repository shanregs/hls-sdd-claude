package com.hls.designation.internal;

import com.hls.designation.api.DesignationDirectory;
import com.hls.designation.api.HolderCounter;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.StaleVersion;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The list of designations (spec 005a US1): add, rename, retire, reactivate; never delete. */
@Service
public class DesignationService implements DesignationDirectory {

    public static final int MAX_NAME = 80;

    public record DesignationView(UUID id, String name, Kind kind, boolean retired, long holders, Long version) {}

    public record Summary(
            long teachersMissingDesignation,
            long managersMissingDesignation,
            long managersMissingJoiningDate,
            long managersMissingExitDate) {}

    private final DesignationRepository repository;
    // resolved on use: the counters live in modules that themselves call this one, so injecting them eagerly is a cycle
    private final ObjectProvider<HolderCounter> counterProvider;
    private final ChangeRecorder changes;
    private final Clock clock;

    public DesignationService(
            DesignationRepository repository,
            ObjectProvider<HolderCounter> counterProvider,
            ChangeRecorder changes,
            Clock clock) {
        this.repository = repository;
        this.counterProvider = counterProvider;
        this.changes = changes;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<DesignationView> list(Kind kind, Boolean retired) {
        Map<UUID, Long> holders = holders();
        return repository.findAllByOrderByKindAscNameAsc().stream()
                .filter(d -> kind == null || d.getKind() == kind)
                .filter(d -> retired == null || d.isRetired() == retired)
                .map(d -> view(d, holders))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DesignationView> options(Kind kind) {
        if (kind == null) {
            throw new InvalidInputException("The kind (TEACHER or MANAGER) is required.");
        }
        return list(kind, false);
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        long teacherMissing = 0;
        long managerMissing = 0;
        long managerJoining = 0;
        long managerExit = 0;
        for (HolderCounter counter : counterProvider.orderedStream().toList()) {
            if (counter.kind() == Kind.TEACHER) {
                teacherMissing += counter.missingDesignation();
            } else {
                managerMissing += counter.missingDesignation();
                managerJoining += counter.missingJoiningDate();
                managerExit += counter.missingExitDate();
            }
        }
        return new Summary(teacherMissing, managerMissing, managerJoining, managerExit);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DesignationInfo> find(UUID designationId) {
        if (designationId == null) {
            return Optional.empty();
        }
        return repository.findById(designationId).map(DesignationService::info);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DesignationInfo> findByName(Kind kind, String name) {
        if (kind == null || name == null || name.isBlank()) {
            return Optional.empty();
        }
        return repository.findByKindAndKey(kind.name(), normalizeKey(name)).stream()
                .findFirst()
                .map(DesignationService::info);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, DesignationInfo> findAll(Collection<UUID> designationIds) {
        if (designationIds.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(designationIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(Designation::getId, DesignationService::info));
    }

    @Override
    @Transactional
    public DesignationInfo assign(UUID designationId, Kind kind) {
        if (designationId == null) {
            throw new InvalidInputException("A designation is required.");
        }
        Designation designation = repository
                .findById(designationId)
                .orElseThrow(() -> new InvalidInputException("That designation does not exist."));
        if (designation.getKind() != kind) {
            throw new InvalidInputException(
                    "That designation is not for " + (kind == Kind.MANAGER ? "Managers." : "Teachers."));
        }
        if (designation.isRetired()) {
            throw new InvalidInputException("That designation is retired and cannot be chosen.");
        }
        repository.markHeld(designationId);
        return info(designation);
    }

    // ----------------------------------------------------------------- commands

    @Transactional
    public DesignationView create(UUID actor, String rawName, Kind kind) {
        if (kind == null) {
            throw new InvalidInputException("Choose whether the designation is for Teachers or Managers.");
        }
        String name = cleanName(rawName);
        requireUnique(kind, name, null);
        Designation designation = repository.saveAndFlush(new Designation(name, kind, actor, clock.instant()));
        changes.recordLifecycle(actor, "DESIGNATION", designation.getId(), "created", name + " (" + kind + ")");
        return view(designation, Map.of());
    }

    @Transactional
    public DesignationView update(UUID actor, UUID id, String rawName, Kind kind, Boolean retired, Long version) {
        Designation designation =
                repository.findById(id).orElseThrow(() -> new NotFoundException("Designation not found."));
        StaleVersion.check(Designation.class, id, designation.getVersion(), version);
        String name = rawName == null ? designation.getName() : cleanName(rawName);
        Kind newKind = kind == null ? designation.getKind() : kind;
        if (newKind != designation.getKind() && designation.isHeldEver()) {
            throw new ConflictException("This designation has been held by people, so its kind cannot change.");
        }
        requireUnique(newKind, name, designation.getId());
        boolean newRetired = retired == null ? designation.isRetired() : retired;
        changes.record(actor, "DESIGNATION", id, "name", designation.getName(), name);
        changes.record(actor, "DESIGNATION", id, "kind", designation.getKind(), newKind);
        changes.record(actor, "DESIGNATION", id, "retired", designation.isRetired(), newRetired);
        designation.rename(name, clock.instant());
        designation.setKind(newKind, clock.instant());
        designation.setRetired(newRetired, clock.instant());
        repository.saveAndFlush(designation);
        return view(designation, holders());
    }

    // ------------------------------------------------------------------ helpers

    private Map<UUID, Long> holders() {
        Map<UUID, Long> total = new HashMap<>();
        for (HolderCounter counter : counterProvider.orderedStream().toList()) {
            counter.holdersByDesignation().forEach((id, n) -> total.merge(id, n, Long::sum));
        }
        return total;
    }

    private static DesignationView view(Designation d, Map<UUID, Long> holders) {
        return new DesignationView(
                d.getId(), d.getName(), d.getKind(), d.isRetired(), holders.getOrDefault(d.getId(), 0L), d.getVersion());
    }

    private static DesignationInfo info(Designation d) {
        return new DesignationInfo(d.getId(), d.getName(), d.getKind(), d.isRetired());
    }

    private void requireUnique(Kind kind, String name, UUID selfId) {
        boolean taken = repository.findByKindAndKey(kind.name(), normalizeKey(name)).stream()
                .anyMatch(d -> !d.getId().equals(selfId));
        if (taken) {
            throw new ConflictException("There is already a designation named \"" + name + "\" for "
                    + (kind == Kind.MANAGER ? "Managers." : "Teachers."));
        }
    }

    static String cleanName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidInputException("The designation name is required.");
        }
        String name = raw.trim().replaceAll("\\s+", " ");
        if (name.length() > MAX_NAME) {
            throw new InvalidInputException("The designation name must be at most " + MAX_NAME + " characters.");
        }
        return name;
    }

    static String normalizeKey(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
