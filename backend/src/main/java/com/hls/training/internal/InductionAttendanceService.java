package com.hls.training.internal;

import com.hls.attendance.api.AttendanceReadApi;
import com.hls.attendance.api.MarkView;
import com.hls.attendance.api.TrainingAttendance;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Induction attendance. A present or half day is a training-day mark in the attendance records (the single source of
 * truth, written with no School); an absence is kept here with its reason. Corrections keep history in attendance.
 */
@Service
public class InductionAttendanceService {

    public record AttendanceRequest(LocalDate date, String status, String reason) {}

    public record DayRow(LocalDate date, String status, BigDecimal dayValue, String reason) {}

    public record RosterRow(
            UUID enrolmentId,
            UUID teacherId,
            String name,
            String result,
            String remarks,
            String followUp,
            List<DayRow> days) {}

    private final InductionEnrolmentRepository enrolments;
    private final InductionAbsenceRepository absences;
    private final BatchService batches;
    private final TrainingAttendance trainingAttendance;
    private final AttendanceReadApi attendanceRead;
    private final TeacherDirectory teacherDirectory;
    private final ChangeRecorder changes;
    private final Clock clock;

    public InductionAttendanceService(
            InductionEnrolmentRepository enrolments,
            InductionAbsenceRepository absences,
            BatchService batches,
            TrainingAttendance trainingAttendance,
            AttendanceReadApi attendanceRead,
            TeacherDirectory teacherDirectory,
            ChangeRecorder changes,
            Clock clock) {
        this.enrolments = enrolments;
        this.absences = absences;
        this.batches = batches;
        this.trainingAttendance = trainingAttendance;
        this.attendanceRead = attendanceRead;
        this.teacherDirectory = teacherDirectory;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional
    public DayRow record(UUID actor, UUID enrolmentId, AttendanceRequest request) {
        InductionEnrolment enrolment = enrolments.findById(enrolmentId).orElseThrow(() -> new NotFoundException("Enrolment not found."));
        if (enrolment.isSignedOff()) {
            throw new ConflictException("This recruit has been signed off; attendance can no longer change.");
        }
        LocalDate date = request.date();
        if (date == null) {
            throw new InvalidInputException("The date is required.");
        }
        if (date.isBefore(enrolment.getStartsOn()) || date.isAfter(enrolment.getEndsOn())) {
            throw new InvalidInputException("The date is outside the batch dates.");
        }
        if (date.isAfter(LocalDate.now(clock))) {
            throw new ConflictException("You cannot record attendance for a future date.");
        }
        String status = request.status() == null ? "" : request.status().trim().toUpperCase();
        UUID teacher = enrolment.getTeacherId();
        switch (status) {
            case "PRESENT", "HALF" -> {
                absences.findByEnrolmentIdAndAbsentOn(enrolmentId, date).ifPresent(absences::delete);
                absences.flush();
                BigDecimal value = status.equals("PRESENT") ? new BigDecimal("1.00") : new BigDecimal("0.50");
                trainingAttendance.markTrainingDay(actor, teacher, date, value);
                changes.record(actor, "INDUCTION_ATTENDANCE", enrolmentId + ":" + date, "status", null, status);
                return new DayRow(date, status, value, null);
            }
            case "ABSENT" -> {
                String reason = request.reason() == null ? "" : request.reason().trim();
                if (reason.isEmpty()) {
                    throw new InvalidInputException("A reason is required for an absence.");
                }
                if (reason.length() > 300) {
                    throw new InvalidInputException("The reason must be at most 300 characters.");
                }
                trainingAttendance.clearTrainingDay(actor, teacher, date);
                absences.findByEnrolmentIdAndAbsentOn(enrolmentId, date).ifPresent(absences::delete);
                absences.flush();
                absences.save(new InductionAbsence(enrolmentId, date, reason, actor, clock.instant()));
                changes.record(actor, "INDUCTION_ATTENDANCE", enrolmentId + ":" + date, "status", null, "ABSENT");
                return new DayRow(date, "ABSENT", BigDecimal.ZERO, reason);
            }
            default -> throw new InvalidInputException("The status must be PRESENT, HALF or ABSENT.");
        }
    }

    @Transactional(readOnly = true)
    public List<RosterRow> roster(UUID batchId) {
        InductionBatch batch = batches.find(batchId);
        List<InductionEnrolment> rows = enrolments.findByBatchIdOrderByEnrolledAt(batchId);
        Map<UUID, TeacherInfo> infos = teacherDirectory.teacherInfo(rows.stream().map(InductionEnrolment::getTeacherId).toList());
        Map<UUID, List<InductionAbsence>> absent = absences.findByEnrolmentIdIn(rows.stream().map(InductionEnrolment::getId).toList()).stream()
                .collect(Collectors.groupingBy(InductionAbsence::getEnrolmentId));
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = YearMonth.from(batch.getStartsOn()); !m.isAfter(YearMonth.from(batch.getEndsOn())); m = m.plusMonths(1)) {
            months.add(m);
        }
        List<RosterRow> out = new ArrayList<>();
        for (InductionEnrolment e : rows) {
            Map<LocalDate, DayRow> days = new HashMap<>();
            for (YearMonth month : months) {
                for (MarkView mark : attendanceRead.marksOf(e.getTeacherId(), month)) {
                    if ("TRAINING".equals(mark.category()) && !mark.date().isBefore(e.getStartsOn()) && !mark.date().isAfter(e.getEndsOn())) {
                        days.put(mark.date(), new DayRow(mark.date(), mark.dayValue().compareTo(BigDecimal.ONE) == 0 ? "PRESENT" : "HALF", mark.dayValue(), null));
                    }
                }
            }
            for (InductionAbsence a : absent.getOrDefault(e.getId(), List.of())) {
                days.put(a.getAbsentOn(), new DayRow(a.getAbsentOn(), "ABSENT", BigDecimal.ZERO, a.getReason()));
            }
            TeacherInfo info = infos.get(e.getTeacherId());
            out.add(new RosterRow(
                    e.getId(),
                    e.getTeacherId(),
                    info == null ? "Unknown" : info.name(),
                    e.getResult(),
                    e.getRemarks(),
                    e.getFollowUp(),
                    days.values().stream().sorted(Comparator.comparing(DayRow::date)).toList()));
        }
        return out;
    }
}
