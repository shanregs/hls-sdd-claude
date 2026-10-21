package com.hls.performance.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.files.api.FileStore;
import com.hls.files.api.FileStore.FileContent;
import com.hls.performance.internal.PerformanceTypes.Area;
import com.hls.performance.internal.PerformanceTypes.IssueStatus;
import com.hls.performance.internal.PerformanceTypes.NotHeldReason;
import com.hls.performance.internal.PerformanceTypes.ObservationStatus;
import com.hls.performance.internal.PerformanceTypes.PermissionWay;
import com.hls.performance.internal.PerformanceTypes.Result;
import com.hls.performance.internal.TeacherFacts.TeacherContext;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolContacts;
import com.hls.school.api.StaleVersion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The classroom observation form (spec 026 US1): start a Draft, save, submit, record a visit that did not happen, correct a
 * Submitted observation, and attach files. A submit opens or joins a performance issue and completes the Manager's task.
 */
@Service
public class ObservationService {

    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 240;
    public static final String FILE_OWNER = "OBSERVATION";

    /** Ratings are keyed by area name; an area left out (or null) is "Not observed". */
    public record PermissionBody(String by, PermissionWay way, LocalDate on) {}

    public record SaveRequest(
            LocalDate observedOn,
            String className,
            String subject,
            Integer durationMinutes,
            Map<String, Integer> ratings,
            Result overallResult,
            String comments,
            String strengths,
            String improvements,
            String principalComments,
            PermissionBody permission,
            Long version) {}

    public record NotHeldRequest(NotHeldReason reason, String text, LocalDate observedOn, Long version) {}

    public record PreviewRequest(Map<String, Integer> ratings) {}

    public record Preview(BigDecimal average, String suggestedResult) {}

    public record AttachmentView(UUID fileId, String name, String contentType, long sizeBytes, Instant addedAt, UUID addedBy) {}

    public record Actions(boolean edit, boolean submit, boolean notHeld, boolean correct, boolean attach) {}

    public record ObservationView(
            UUID id,
            UUID rootId,
            int versionNo,
            boolean current,
            UUID teacherId,
            String teacherName,
            UUID schoolId,
            String schoolName,
            UUID observerUserId,
            String observerName,
            LocalDate observedOn,
            String className,
            String subject,
            Integer durationMinutes,
            String status,
            String notHeldReason,
            String notHeldReasonLabel,
            String notHeldText,
            boolean principalDeclined,
            String overallResult,
            BigDecimal averageRating,
            String suggestedResult,
            Map<String, Integer> ratings,
            String comments,
            String strengths,
            String improvements,
            String principalComments,
            boolean permissionRecorded,
            String permissionBy,
            String permissionWay,
            LocalDate permissionOn,
            String permissionLabel,
            String defaultPrincipalName,
            UUID issueId,
            String issueStatus,
            UUID followupOfIssueId,
            String nextStep,
            String correctionReason,
            Instant submittedAt,
            List<AttachmentView> attachments,
            Actions actions,
            Long version) {}

    private final ObservationRepository observations;
    private final ObservationAttachmentRepository attachments;
    private final IssueRepository issueRepository;
    private final IssueService issues;
    private final PlanService plans;
    private final TeacherFacts facts;
    private final ScopeGuard scope;
    private final PerformanceRecorder recorder;
    private final SchoolContacts contacts;
    private final FileStore files;
    private final BusinessDate businessDate;
    private final Clock clock;

    public ObservationService(
            ObservationRepository observations,
            ObservationAttachmentRepository attachments,
            IssueRepository issueRepository,
            IssueService issues,
            PlanService plans,
            TeacherFacts facts,
            ScopeGuard scope,
            PerformanceRecorder recorder,
            SchoolContacts contacts,
            FileStore files,
            BusinessDate businessDate,
            Clock clock) {
        this.observations = observations;
        this.attachments = attachments;
        this.issueRepository = issueRepository;
        this.issues = issues;
        this.plans = plans;
        this.facts = facts;
        this.scope = scope;
        this.recorder = recorder;
        this.contacts = contacts;
        this.files = files;
        this.businessDate = businessDate;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ the guide

    /** The average of the rated areas (one decimal) and the suggested result, shown as a guide only (FR-004). */
    public static Preview guide(Map<Area, Integer> ratings) {
        var rated = ratings.values().stream().filter(r -> r != null).toList();
        if (rated.isEmpty()) {
            return new Preview(null, null);
        }
        double mean = rated.stream().mapToInt(Integer::intValue).average().orElse(0);
        BigDecimal average = BigDecimal.valueOf(mean).setScale(1, RoundingMode.HALF_UP);
        Result suggested = mean >= 4 ? Result.GOOD : mean < 2.5 ? Result.UNSUITABLE : Result.NEEDS_IMPROVEMENT;
        return new Preview(average, suggested.name());
    }

    public Preview preview(PreviewRequest request) {
        return guide(parseRatings(request == null ? null : request.ratings()));
    }

    // ------------------------------------------------------------------ start, save, submit

    /** Starts a Draft for an active Teacher placed at a School; the Teacher's under-correction issue makes it a follow-up. */
    @Transactional
    public ObservationView start(Caller caller, UUID teacherId, LocalDate observedOn) {
        scope.requireTeacher(caller, teacherId);
        TeacherContext teacher = facts.require(teacherId);
        if (!teacher.active()) {
            throw new InvalidInputException("Only an active Teacher can be observed. " + teacher.name() + " is "
                    + teacher.status().toLowerCase().replace('_', ' ') + ".");
        }
        if (!teacher.placed()) {
            throw new InvalidInputException(teacher.name() + " is not placed at a School, so there is nothing to observe.");
        }
        LocalDate date = observedOn == null ? businessDate.today() : observedOn;
        requireNotFuture(date);
        Observation draft = new Observation(teacherId, teacher.schoolId(), caller.userId(), java.util.Objects.requireNonNullElse(facts.userName(caller.userId()), "Unknown"), teacher.managerUserId(), date,
                clock.instant());
        issues.underCorrectionOf(teacherId).ifPresent(issue -> draft.followupOfIssueId = issue.id);
        observations.saveAndFlush(draft);
        recorder.event(PerformanceRecorder.OBSERVATION, draft.id, teacherId, "CREATED", caller.userId(),
                "Observation of " + teacher.name() + " started for " + date);
        return view(draft, caller, false);
    }

    @Transactional
    public ObservationView save(Caller caller, UUID id, SaveRequest request) {
        Observation draft = current(caller, id);
        requireDraftOf(caller, draft);
        StaleVersion.check(Observation.class, id, draft.version, request.version());
        apply(draft, request);
        draft.updatedAt = clock.instant();
        observations.saveAndFlush(draft);
        recorder.event(PerformanceRecorder.OBSERVATION, draft.id, draft.teacherId, "DRAFT_SAVED", caller.userId(), null);
        return view(draft, caller, false);
    }

    /** Submits the Draft. A body, when given, is saved first so the form can be saved and submitted in one step. */
    @Transactional
    public ObservationView submit(Caller caller, UUID id, SaveRequest request) {
        Observation draft = current(caller, id);
        requireDraftOf(caller, draft);
        if (request != null) {
            StaleVersion.check(Observation.class, id, draft.version, request.version());
            apply(draft, request);
        }
        requireSubmittable(draft);
        Instant now = clock.instant();
        draft.status = ObservationStatus.SUBMITTED;
        draft.submittedAt = now;
        draft.updatedAt = now;
        observations.saveAndFlush(draft);
        recorder.change(PerformanceRecorder.OBSERVATION, draft.id, draft.teacherId, "SUBMITTED", caller.userId(), "status", ObservationStatus.DRAFT,
                ObservationStatus.SUBMITTED, draft.overallResult.label() + ", average " + draft.averageRating);
        afterSubmit(caller, draft);
        return view(draft, caller, false);
    }

    /** The task is completed first (when the observer is the Manager), then the issue is opened, joined or resolved. */
    private void afterSubmit(Caller caller, Observation observation) {
        plans.completeFor(observation.teacherId, observation.rootId, caller.userId(), observation.observedOn, observation.overallResult.label());
        PerformanceIssue issue = issues.afterObservationSubmitted(observation, caller);
        if (issue != null && !issue.id.equals(observation.issueId)) {
            observation.issueId = issue.id;
            observations.saveAndFlush(observation);
        }
    }

    /** Records that the visit did not take place; it needs no ratings and does not satisfy a due observation (FR-008). */
    @Transactional
    public ObservationView notHeld(Caller caller, UUID id, NotHeldRequest request) {
        Observation draft = current(caller, id);
        requireDraftOf(caller, draft);
        StaleVersion.check(Observation.class, id, draft.version, request.version());
        if (request.reason() == null) {
            throw new InvalidInputException("Choose why the visit did not take place.");
        }
        String text = clean(request.text(), PerformanceTypes.MAX_TEXT, "The explanation");
        if (request.reason() == NotHeldReason.OTHER && text == null) {
            throw new InvalidInputException("Say why the visit did not take place.");
        }
        if (request.observedOn() != null) {
            requireNotFuture(request.observedOn());
            draft.observedOn = request.observedOn();
        }
        draft.status = ObservationStatus.NOT_HELD;
        draft.notHeldReason = request.reason();
        draft.notHeldText = text;
        draft.updatedAt = clock.instant();
        observations.saveAndFlush(draft);
        recorder.change(PerformanceRecorder.OBSERVATION, draft.id, draft.teacherId, "NOT_HELD", caller.userId(), "status", ObservationStatus.DRAFT,
                ObservationStatus.NOT_HELD, request.reason().label() + (text == null ? "" : ": " + text));
        return view(draft, caller, false);
    }

    /** A correction is a new version with a reason; the earlier version stays, readable to Admin and Director (FR-025). */
    @Transactional
    public ObservationView correct(Caller caller, UUID id, String reason, SaveRequest request) {
        Observation old = current(caller, id);
        if (old.status != ObservationStatus.SUBMITTED) {
            throw new ConflictException("Only a submitted observation can be corrected.");
        }
        if (!caller.orgWide() && !old.observerUserId.equals(caller.userId())) {
            throw new ForbiddenFieldException("Only the observer, Admin or Director can correct an observation.");
        }
        StaleVersion.check(Observation.class, id, old.version, request.version());
        String why = required(reason, 5, 500, "The reason for the correction");
        Instant now = clock.instant();
        Observation next = old.nextVersion(why, now);
        apply(next, request);
        requireSubmittable(next);
        next.status = ObservationStatus.SUBMITTED;
        old.current = false;
        old.status = ObservationStatus.SUPERSEDED;
        old.updatedAt = now;
        observations.saveAndFlush(old);
        observations.saveAndFlush(next);
        for (ObservationAttachment a : attachments.findByObservationIdOrderByAddedAt(old.id)) {
            attachments.save(new ObservationAttachment(next.id, a.fileId, a.name, a.addedBy, a.addedAt));
        }
        recorder.change(PerformanceRecorder.OBSERVATION, next.id, next.teacherId, "CORRECTED", caller.userId(), "overallResult", old.overallResult,
                next.overallResult, "Version " + next.versionNo + ": " + why);
        if (next.overallResult.needsIssue() && next.issueId == null) {
            PerformanceIssue issue = issues.afterObservationSubmitted(next, caller);
            if (issue != null) {
                next.issueId = issue.id;
                observations.saveAndFlush(next);
            }
        }
        return view(next, caller, false);
    }

    // ------------------------------------------------------------------ attachments

    @Transactional
    public AttachmentView addAttachment(Caller caller, UUID id, String name, byte[] content) {
        Observation observation = current(caller, id);
        requireCanAttach(caller, observation);
        List<ObservationAttachment> existing = attachments.findByObservationIdOrderByAddedAt(observation.id);
        if (existing.size() >= PerformanceTypes.MAX_ATTACHMENTS) {
            throw new ConflictException("An observation can have at most " + PerformanceTypes.MAX_ATTACHMENTS + " files.");
        }
        var stored = files.store(caller.userId(), FILE_OWNER, observation.id, name, content);
        ObservationAttachment attachment = new ObservationAttachment(observation.id, stored.id(), stored.name(), caller.userId(), clock.instant());
        attachments.save(attachment);
        recorder.event(PerformanceRecorder.OBSERVATION, observation.id, observation.teacherId, "FILE_ADDED", caller.userId(), stored.name());
        return new AttachmentView(stored.id(), stored.name(), stored.contentType(), stored.sizeBytes(), attachment.addedAt, caller.userId());
    }

    @Transactional
    public void removeAttachment(Caller caller, UUID id, UUID fileId, String reason) {
        Observation observation = current(caller, id);
        if (observation.status != ObservationStatus.DRAFT) {
            throw new ConflictException("A file can be removed only from a draft.");
        }
        requireDraftOf(caller, observation);
        ObservationAttachment attachment = attachments.findByObservationIdAndFileId(observation.id, fileId)
                .orElseThrow(() -> new NotFoundException("File not found."));
        String why = required(reason, 3, 300, "The reason");
        files.remove(caller.userId(), fileId, why);
        attachments.delete(attachment);
        recorder.event(PerformanceRecorder.OBSERVATION, observation.id, observation.teacherId, "FILE_REMOVED", caller.userId(), attachment.name + ": " + why);
    }

    @Transactional(readOnly = true)
    public FileContent download(Caller caller, UUID id, UUID fileId) {
        Observation observation = visible(caller, id);
        attachments.findByObservationIdAndFileId(observation.id, fileId).orElseThrow(() -> new NotFoundException("File not found."));
        return files.open(fileId).orElseThrow(() -> new NotFoundException("File not found."));
    }

    // ------------------------------------------------------------------ reads

    /** One observation; Admin and Director may also ask for the earlier versions. Returns the current version's view. */
    @Transactional(readOnly = true)
    public ObservationView get(Caller caller, UUID id) {
        return view(visible(caller, id), caller, false);
    }

    /** Every version of the observation, newest first: Admin and Director only (FR-025). */
    @Transactional(readOnly = true)
    public List<ObservationView> versions(Caller caller, UUID id) {
        if (!caller.orgWide()) {
            throw new ForbiddenFieldException("Only Admin or Director can read earlier versions.");
        }
        Observation observation = visible(caller, id);
        List<ObservationView> views = new ArrayList<>();
        for (Observation o : observations.findByRootIdOrderByVersionNoDesc(observation.rootId)) {
            views.add(view(o, caller, true));
        }
        return views;
    }

    /** The observation by any version id; a Manager sees it if its Teacher is in scope today or they wrote it. */
    Observation visible(Caller caller, UUID id) {
        Observation observation = observations.findById(id).orElseThrow(() -> new NotFoundException("Observation not found."));
        scope.requireRecord(caller, observation.teacherId, observation.observerUserId, "Observation");
        return observation;
    }

    private Observation current(Caller caller, UUID id) {
        Observation observation = visible(caller, id);
        if (!observation.current) {
            throw new ConflictException("This is an earlier version. Open the current version of the observation.");
        }
        return observation;
    }

    ObservationView view(Observation o, Caller caller, boolean historic) {
        TeacherContext teacher = facts.find(o.teacherId).orElse(null);
        Map<String, Integer> ratings = new LinkedHashMap<>();
        for (Area area : Area.values()) {
            ratings.put(area.name(), o.ratings.get(area));
        }
        Preview guide = guide(o.ratings);
        boolean draft = o.status == ObservationStatus.DRAFT && o.observerUserId.equals(caller.userId()) && !historic;
        boolean author = caller.orgWide() || o.observerUserId.equals(caller.userId());
        boolean recorded = o.permissionBy != null;
        PerformanceIssue issue = o.issueId == null ? null : issueRepository.findById(o.issueId).orElse(null);
        List<AttachmentView> files = new ArrayList<>();
        for (ObservationAttachment a : attachments.findByObservationIdOrderByAddedAt(o.id)) {
            this.files.find(a.fileId).ifPresent(f -> files.add(new AttachmentView(f.id(), f.name(), f.contentType(), f.sizeBytes(), a.addedAt, a.addedBy)));
        }
        return new ObservationView(
                o.id,
                o.rootId,
                o.versionNo,
                o.current,
                o.teacherId,
                teacher == null ? null : teacher.name(),
                o.schoolId,
                facts.schoolNames(List.of(o.schoolId)).get(o.schoolId),
                o.observerUserId,
                o.observerName,
                o.observedOn,
                o.className,
                o.subject,
                o.durationMinutes,
                o.status.name(),
                o.notHeldReason == null ? null : o.notHeldReason.name(),
                o.notHeldReason == null ? null : o.notHeldReason.label(),
                o.notHeldText,
                o.notHeldReason == NotHeldReason.PRINCIPAL_DECLINED,
                o.overallResult == null ? null : o.overallResult.name(),
                o.status == ObservationStatus.DRAFT ? guide.average() : o.averageRating,
                o.status == ObservationStatus.DRAFT ? guide.suggestedResult() : o.suggestedResult == null ? null : o.suggestedResult.name(),
                ratings,
                o.comments,
                o.strengths,
                o.improvements,
                o.principalComments,
                recorded,
                o.permissionBy,
                o.permissionWay == null ? null : o.permissionWay.name(),
                o.permissionOn,
                recorded ? null : "Permission not recorded",
                principalName(o.schoolId),
                o.issueId,
                issue == null ? null : issue.status.name(),
                o.followupOfIssueId,
                nextStep(o, issue),
                o.correctionReason,
                o.submittedAt,
                files,
                new Actions(draft, draft, draft, o.current && o.status == ObservationStatus.SUBMITTED && author, draft),
                o.version);
    }

    /** What the screen should offer after a submit: a corrective action or a referral. */
    private String nextStep(Observation o, PerformanceIssue issue) {
        if (o.status != ObservationStatus.SUBMITTED || issue == null || o.overallResult == null) {
            return null;
        }
        if (o.overallResult == Result.UNSUITABLE && issue.status != IssueStatus.REFERRED && issue.isActive()) {
            return "REFERRAL";
        }
        if (o.overallResult == Result.NEEDS_IMPROVEMENT && (issue.status == IssueStatus.OPEN || issue.status == IssueStatus.UNDER_CORRECTION)) {
            return "CORRECTIVE_ACTION";
        }
        return null;
    }

    String principalName(UUID schoolId) {
        var view = contacts.contactsOf(schoolId);
        return view == null || view.principal() == null ? null : view.principal().name();
    }

    // ------------------------------------------------------------------ validation

    private void apply(Observation o, SaveRequest r) {
        if (r.observedOn() != null) {
            requireNotFuture(r.observedOn());
            o.observedOn = r.observedOn();
        }
        o.className = clean(r.className(), 100, "The class");
        o.subject = clean(r.subject(), 100, "The subject");
        if (r.durationMinutes() != null && (r.durationMinutes() < MIN_MINUTES || r.durationMinutes() > MAX_MINUTES)) {
            throw new InvalidInputException("The duration must be " + MIN_MINUTES + " to " + MAX_MINUTES + " minutes.");
        }
        o.durationMinutes = r.durationMinutes();
        o.ratings.clear();
        Map<Area, Integer> parsed = parseRatings(r.ratings());
        parsed.forEach((area, rating) -> {
            if (rating != null) {
                o.ratings.put(area, rating);
            }
        });
        Preview guide = guide(o.ratings);
        o.averageRating = guide.average();
        o.suggestedResult = guide.suggestedResult() == null ? null : Result.valueOf(guide.suggestedResult());
        o.overallResult = r.overallResult();
        o.comments = clean(r.comments(), PerformanceTypes.MAX_TEXT, "The comments");
        o.strengths = clean(r.strengths(), PerformanceTypes.MAX_TEXT, "The strengths");
        o.improvements = clean(r.improvements(), PerformanceTypes.MAX_TEXT, "The areas for improvement");
        o.principalComments = clean(r.principalComments(), PerformanceTypes.MAX_TEXT, "The Principal's comments");
        PermissionBody p = r.permission();
        if (p == null || (isBlank(p.by()) && p.way() == null && p.on() == null)) {
            o.permissionBy = null;
            o.permissionWay = null;
            o.permissionOn = null;
        } else {
            String by = clean(p.by(), 160, "The name of the person who gave permission");
            if (by == null) {
                throw new InvalidInputException("Say who gave the permission.");
            }
            o.permissionBy = by;
            o.permissionWay = p.way();
            o.permissionOn = p.on();
        }
    }

    private static Map<Area, Integer> parseRatings(Map<String, Integer> raw) {
        Map<Area, Integer> ratings = new EnumMap<>(Area.class);
        if (raw == null) {
            return ratings;
        }
        raw.forEach((key, value) -> {
            Area area;
            try {
                area = Area.valueOf(key == null ? "" : key.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("Unknown teaching area: " + key + ".");
            }
            if (value != null && (value < 1 || value > 5)) {
                throw new InvalidInputException("A rating must be 1 to 5 (" + area.label() + ").");
            }
            ratings.put(area, value);
        });
        return ratings;
    }

    private static void requireSubmittable(Observation o) {
        if (o.ratings.isEmpty()) {
            throw new InvalidInputException("Rate at least one teaching area.");
        }
        if (o.overallResult == null) {
            throw new InvalidInputException("Choose the overall result: Good, Needs improvement or Unsuitable.");
        }
        if (isBlank(o.strengths) && isBlank(o.improvements)) {
            throw new InvalidInputException("Write the strengths or the areas for improvement.");
        }
    }

    private static void requireDraftOf(Caller caller, Observation o) {
        if (o.status != ObservationStatus.DRAFT) {
            throw new ConflictException("This observation is " + o.status.name().toLowerCase().replace('_', ' ') + ", not a draft.");
        }
        if (!o.observerUserId.equals(caller.userId())) {
            throw new ForbiddenFieldException("Only the observer can change a draft.");
        }
    }

    private static void requireCanAttach(Caller caller, Observation o) {
        requireDraftOf(caller, o);
    }

    private void requireNotFuture(LocalDate date) {
        if (date.isAfter(businessDate.today())) {
            throw new InvalidInputException("The date of the observation cannot be in the future.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String clean(String raw, int max, String what) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        if (text.length() > max) {
            throw new InvalidInputException(what + " must be at most " + max + " characters.");
        }
        return text;
    }

    private static String required(String raw, int min, int max, String what) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() < min) {
            throw new InvalidInputException(what + " is required (at least " + min + " characters).");
        }
        if (text.length() > max) {
            throw new InvalidInputException(what + " must be at most " + max + " characters.");
        }
        return text;
    }
}
