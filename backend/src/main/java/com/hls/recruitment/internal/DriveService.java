package com.hls.recruitment.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.school.api.CallerContext;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Campus drives: scheduled visits to colleges. Anyone with recruitment access may schedule one; a Zone Manager may
 * change only a drive they scheduled or are an interviewer on (spec 016 Clarifications), Admin and Director any.
 */
@Service
public class DriveService {

    public record DriveRequest(
            UUID collegeId,
            List<LocalDate> dates,
            String venue,
            String season,
            List<UUID> interviewerUserIds,
            Long version) {}

    public record StatusRequest(String status, String reason, Long version) {}

    public record CollegeRef(UUID id, String name, String city) {}

    public record PersonRef(UUID userId, String name) {}

    public record DriveDto(
            UUID id,
            CollegeRef college,
            String season,
            String venue,
            String status,
            String cancelReason,
            List<LocalDate> dates,
            List<PersonRef> interviewers,
            UUID scheduledBy,
            long candidates,
            Map<String, Long> outcomes,
            boolean heldNoCandidates,
            Long version) {}

    private final CampusDriveRepository drives;
    private final CollegeRepository colleges;
    private final CandidateRepository candidates;
    private final AppUserRepository users;
    private final com.hls.identity.user.RoleAssignmentRepository roleAssignments;
    private final ChangeRecorder changes;
    private final Clock clock;

    public DriveService(
            CampusDriveRepository drives,
            CollegeRepository colleges,
            CandidateRepository candidates,
            AppUserRepository users,
            com.hls.identity.user.RoleAssignmentRepository roleAssignments,
            ChangeRecorder changes,
            Clock clock) {
        this.drives = drives;
        this.colleges = colleges;
        this.candidates = candidates;
        this.users = users;
        this.roleAssignments = roleAssignments;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DriveDto> list(UUID userId, LocalDate from, LocalDate to, String season, boolean mine) {
        List<CampusDrive> rows = drives.findAll().stream()
                .filter(d -> from == null || d.getDates().stream().anyMatch(x -> !x.isBefore(from)))
                .filter(d -> to == null || d.getDates().stream().anyMatch(x -> !x.isAfter(to)))
                .filter(d -> season == null || season.isBlank() || season.equalsIgnoreCase(d.getSeasonLabel()))
                .filter(d -> !mine || d.getScheduledBy().equals(userId) || d.getInterviewers().contains(userId))
                .sorted(Comparator.comparing((CampusDrive d) -> firstDate(d)).thenComparing(CampusDrive::getId))
                .toList();
        return views(rows);
    }

    /** Active Admin, Director and Zone Manager users: the people who can attend a drive as interviewers. */
    @Transactional(readOnly = true)
    public List<PersonRef> interviewerChoices() {
        Set<UUID> ids = new java.util.HashSet<>();
        for (Role role : List.of(Role.ADMIN, Role.DIRECTOR, Role.MANAGER)) {
            ids.addAll(roleAssignments.userIdsWithRole(role));
        }
        return users.findAllById(ids).stream()
                .filter(AppUser::isActive)
                .map(u -> new PersonRef(u.getId(), u.getDisplayName()))
                .sorted(Comparator.comparing(PersonRef::name))
                .toList();
    }

    @Transactional(readOnly = true)
    public DriveDto get(UUID id) {
        return views(List.of(find(id))).get(0);
    }

    @Transactional
    public DriveDto create(UUID actor, DriveRequest request) {
        College college = colleges.findById(request.collegeId() == null ? new UUID(0, 0) : request.collegeId())
                .orElseThrow(() -> new InvalidInputException("Choose a college."));
        Set<LocalDate> dates = datesOf(request.dates());
        Set<UUID> interviewers = interviewersOf(request.interviewerUserIds());
        CampusDrive drive = drives.saveAndFlush(new CampusDrive(
                college.getId(),
                Texts.clean(request.season(), 40, "Season"),
                Texts.clean(request.venue(), 200, "Venue"),
                actor,
                dates,
                interviewers,
                clock.instant()));
        changes.recordLifecycle(actor, "CAMPUS_DRIVE", drive.getId(), "created", college.getName() + " " + dates);
        return views(List.of(drive)).get(0);
    }

    @Transactional
    public DriveDto update(UUID actor, Set<Role> roles, UUID id, DriveRequest request) {
        CampusDrive drive = find(id);
        requireWritable(actor, roles, drive);
        StaleVersion.check(CampusDrive.class, id, drive.getVersion(), request.version());
        if (drive.getStatus() == DriveStatus.CANCELLED) {
            throw new ConflictException("A cancelled drive cannot be changed.");
        }
        Set<LocalDate> dates = datesOf(request.dates());
        Set<UUID> interviewers = interviewersOf(request.interviewerUserIds());
        String season = Texts.clean(request.season(), 40, "Season");
        String venue = Texts.clean(request.venue(), 200, "Venue");
        changes.record(actor, "CAMPUS_DRIVE", id, "dates", sorted(drive.getDates()), sorted(dates));
        changes.record(actor, "CAMPUS_DRIVE", id, "venue", drive.getVenue(), venue);
        changes.record(actor, "CAMPUS_DRIVE", id, "season", drive.getSeasonLabel(), season);
        changes.record(actor, "CAMPUS_DRIVE", id, "interviewers", sortedIds(drive.getInterviewers()), sortedIds(interviewers));
        drive.change(season, venue, dates, interviewers);
        drives.saveAndFlush(drive);
        return views(List.of(drive)).get(0);
    }

    @Transactional
    public DriveDto setStatus(UUID actor, Set<Role> roles, UUID id, StatusRequest request) {
        CampusDrive drive = find(id);
        requireWritable(actor, roles, drive);
        if (request.version() != null) {
            StaleVersion.check(CampusDrive.class, id, drive.getVersion(), request.version());
        }
        String wanted = request.status() == null ? "" : request.status().trim().toUpperCase();
        DriveStatus before = drive.getStatus();
        switch (wanted) {
            case "HELD" -> {
                if (before == DriveStatus.CANCELLED) {
                    throw new ConflictException("A cancelled drive cannot be marked held.");
                }
                drive.markHeld();
            }
            case "CANCELLED" -> {
                String reason = Texts.clean(request.reason(), 300, "Reason");
                if (reason == null) {
                    throw new InvalidInputException("A reason is required to cancel a drive.");
                }
                drive.cancel(reason);
            }
            default -> throw new InvalidInputException("The status must be HELD or CANCELLED.");
        }
        drives.saveAndFlush(drive);
        changes.record(actor, "CAMPUS_DRIVE", id, "status", before, drive.getStatus());
        return views(List.of(drive)).get(0);
    }

    CampusDrive find(UUID id) {
        return drives.findById(id).orElseThrow(() -> new NotFoundException("Drive not found."));
    }

    /** Admin and Director change any drive; a Zone Manager only one they scheduled or attend. */
    void requireWritable(UUID userId, Set<Role> roles, CampusDrive drive) {
        if (CallerContext.isOrgWide(roles)) {
            return;
        }
        if (drive.getScheduledBy().equals(userId) || drive.getInterviewers().contains(userId)) {
            return;
        }
        throw new ForbiddenFieldException("Not your drive: you can change only drives you scheduled or attend.");
    }

    private static LocalDate firstDate(CampusDrive d) {
        return d.getDates().stream().min(Comparator.naturalOrder()).orElse(LocalDate.MAX);
    }

    private static Set<LocalDate> datesOf(List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            throw new InvalidInputException("A drive needs at least one date.");
        }
        return Set.copyOf(dates);
    }

    private Set<UUID> interviewersOf(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<UUID> wanted = Set.copyOf(ids);
        Set<UUID> found = users.findAllById(wanted).stream().map(AppUser::getId).collect(Collectors.toSet());
        if (!found.containsAll(wanted)) {
            throw new InvalidInputException("An interviewer is not a known user.");
        }
        return wanted;
    }

    private static List<LocalDate> sorted(Collection<LocalDate> dates) {
        return dates.stream().sorted().toList();
    }

    private static List<String> sortedIds(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).sorted().toList();
    }

    List<DriveDto> views(List<CampusDrive> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(CampusDrive::getId).toList();
        Map<UUID, College> collegeById = colleges.findAllById(rows.stream().map(CampusDrive::getCollegeId).distinct().toList()).stream()
                .collect(Collectors.toMap(College::getId, c -> c));
        Set<UUID> userIds = new java.util.HashSet<>();
        rows.forEach(d -> userIds.addAll(d.getInterviewers()));
        Map<UUID, String> names = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        Map<UUID, List<Candidate>> byDrive = candidates.findByDriveIdIn(ids).stream()
                .collect(Collectors.groupingBy(Candidate::getDriveId));
        LocalDate today = LocalDate.now(clock);
        List<DriveDto> out = new ArrayList<>();
        for (CampusDrive d : rows) {
            College college = collegeById.get(d.getCollegeId());
            List<Candidate> own = byDrive.getOrDefault(d.getId(), List.of());
            Map<String, Long> counts = new HashMap<>();
            for (Outcome o : Outcome.values()) {
                counts.put(o.name(), own.stream().filter(c -> c.getOutcome() == o).count());
            }
            counts.put("PENDING", own.stream().filter(c -> c.getOutcome() == null).count());
            boolean allPast = d.getDates().stream().allMatch(x -> x.isBefore(today));
            out.add(new DriveDto(
                    d.getId(),
                    new CollegeRef(college.getId(), college.getName(), college.getCity()),
                    d.getSeasonLabel(),
                    d.getVenue(),
                    d.getStatus().name(),
                    d.getCancelReason(),
                    sorted(d.getDates()),
                    d.getInterviewers().stream()
                            .map(u -> new PersonRef(u, names.getOrDefault(u, "Unknown user")))
                            .sorted(Comparator.comparing(PersonRef::name))
                            .toList(),
                    d.getScheduledBy(),
                    own.size(),
                    counts,
                    d.getStatus() == DriveStatus.PLANNED && allPast && own.isEmpty(),
                    d.getVersion()));
        }
        return out;
    }
}
