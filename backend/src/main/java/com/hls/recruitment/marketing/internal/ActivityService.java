package com.hls.recruitment.marketing.internal;

import com.hls.files.api.FileStore;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.identity.user.Role;
import com.hls.organization.api.ScopeView;
import com.hls.recruitment.api.DriveActivities;
import com.hls.recruitment.api.FollowUpScheduled;
import com.hls.recruitment.api.PlannedActivity;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visits, calls, proposal meetings and follow-ups. A visit is planned, then completed with a required outcome,
 * rescheduled (the earlier date stays) or cancelled with a reason; "missed" is derived when the date passes. Access
 * follows the Zone of the prospect (or the School of an account visit).
 */
@Service
public class ActivityService {

    public static final String FILE_OWNER = "MARKETING_ACTIVITY";

    public record PlanRequest(
            UUID prospectId, UUID schoolId, String type, LocalDate date, List<UUID> attendeeUserIds, String notes) {}

    public record CompleteRequest(String outcome, String notes, LocalDate followUpOn, Long version) {}

    public record RescheduleRequest(LocalDate date, Long version) {}

    public record CancelRequest(String reason, Long version) {}

    public record TargetRef(UUID id, String name) {}

    public record AttachmentRef(UUID fileId, String name, long sizeBytes, String contentType) {}

    public record DateChange(LocalDate from, LocalDate to) {}

    public record ActivityDto(
            UUID id,
            String type,
            String status,
            String effectiveStatus,
            boolean rescheduled,
            LocalDate date,
            String notes,
            String outcome,
            LocalDate followUpOn,
            boolean followUpOverdue,
            String cancelReason,
            TargetRef prospect,
            TargetRef school,
            List<ProspectService.PersonRef> attendees,
            List<AttachmentRef> attachments,
            List<DateChange> dateHistory,
            Long version) {}

    public record ListDto(List<ActivityDto> content, List<PlannedActivity> drives) {}

    private final MarketingActivityRepository activities;
    private final ActivityDateHistoryRepository dateHistory;
    private final ActivityAttachmentRepository attachments;
    private final ProspectRepository prospects;
    private final ProspectService prospectService;
    private final AppUserRepository users;
    private final SchoolDirectory schools;
    private final FileStore files;
    private final MarketingScope scope;
    private final ObjectProvider<DriveActivities> drives;
    private final ApplicationEventPublisher events;
    private final ChangeRecorder changes;
    private final Clock clock;

    public ActivityService(
            MarketingActivityRepository activities,
            ActivityDateHistoryRepository dateHistory,
            ActivityAttachmentRepository attachments,
            ProspectRepository prospects,
            ProspectService prospectService,
            AppUserRepository users,
            SchoolDirectory schools,
            FileStore files,
            MarketingScope scope,
            ObjectProvider<DriveActivities> drives,
            ApplicationEventPublisher events,
            ChangeRecorder changes,
            Clock clock) {
        this.activities = activities;
        this.dateHistory = dateHistory;
        this.attachments = attachments;
        this.prospects = prospects;
        this.prospectService = prospectService;
        this.users = users;
        this.schools = schools;
        this.files = files;
        this.scope = scope;
        this.drives = drives;
        this.events = events;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ListDto list(UUID userId, Set<Role> roles, LocalDate from, LocalDate to, boolean mine, UUID prospectId) {
        ScopeView view = scope.of(userId, roles);
        List<MarketingActivity> found = (prospectId != null
                        ? activities.findByProspectIdOrderByActivityDateDesc(prospectId)
                        : activities.findByActivityDateBetweenOrderByActivityDate(
                                from == null ? LocalDate.of(2000, 1, 1) : from, to == null ? LocalDate.of(2100, 1, 1) : to))
                .stream()
                .filter(a -> mine ? a.getAttendees().contains(userId) || a.getCreatedBy().equals(userId) : true)
                .toList();
        Map<UUID, Prospect> byId = prospectsOf(found);
        List<MarketingActivity> visible = found.stream().filter(a -> allowed(view, a, byId)).toList();
        List<PlannedActivity> driveEntries = List.of();
        DriveActivities driveSource = drives.getIfAvailable();
        if (driveSource != null && prospectId == null) {
            driveEntries = driveSource.plannedBetween(from, to, mine ? userId : null);
        }
        return new ListDto(dtosOf(visible, byId), driveEntries);
    }

    @Transactional(readOnly = true)
    public ActivityDto get(UUID userId, Set<Role> roles, UUID id) {
        MarketingActivity activity = visible(userId, roles, id);
        return dtosOf(List.of(activity), prospectsOf(List.of(activity))).get(0);
    }

    @Transactional
    public ActivityDto plan(UUID actor, Set<Role> roles, PlanRequest request) {
        if ((request.prospectId() == null) == (request.schoolId() == null)) {
            throw new InvalidInputException("A visit is for a prospect or for a School, not both and not neither.");
        }
        if (request.date() == null) {
            throw new InvalidInputException("The date is required.");
        }
        ActivityType type = parseType(request.type());
        ScopeView view = scope.of(actor, roles);
        if (request.prospectId() != null) {
            prospectService.visible(actor, roles, request.prospectId());
        } else {
            SchoolDirectory.SchoolInfo school = schools.school(request.schoolId())
                    .orElseThrow(() -> new InvalidInputException("School not found."));
            if (!view.allowsSchool(school.id())) {
                throw new NotFoundException("School not found.");
            }
        }
        Set<UUID> attendees = attendeesOf(request.attendeeUserIds(), actor);
        MarketingActivity activity = activities.saveAndFlush(new MarketingActivity(
                request.prospectId(),
                request.schoolId(),
                type,
                request.date(),
                Texts.clean(request.notes(), 1000, "Notes"),
                attendees,
                actor,
                clock.instant()));
        changes.recordLifecycle(actor, "MARKETING_ACTIVITY", activity.getId(), "planned", type + " " + request.date());
        return get(actor, roles, activity.getId());
    }

    @Transactional
    public ActivityDto complete(UUID actor, Set<Role> roles, UUID id, CompleteRequest request) {
        MarketingActivity activity = visible(actor, roles, id);
        checkVersion(activity, id, request.version());
        requirePlanned(activity, "completed");
        String outcome = Texts.clean(request.outcome(), 1000, "Outcome");
        if (outcome == null) {
            throw new InvalidInputException("An outcome is required to complete a visit: who was met, what was discussed and the next action.");
        }
        LocalDate followUp = request.followUpOn();
        if (followUp != null && followUp.isBefore(activity.getActivityDate())) {
            throw new InvalidInputException("The follow-up date cannot be before the visit.");
        }
        activity.complete(outcome, Texts.clean(request.notes(), 1000, "Notes"), followUp);
        activities.saveAndFlush(activity);
        changes.record(actor, "MARKETING_ACTIVITY", id, "status", ActivityStatus.PLANNED, ActivityStatus.COMPLETED);
        changes.record(actor, "MARKETING_ACTIVITY", id, "followUpOn", null, followUp);
        if (followUp != null) {
            publishFollowUp(activity, followUp);
        }
        return get(actor, roles, id);
    }

    @Transactional
    public ActivityDto reschedule(UUID actor, Set<Role> roles, UUID id, RescheduleRequest request) {
        MarketingActivity activity = visible(actor, roles, id);
        checkVersion(activity, id, request.version());
        requirePlanned(activity, "rescheduled");
        if (request.date() == null) {
            throw new InvalidInputException("The new date is required.");
        }
        if (request.date().equals(activity.getActivityDate())) {
            throw new ConflictException("The activity is already on this date.");
        }
        LocalDate old = activity.getActivityDate();
        activity.reschedule(request.date());
        activities.saveAndFlush(activity);
        dateHistory.save(new ActivityDateHistory(id, old, request.date(), actor, clock.instant()));
        changes.record(actor, "MARKETING_ACTIVITY", id, "date", old, request.date());
        return get(actor, roles, id);
    }

    @Transactional
    public ActivityDto cancel(UUID actor, Set<Role> roles, UUID id, CancelRequest request) {
        MarketingActivity activity = visible(actor, roles, id);
        checkVersion(activity, id, request.version());
        requirePlanned(activity, "cancelled");
        String reason = Texts.clean(request.reason(), 300, "Reason");
        if (reason == null) {
            throw new InvalidInputException("A reason is required to cancel an activity.");
        }
        activity.cancel(reason);
        activities.saveAndFlush(activity);
        changes.record(actor, "MARKETING_ACTIVITY", id, "status", ActivityStatus.PLANNED, ActivityStatus.CANCELLED);
        return get(actor, roles, id);
    }

    MarketingActivity visible(UUID userId, Set<Role> roles, UUID id) {
        MarketingActivity activity = activities.findById(id).orElseThrow(() -> new NotFoundException("Activity not found."));
        ScopeView view = scope.of(userId, roles);
        if (!allowed(view, activity, prospectsOf(List.of(activity)))) {
            throw new NotFoundException("Activity not found.");
        }
        return activity;
    }

    private boolean allowed(ScopeView view, MarketingActivity a, Map<UUID, Prospect> prospectsById) {
        if (a.getProspectId() != null) {
            Prospect p = prospectsById.get(a.getProspectId());
            return p != null && scope.allowsZone(view, p.getZoneId());
        }
        return a.getSchoolId() != null && view.allowsSchool(a.getSchoolId());
    }

    private Map<UUID, Prospect> prospectsOf(Collection<MarketingActivity> list) {
        Set<UUID> ids = list.stream().map(MarketingActivity::getProspectId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        return prospects.findAllById(ids).stream().collect(Collectors.toMap(Prospect::getId, p -> p));
    }

    private static void checkVersion(MarketingActivity activity, UUID id, Long version) {
        if (version != null) {
            com.hls.school.api.StaleVersion.check(MarketingActivity.class, id, activity.getVersion(), version);
        }
    }

    private void requirePlanned(MarketingActivity activity, String what) {
        if (activity.getStatus() != ActivityStatus.PLANNED) {
            throw new ConflictException("Only a planned activity can be " + what + ".");
        }
    }

    private void publishFollowUp(MarketingActivity activity, LocalDate dueOn) {
        UUID owner = activity.getCreatedBy();
        String title = "Follow up";
        if (activity.getProspectId() != null) {
            Optional<Prospect> prospect = prospects.findById(activity.getProspectId());
            if (prospect.isPresent()) {
                owner = prospect.get().getOwnerUserId();
                title = "Follow up with " + prospect.get().getName();
            }
        } else if (activity.getSchoolId() != null) {
            title = "Follow up with " + schools.school(activity.getSchoolId()).map(SchoolDirectory.SchoolInfo::name).orElse("the School");
        }
        events.publishEvent(new FollowUpScheduled(activity.getId(), activity.getProspectId(), owner, dueOn, title));
    }

    private static ActivityType parseType(String value) {
        try {
            return ActivityType.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("The type must be VISIT, CALL, PROPOSAL_MEETING or FOLLOW_UP.");
        }
    }

    private Set<UUID> attendeesOf(List<UUID> ids, UUID actor) {
        Set<UUID> wanted = new HashSet<>();
        if (ids != null) {
            wanted.addAll(ids);
        }
        if (wanted.isEmpty()) {
            wanted.add(actor);
        }
        Set<UUID> found = users.findAllById(wanted).stream().map(AppUser::getId).collect(Collectors.toSet());
        if (!found.containsAll(wanted)) {
            throw new InvalidInputException("An attendee is not a known user.");
        }
        return wanted;
    }

    List<ActivityDto> dtosOf(List<MarketingActivity> list, Map<UUID, Prospect> prospectsById) {
        if (list.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        Set<UUID> userIds = new HashSet<>();
        list.forEach(a -> userIds.addAll(a.getAttendees()));
        Map<UUID, String> names = prospectService.namesOf(userIds);
        List<UUID> ids = list.stream().map(MarketingActivity::getId).toList();
        Map<UUID, List<ActivityDateHistory>> history = new HashMap<>();
        Map<UUID, List<AttachmentRef>> files = new HashMap<>();
        for (UUID id : ids) {
            history.put(id, dateHistory.findByActivityIdOrderByNewDateAsc(id));
            files.put(id, attachmentsOf(id));
        }
        Set<UUID> prospectIds = list.stream().map(MarketingActivity::getProspectId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, List<MarketingActivity>> byProspect = prospectIds.isEmpty()
                ? Map.of()
                : activities.findByProspectIdIn(prospectIds).stream().collect(Collectors.groupingBy(MarketingActivity::getProspectId));
        Set<UUID> schoolIds = list.stream().map(MarketingActivity::getSchoolId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> schoolNames = schoolIds.isEmpty()
                ? Map.of()
                : schools.schools(schoolIds).stream().collect(Collectors.toMap(SchoolDirectory.SchoolInfo::id, SchoolDirectory.SchoolInfo::name));
        return list.stream()
                .map(a -> {
                    Prospect p = a.getProspectId() == null ? null : prospectsById.get(a.getProspectId());
                    boolean overdue = a.getStatus() == ActivityStatus.COMPLETED
                            && a.getFollowUpOn() != null
                            && a.getFollowUpOn().isBefore(today)
                            && a.getProspectId() != null
                            && byProspect.getOrDefault(a.getProspectId(), List.of()).stream()
                                    .noneMatch(other -> !other.getId().equals(a.getId())
                                            && other.getStatus() != ActivityStatus.CANCELLED
                                            && other.getActivityDate().isAfter(a.getActivityDate()));
                    boolean missed = a.getStatus() == ActivityStatus.PLANNED && a.getActivityDate().isBefore(today);
                    return new ActivityDto(
                            a.getId(),
                            a.getType().name(),
                            a.getStatus().name(),
                            missed ? "MISSED" : a.getStatus().name(),
                            !history.get(a.getId()).isEmpty(),
                            a.getActivityDate(),
                            a.getNotes(),
                            a.getOutcome(),
                            a.getFollowUpOn(),
                            overdue,
                            a.getCancelReason(),
                            p == null ? null : new TargetRef(p.getId(), p.getName()),
                            a.getSchoolId() == null ? null : new TargetRef(a.getSchoolId(), schoolNames.getOrDefault(a.getSchoolId(), "-")),
                            a.getAttendees().stream()
                                    .map(u -> new ProspectService.PersonRef(u, names.getOrDefault(u, "Unknown user")))
                                    .sorted(Comparator.comparing(ProspectService.PersonRef::name))
                                    .toList(),
                            files.get(a.getId()),
                            history.get(a.getId()).stream().map(h -> new DateChange(h.getOldDate(), h.getNewDate())).toList(),
                            a.getVersion());
                })
                .sorted(Comparator.comparing(ActivityDto::date))
                .toList();
    }

    List<AttachmentRef> attachmentsOf(UUID activityId) {
        return attachments.findByActivityId(activityId).stream()
                .map(l -> files.find(l.getFileId()))
                .flatMap(Optional::stream)
                .map(f -> new AttachmentRef(f.id(), f.name(), f.sizeBytes(), f.contentType()))
                .toList();
    }

    Instant now() {
        return clock.instant();
    }
}
