package com.hls.leave.internal;

import com.hls.attendance.api.LeaveAttendance;
import com.hls.attendance.api.LeaveAttendance.LeaveDay;
import com.hls.leave.api.LeaveCancelled;
import com.hls.leave.api.LeaveDecided;
import com.hls.leave.api.LeaveTypes;
import com.hls.leave.api.LeaveRequested;
import com.hls.leave.internal.LeaveViewFactory.LeaveView;
import com.hls.leave.internal.LeaveViewFactory.Perspective;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.PageResponse;
import com.hls.teacher.api.TeacherDirectory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A Teacher's side of leave (spec 009 US1, US2): preview, apply, own history and cancel. */
@Service
public class LeaveRequestService {

    public static final int MAX_REASON = 500;
    public static final int MAX_SPAN_DAYS = 90;
    public static final int LOOKBACK_DAYS = 30;

    /** What the Teacher typed. */
    public record Draft(
            UUID leaveTypeId, LocalDate firstDate, LocalDate lastDate, boolean halfDayStart, boolean halfDayEnd, String reason) {}

    /** Why a draft cannot be submitted: {@code invalid} is a 400, otherwise a 409. */
    public record Problem(boolean invalid, String text) {}

    public record Evaluation(List<LeaveDay> days, BigDecimal workingDays, List<Problem> problems) {}

    public record PreviewDay(LocalDate date, BigDecimal value) {}

    public record Preview(
            BigDecimal workingDays, List<PreviewDay> days, List<String> problems, boolean takesEffectAtOnce) {}

    private final LeaveRequestRepository requests;
    private final LeaveTypeCatalog types;
    private final LeaveAttendance attendance;
    private final TeacherDirectory teachers;
    private final LeaveViewFactory views;
    private final LeaveAudit audit;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final LeaveTypes leaveTypes;

    public LeaveRequestService(
            LeaveRequestRepository requests,
            LeaveTypeCatalog types,
            LeaveAttendance attendance,
            TeacherDirectory teachers,
            LeaveViewFactory views,
            LeaveAudit audit,
            Clock clock,
            ApplicationEventPublisher events,
            LeaveTypes leaveTypes) {
        this.requests = requests;
        this.types = types;
        this.attendance = attendance;
        this.teachers = teachers;
        this.views = views;
        this.audit = audit;
        this.clock = clock;
        this.events = events;
        this.leaveTypes = leaveTypes;
    }

    @Transactional(readOnly = true)
    public List<LeaveType> activeTypes() {
        return types.active();
    }

    /** The Teacher record of the signed-in user, or a 404 when the profile is not set up. */
    @Transactional(readOnly = true)
    public UUID ownTeacherId(UUID userId) {
        return teachers.teacherOfUser(userId)
                .orElseThrow(() -> new NotFoundException("Your profile has not been set up yet."))
                .id();
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID userId, Draft draft) {
        Evaluation evaluation = evaluate(ownTeacherId(userId), draft);
        return new Preview(
                evaluation.workingDays(),
                evaluation.days().stream().map(d -> new PreviewDay(d.date(), d.value())).toList(),
                evaluation.problems().stream().map(Problem::text).toList(),
                // spec 040: a request that starts on a working day today is approved the moment it is saved
                evaluation.problems().isEmpty()
                        && !evaluation.days().isEmpty()
                        && evaluation.days().get(0).date().equals(attendance.businessToday()));
    }

    @Transactional
    public LeaveView submit(UUID userId, Draft draft) {
        return submitFor(ownTeacherId(userId), userId, draft, Perspective.OWN);
    }

    /**
     * Saves a request for the Teacher, made by {@code recorderUserId} (the Teacher, or a supervisor recording for them).
     * A request that starts today and has today as a working day is same-day (spec 040): today's part is Approved at once,
     * and any later days become a second, Pending request linked to it.
     */
    @Transactional
    public LeaveView submitFor(UUID teacherId, UUID recorderUserId, Draft draft, Perspective perspective) {
        Evaluation evaluation = evaluate(teacherId, draft);
        requireNoProblems(evaluation);
        LocalDate today = attendance.businessToday();
        boolean sameDay = draft.firstDate().equals(today) && evaluation.days().get(0).date().equals(today);
        if (!sameDay) {
            LeaveRequest saved = insert(newRequest(teacherId, draft, evaluation, recorderUserId));
            audit.created(recorderUserId, saved);
            publishRequested(saved, teacherId);
            return views.of(saved, perspective);
        }
        return submitSameDay(teacherId, recorderUserId, draft, evaluation, today, perspective);
    }

    private LeaveView submitSameDay(
            UUID teacherId, UUID recorder, Draft draft, Evaluation whole, LocalDate today, Perspective perspective) {
        boolean runsPastToday = draft.lastDate().isAfter(today);
        Draft todayDraft = runsPastToday
                ? new Draft(draft.leaveTypeId(), today, today, draft.halfDayStart(), false, draft.reason())
                : draft;
        Evaluation todayPart = runsPastToday ? evaluate(teacherId, todayDraft) : whole;
        requireNoProblems(todayPart);

        LeaveRequest sameDayRequest = LeaveRequest.approvedSameDay(
                teacherId,
                todayPart.days().get(0).schoolId(),
                todayDraft.leaveTypeId(),
                today,
                todayDraft.lastDate(),
                todayDraft.halfDayStart(),
                todayDraft.halfDayEnd(),
                todayPart.workingDays(),
                draft.reason().trim(),
                recorder,
                clock.instant());
        LeaveRequest saved = insert(sameDayRequest);
        // replaceAbsent: a day a supervisor marked Absent earlier today becomes this leave (spec 040 FR-003)
        attendance.apply(saved.getId(), recorder, teacherId, todayPart.days(), leaveTypes.isLossOfPay(saved.getId()), true);
        audit.created(recorder, saved);
        audit.sameDay(recorder, saved);
        publishRequested(saved, teacherId);
        events.publishEvent(new LeaveDecided(
                saved.getId(), teacherId, LeaveDecided.Decision.APPROVED, saved.getFirstDate(), saved.getLastDate(), null));

        if (runsPastToday) {
            Draft laterDraft = new Draft(
                    draft.leaveTypeId(), today.plusDays(1), draft.lastDate(), false, draft.halfDayEnd(), draft.reason());
            Evaluation later = evaluate(teacherId, laterDraft);
            // later days with no working day (a weekend, say) leave nothing to approve
            if (!later.days().isEmpty()) {
                requireNoProblems(later);
                LeaveRequest laterRequest = newRequest(teacherId, laterDraft, later, recorder);
                laterRequest.linkTo(saved.getId());
                LeaveRequest savedLater = insert(laterRequest);
                audit.created(recorder, savedLater);
                publishRequested(savedLater, teacherId);
            }
        }
        return views.of(saved, perspective);
    }

    private static void requireNoProblems(Evaluation evaluation) {
        if (!evaluation.problems().isEmpty()) {
            Problem first = evaluation.problems().get(0);
            if (first.invalid()) {
                throw new InvalidInputException(first.text());
            }
            throw new ConflictException(first.text());
        }
    }

    private LeaveRequest newRequest(UUID teacherId, Draft draft, Evaluation evaluation, UUID createdBy) {
        return new LeaveRequest(
                teacherId,
                evaluation.days().get(0).schoolId(),
                draft.leaveTypeId(),
                draft.firstDate(),
                draft.lastDate(),
                draft.halfDayStart(),
                draft.halfDayEnd(),
                evaluation.workingDays(),
                draft.reason().trim(),
                createdBy,
                clock.instant());
    }

    private LeaveRequest insert(LeaveRequest request) {
        try {
            return requests.saveAndFlush(request);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            // Two simultaneous overlapping submissions can also deadlock on the exclusion constraint
            // (Postgres 40P01); the survivor is saved and the loser is refused like any other overlap.
            throw new ConflictException("These dates overlap another leave request of yours.");
        }
    }

    private void publishRequested(LeaveRequest saved, UUID teacherId) {
        events.publishEvent(new LeaveRequested(
                saved.getId(),
                teacherId,
                saved.getSchoolId(),
                saved.getFirstDate(),
                saved.getLastDate(),
                teacherName(teacherId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveView> listOwn(UUID userId, LeaveStatus status, int page, int size) {
        UUID teacherId = ownTeacherId(userId);
        var statuses = status == null ? EnumSet.allOf(LeaveStatus.class) : EnumSet.of(status);
        var result = requests.findOwn(teacherId, statuses, PageRequest.of(page, Math.min(Math.max(size, 1), 100)));
        return new PageResponse<>(
                views.ofAll(result.getContent(), Perspective.OWN), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /** Cancels the Teacher's own request: Pending, or Approved while its first day is still ahead (US2, US4). */
    @Transactional
    public LeaveView cancelOwn(UUID userId, UUID id) {
        UUID teacherId = ownTeacherId(userId);
        LeaveRequest request = requests.findByIdForUpdate(id)
                .filter(r -> r.getTeacherId().equals(teacherId))
                .orElseThrow(() -> new NotFoundException("Leave request not found."));
        LeaveStatus before = request.getStatus();
        if (before == LeaveStatus.APPROVED) {
            boolean sameDayToday = request.isSameDay() && request.getFirstDate().equals(attendance.businessToday());
            if (!sameDayToday && !request.getFirstDate().isAfter(attendance.businessToday())) {
                throw new ConflictException("This leave has already started. Ask your Manager to revoke it.");
            }
            attendance.remove(request.getId(), userId);
        } else if (before != LeaveStatus.PENDING) {
            throw new ConflictException(before == LeaveStatus.CANCELLED
                    ? "This request was already cancelled."
                    : "A " + before.name().toLowerCase() + " request cannot be cancelled.");
        }
        request.cancel("TEACHER", userId, null, clock.instant());
        LeaveRequest saved = requests.saveAndFlush(request);
        audit.status(userId, saved, before, "cancelled by the Teacher");
        events.publishEvent(new LeaveCancelled(
                saved.getId(),
                teacherId,
                saved.getSchoolId(),
                saved.getFirstDate(),
                saved.getLastDate(),
                teacherName(teacherId),
                before == LeaveStatus.APPROVED));
        return views.of(saved, Perspective.OWN);
    }

    private String teacherName(UUID teacherId) {
        var info = teachers.teacherInfo(List.of(teacherId)).get(teacherId);
        return info == null ? "A Teacher" : info.name();
    }

    /** Works out the days a draft would cover and every reason it cannot be submitted. */
    @Transactional(readOnly = true)
    public Evaluation evaluate(UUID teacherId, Draft draft) {
        List<Problem> problems = new ArrayList<>();
        if (draft.leaveTypeId() == null || types.find(draft.leaveTypeId()).filter(LeaveType::isActive).isEmpty()) {
            problems.add(new Problem(true, "Choose a leave type."));
        }
        if (draft.firstDate() == null || draft.lastDate() == null) {
            problems.add(new Problem(true, "Choose the first and last date."));
            return new Evaluation(List.of(), BigDecimal.ZERO, problems);
        }
        if (draft.lastDate().isBefore(draft.firstDate())) {
            problems.add(new Problem(true, "The last date cannot be before the first date."));
            return new Evaluation(List.of(), BigDecimal.ZERO, problems);
        }
        if (draft.reason() == null || draft.reason().isBlank()) {
            problems.add(new Problem(true, "Give a reason."));
        } else if (draft.reason().trim().length() > MAX_REASON) {
            problems.add(new Problem(true, "The reason can be at most " + MAX_REASON + " characters."));
        }
        if (draft.lastDate().toEpochDay() - draft.firstDate().toEpochDay() >= MAX_SPAN_DAYS) {
            problems.add(new Problem(true, "A request can cover at most " + MAX_SPAN_DAYS + " days. Split it into two."));
            return new Evaluation(List.of(), BigDecimal.ZERO, problems);
        }
        if (draft.firstDate().isBefore(attendance.businessToday().minusDays(LOOKBACK_DAYS))) {
            problems.add(new Problem(
                    false, "Leave can start at most " + LOOKBACK_DAYS + " days in the past. Ask your Manager to record older days."));
        }
        if (teachers.placementsOverlapping(List.of(teacherId), draft.firstDate(), draft.lastDate()).isEmpty()) {
            problems.add(new Problem(false, "You are not placed at a School on these dates, so you cannot apply for leave."));
            return new Evaluation(List.of(), BigDecimal.ZERO, problems);
        }
        var counted = LeaveCounter.count(
                attendance.workingDays(teacherId, draft.firstDate(), draft.lastDate()),
                draft.halfDayStart(),
                draft.halfDayEnd());
        if (!counted.ok()) {
            problems.add(new Problem(false, counted.problem()));
            return new Evaluation(List.of(), BigDecimal.ZERO, problems);
        }
        for (LeaveRequest other : requests.findLiveOverlapping(teacherId, draft.firstDate(), draft.lastDate())) {
            problems.add(new Problem(
                    false,
                    "These dates overlap your " + other.getStatus().name().toLowerCase() + " request from "
                            + other.getFirstDate() + " to " + other.getLastDate() + "."));
        }
        attendance.problems(teacherId, counted.days()).stream()
                .filter(p -> p.startsWith("month locked"))
                .forEach(p -> problems.add(new Problem(false, "Attendance is locked for a month in this range (" + p + ").")));
        return new Evaluation(counted.days(), counted.total(), problems);
    }
}
