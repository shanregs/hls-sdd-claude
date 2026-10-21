package com.hls.performance.internal;

import com.hls.designation.api.BusinessDate;
import com.hls.performance.internal.PerformanceTypes.FeedbackCategory;
import com.hls.performance.internal.PerformanceTypes.FeedbackStatus;
import com.hls.performance.internal.TeacherFacts.TeacherContext;
import com.hls.school.api.ConflictException;
import com.hls.school.api.ForbiddenFieldException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolContacts;
import com.hls.school.api.StaleVersion;
import com.hls.tasks.api.TaskCreation;
import com.hls.tasks.api.TaskType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Principal feedback (spec 026 US3): what a School's Principal said about a Teacher, kept apart from the observation ratings.
 * Recording it completes the recorder's open Principal feedback task for the Teacher; marking action required offers an
 * issue or a planned observation.
 */
@Service
public class FeedbackService {

    public static final int MIN_COMMENTS = 10;

    public record RecordRequest(
            UUID teacherId,
            String principalName,
            LocalDate givenOn,
            FeedbackCategory category,
            String comments,
            Boolean actionRequired,
            UUID observationId) {}

    public record CorrectRequest(
            String principalName, LocalDate givenOn, FeedbackCategory category, String comments, Boolean actionRequired, String reason, Long version) {}

    public record Actions(boolean correct, boolean withdraw, boolean openIssue, boolean planObservation) {}

    public record FeedbackView(
            UUID id,
            UUID rootId,
            int versionNo,
            boolean current,
            UUID teacherId,
            String teacherName,
            UUID schoolId,
            String schoolName,
            String principalName,
            LocalDate givenOn,
            String category,
            String categoryLabel,
            String comments,
            boolean actionRequired,
            UUID observationRootId,
            UUID recordedBy,
            String recordedByName,
            String status,
            String withdrawnReason,
            Instant withdrawnAt,
            UUID issueId,
            String issueStatus,
            boolean offerIssue,
            String correctionReason,
            Actions actions,
            Long version) {}

    private final FeedbackRepository feedback;
    private final ObservationRepository observations;
    private final IssueRepository issueRepository;
    private final TeacherFacts facts;
    private final ScopeGuard scope;
    private final PerformanceRecorder recorder;
    private final SchoolContacts contacts;
    private final TaskCreation tasks;
    private final BusinessDate businessDate;
    private final Clock clock;

    public FeedbackService(
            FeedbackRepository feedback,
            ObservationRepository observations,
            IssueRepository issueRepository,
            TeacherFacts facts,
            ScopeGuard scope,
            PerformanceRecorder recorder,
            SchoolContacts contacts,
            TaskCreation tasks,
            BusinessDate businessDate,
            Clock clock) {
        this.feedback = feedback;
        this.observations = observations;
        this.issueRepository = issueRepository;
        this.facts = facts;
        this.scope = scope;
        this.recorder = recorder;
        this.contacts = contacts;
        this.tasks = tasks;
        this.businessDate = businessDate;
        this.clock = clock;
    }

    @Transactional
    public FeedbackView record(Caller caller, RecordRequest request) {
        if (request == null || request.teacherId() == null) {
            throw new InvalidInputException("Choose the Teacher.");
        }
        scope.requireTeacher(caller, request.teacherId());
        TeacherContext teacher = facts.require(request.teacherId());
        if (!teacher.placed()) {
            throw new InvalidInputException(teacher.name() + " is not placed at a School.");
        }
        if (request.category() == null) {
            throw new InvalidInputException("Choose the category of the feedback.");
        }
        LocalDate given = request.givenOn() == null ? businessDate.today() : request.givenOn();
        requireNotFuture(given);
        String principal = clean(request.principalName(), 160, "The Principal's name");
        if (principal == null) {
            principal = defaultPrincipal(teacher.schoolId());
        }
        if (principal == null) {
            throw new InvalidInputException("Write the Principal's name.");
        }
        UUID observationRoot = null;
        if (request.observationId() != null) {
            Observation linked = observations.findById(request.observationId())
                    .filter(o -> o.teacherId.equals(request.teacherId()))
                    .orElseThrow(() -> new InvalidInputException("The observation does not belong to this Teacher."));
            observationRoot = linked.rootId;
        }
        PrincipalFeedback record = new PrincipalFeedback(request.teacherId(), teacher.schoolId(), principal, given, request.category(),
                comments(request.comments()), Boolean.TRUE.equals(request.actionRequired()), observationRoot, caller.userId(), clock.instant());
        feedback.saveAndFlush(record);
        recorder.event(PerformanceRecorder.FEEDBACK, record.id, record.teacherId, "CREATED", caller.userId(),
                request.category().label() + (Boolean.TRUE.equals(request.actionRequired()) ? ", action required" : ""));
        // the recorder's own open Principal feedback task for this Teacher is done
        tasks.completeLinked(TaskType.PRINCIPAL_FEEDBACK, "TEACHER", record.teacherId, caller.userId(),
                "Principal feedback recorded on " + given + ": " + request.category().label() + ".");
        return view(record, caller);
    }

    /** A correction is a new version with a reason; the earlier version stays (FR-025). */
    @Transactional
    public FeedbackView correct(Caller caller, UUID id, CorrectRequest request) {
        PrincipalFeedback old = current(caller, id);
        requireAuthor(caller, old);
        if (old.status != FeedbackStatus.RECORDED) {
            throw new ConflictException("Only recorded feedback can be corrected.");
        }
        StaleVersion.check(PrincipalFeedback.class, id, old.version, request.version());
        String why = required(request.reason(), 5, 500, "The reason for the correction");
        Instant now = clock.instant();
        PrincipalFeedback next = old.nextVersion(why, now);
        next.principalName = request.principalName() == null || request.principalName().isBlank() ? old.principalName
                : clean(request.principalName(), 160, "The Principal's name");
        if (request.givenOn() != null) {
            requireNotFuture(request.givenOn());
            next.givenOn = request.givenOn();
        }
        if (request.category() != null) {
            next.category = request.category();
        }
        if (request.comments() != null) {
            next.comments = comments(request.comments());
        }
        next.actionRequired = Boolean.TRUE.equals(request.actionRequired());
        old.current = false;
        old.status = FeedbackStatus.SUPERSEDED;
        old.updatedAt = now;
        feedback.saveAndFlush(old);
        feedback.saveAndFlush(next);
        recorder.change(PerformanceRecorder.FEEDBACK, next.id, next.teacherId, "CORRECTED", caller.userId(), "comments", old.comments, next.comments,
                "Version " + next.versionNo + ": " + why);
        return view(next, caller);
    }

    /** Withdrawn feedback stays in the history and no longer counts (FR-013). */
    @Transactional
    public FeedbackView withdraw(Caller caller, UUID id, String reason, Long version) {
        PrincipalFeedback record = current(caller, id);
        requireAuthor(caller, record);
        if (record.status != FeedbackStatus.RECORDED) {
            throw new ConflictException("This feedback is already " + record.status.name().toLowerCase() + ".");
        }
        StaleVersion.check(PrincipalFeedback.class, id, record.version, version);
        String why = required(reason, 5, 500, "The reason");
        record.status = FeedbackStatus.WITHDRAWN;
        record.withdrawnReason = why;
        record.withdrawnBy = caller.userId();
        record.withdrawnAt = clock.instant();
        record.updatedAt = record.withdrawnAt;
        feedback.saveAndFlush(record);
        recorder.change(PerformanceRecorder.FEEDBACK, record.id, record.teacherId, "WITHDRAWN", caller.userId(), "status", FeedbackStatus.RECORDED,
                FeedbackStatus.WITHDRAWN, why);
        return view(record, caller);
    }

    @Transactional(readOnly = true)
    public FeedbackView get(Caller caller, UUID id) {
        return view(visible(caller, id), caller);
    }

    @Transactional(readOnly = true)
    public List<FeedbackView> versions(Caller caller, UUID id) {
        if (!caller.orgWide()) {
            throw new ForbiddenFieldException("Only Admin or Director can read earlier versions.");
        }
        PrincipalFeedback record = visible(caller, id);
        return feedback.findByRootIdOrderByVersionNoDesc(record.rootId).stream().map(f -> view(f, caller)).toList();
    }

    PrincipalFeedback visible(Caller caller, UUID id) {
        PrincipalFeedback record = feedback.findById(id).orElseThrow(() -> new NotFoundException("Feedback not found."));
        scope.requireRecord(caller, record.teacherId, record.recordedBy, "Feedback");
        return record;
    }

    private PrincipalFeedback current(Caller caller, UUID id) {
        PrincipalFeedback record = visible(caller, id);
        if (!record.current) {
            throw new ConflictException("This is an earlier version. Open the current version of the feedback.");
        }
        return record;
    }

    FeedbackView view(PrincipalFeedback f, Caller caller) {
        TeacherContext teacher = facts.find(f.teacherId).orElse(null);
        PerformanceIssue issue = f.issueId == null ? null : issueRepository.findById(f.issueId).orElse(null);
        boolean recorded = f.status == FeedbackStatus.RECORDED && f.current;
        boolean author = caller.orgWide() || f.recordedBy.equals(caller.userId());
        boolean offer = recorded && f.actionRequired && (issue == null || !issue.isActive());
        return new FeedbackView(
                f.id,
                f.rootId,
                f.versionNo,
                f.current,
                f.teacherId,
                teacher == null ? null : teacher.name(),
                f.schoolId,
                facts.schoolNames(List.of(f.schoolId)).get(f.schoolId),
                f.principalName,
                f.givenOn,
                f.category.name(),
                f.category.label(),
                f.comments,
                f.actionRequired,
                f.observationRootId,
                f.recordedBy,
                facts.userName(f.recordedBy),
                f.status.name(),
                f.withdrawnReason,
                f.withdrawnAt,
                f.issueId,
                issue == null ? null : issue.status.name(),
                offer,
                f.correctionReason,
                new Actions(recorded && author, recorded && author, offer, recorded),
                f.version);
    }

    private String defaultPrincipal(UUID schoolId) {
        var view = contacts.contactsOf(schoolId);
        return view == null || view.principal() == null ? null : view.principal().name();
    }

    private static String comments(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() < MIN_COMMENTS) {
            throw new InvalidInputException("The comments must be at least " + MIN_COMMENTS + " characters.");
        }
        if (text.length() > PerformanceTypes.MAX_TEXT) {
            throw new InvalidInputException("The comments must be at most " + PerformanceTypes.MAX_TEXT + " characters.");
        }
        return text;
    }

    private static void requireAuthor(Caller caller, PrincipalFeedback f) {
        if (!caller.orgWide() && !f.recordedBy.equals(caller.userId())) {
            throw new ForbiddenFieldException("Only the person who recorded the feedback, Admin or Director can change it.");
        }
    }

    private void requireNotFuture(LocalDate date) {
        if (date.isAfter(businessDate.today())) {
            throw new InvalidInputException("The date cannot be in the future.");
        }
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
