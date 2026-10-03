package com.hls.teacher.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interim Teacher-School placements (spec 005 FR-012, research.md section 6): dated rows, a future
 * date schedules a move that takes effect when the date arrives (no scheduler: "current" is always
 * evaluated against today), history is never overwritten, and placements never overlap.
 */
@Service
public class TeacherPlacementService {

    private final TeacherRepository teacherRepository;
    private final TeacherPlacementRepository placementRepository;
    private final SchoolDirectory schoolDirectory;
    private final ChangeRecorder changes;
    private final Clock clock;

    public TeacherPlacementService(
            TeacherRepository teacherRepository,
            TeacherPlacementRepository placementRepository,
            SchoolDirectory schoolDirectory,
            ChangeRecorder changes,
            Clock clock) {
        this.teacherRepository = teacherRepository;
        this.placementRepository = placementRepository;
        this.schoolDirectory = schoolDirectory;
        this.changes = changes;
        this.clock = clock;
    }

    /** Places or moves the Teacher to {@code schoolId} effective {@code effectiveOn} (default today). */
    @Transactional
    public void place(UUID actor, UUID teacherId, UUID schoolId, LocalDate effectiveOn) {
        Teacher teacher =
                teacherRepository.findById(teacherId).orElseThrow(() -> new NotFoundException("Teacher not found."));
        if (teacher.getStatus() == TeacherStatus.EXITED) {
            throw new ConflictException("An exited Teacher cannot be placed in a School.");
        }
        if (schoolId == null) {
            throw new InvalidInputException("A School is required.");
        }
        SchoolInfo school =
                schoolDirectory.school(schoolId).orElseThrow(() -> new InvalidInputException("School not found."));
        if (!school.active()) {
            throw new ConflictException("This School is inactive.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = effectiveOn == null ? today : effectiveOn;

        List<TeacherPlacement> active =
                placementRepository.findByTeacherIdAndStatusOrderByStartsOnDesc(teacherId, PlacementStatus.ACTIVE);
        Optional<TeacherPlacement> current =
                active.stream().filter(p -> p.isInEffectOn(today)).findFirst();
        Optional<TeacherPlacement> pending =
                active.stream().filter(p -> p.getStartsOn().isAfter(today)).findFirst();
        UUID before = current.map(TeacherPlacement::getSchoolId).orElse(null);
        if (current.isPresent() && current.get().getSchoolId().equals(schoolId) && pending.isEmpty()) {
            throw new ConflictException("The Teacher is already placed in this School.");
        }

        // a new instruction replaces any scheduled move; restore the current placement first
        if (pending.isPresent()) {
            cancelPendingRow(pending.get(), current.orElse(null));
            placementRepository.flush();
        }
        // re-read the current row (its end date may just have been cleared)
        if (start.isAfter(today)) {
            schedule(teacherId, schoolId, start, current.orElse(null));
        } else {
            moveNow(teacherId, schoolId, start, current.orElse(null), active);
        }
        flush();
        changes.record(actor, "TEACHER_PLACEMENT", teacherId, "school", before, schoolId);
        changes.record(actor, "TEACHER_PLACEMENT", teacherId, "effectiveOn", null, start);
    }

    /** Cancels the scheduled future move and restores the current placement's open end. */
    @Transactional
    public void cancelPending(UUID actor, UUID teacherId) {
        teacherRepository.findById(teacherId).orElseThrow(() -> new NotFoundException("Teacher not found."));
        LocalDate today = LocalDate.now(clock);
        List<TeacherPlacement> active =
                placementRepository.findByTeacherIdAndStatusOrderByStartsOnDesc(teacherId, PlacementStatus.ACTIVE);
        TeacherPlacement pending = active.stream()
                .filter(p -> p.getStartsOn().isAfter(today))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("There is no scheduled move to cancel."));
        TeacherPlacement current =
                active.stream().filter(p -> p.isInEffectOn(today)).findFirst().orElse(null);
        UUID scheduledSchool = pending.getSchoolId();
        cancelPendingRow(pending, current);
        flush();
        changes.record(actor, "TEACHER_PLACEMENT", teacherId, "scheduledSchool", scheduledSchool, null);
    }

    /** Ends the current placement on the exit date and cancels a scheduled one (Teacher exit). */
    @Transactional
    public void endForExit(UUID teacherId, LocalDate exitDate) {
        LocalDate today = LocalDate.now(clock);
        List<TeacherPlacement> active =
                placementRepository.findByTeacherIdAndStatusOrderByStartsOnDesc(teacherId, PlacementStatus.ACTIVE);
        TeacherPlacement current =
                active.stream().filter(p -> p.isInEffectOn(today)).findFirst().orElse(null);
        for (TeacherPlacement row : active) {
            if (row.getStartsOn().isAfter(today) || row.getStartsOn().isAfter(exitDate)) {
                row.setStatus(PlacementStatus.CANCELLED);
                row.setEndsOn(null);
                placementRepository.save(row);
            }
        }
        if (current != null && current.getStatus() == PlacementStatus.ACTIVE) {
            if (exitDate.isAfter(current.getStartsOn())) {
                // the Teacher is no longer placed from the exit date itself
                current.setEndsOn(exitDate.minusDays(1));
            } else {
                // the exit predates or equals the placement start: it never took effect
                current.setStatus(PlacementStatus.CANCELLED);
                current.setEndsOn(null);
            }
            placementRepository.save(current);
        }
        flush();
    }

    private void schedule(UUID teacherId, UUID schoolId, LocalDate start, TeacherPlacement current) {
        if (current != null) {
            current.setEndsOn(start.minusDays(1));
            placementRepository.saveAndFlush(current);
        }
        placementRepository.save(new TeacherPlacement(teacherId, schoolId, start, clock.instant()));
    }

    private void moveNow(
            UUID teacherId,
            UUID schoolId,
            LocalDate start,
            TeacherPlacement current,
            List<TeacherPlacement> allActive) {
        if (current != null) {
            if (start.isBefore(current.getStartsOn())) {
                throw new ConflictException(
                        "The date cannot be earlier than the start of the current placement ("
                                + current.getStartsOn() + ").");
            }
            if (start.equals(current.getStartsOn())) {
                current.setStatus(PlacementStatus.CORRECTED);
            } else {
                current.setEndsOn(start.minusDays(1));
            }
            placementRepository.saveAndFlush(current);
        } else {
            LocalDate lastEnd = allActive.stream()
                    .filter(p -> p.getEndsOn() != null)
                    .map(TeacherPlacement::getEndsOn)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            if (lastEnd != null && !start.isAfter(lastEnd)) {
                throw new ConflictException("The date overlaps an earlier placement that ended on " + lastEnd + ".");
            }
        }
        placementRepository.save(new TeacherPlacement(teacherId, schoolId, start, clock.instant()));
    }

    private void cancelPendingRow(TeacherPlacement pending, TeacherPlacement current) {
        pending.setStatus(PlacementStatus.CANCELLED);
        placementRepository.save(pending);
        if (current != null
                && current.getEndsOn() != null
                && current.getEndsOn().equals(pending.getStartsOn().minusDays(1))) {
            current.setEndsOn(null);
            placementRepository.save(current);
        }
    }

    private void flush() {
        try {
            placementRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This placement overlaps another placement of the same Teacher.");
        }
    }
}
