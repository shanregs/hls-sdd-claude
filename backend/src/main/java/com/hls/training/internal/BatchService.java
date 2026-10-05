package com.hls.training.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import com.hls.teacher.api.TeacherRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Induction batches and who is in them. Enrolment takes a row lock on the batch so two requests for the last seat let
 * one through, and the database refuses a Teacher in two batches on overlapping dates.
 */
@Service
public class BatchService {

    public record BatchRequest(String name, LocalDate startsOn, LocalDate endsOn, String trainer, String venueType, String venue, Integer seatLimit) {}

    public record EnrolRequest(UUID teacherId) {}

    public record BatchDto(
            UUID id,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            String trainer,
            String venueType,
            String venue,
            int seatLimit,
            long enrolled,
            String status,
            Long version) {}

    public record RecruitRef(UUID teacherId, String name, String status) {}

    private final InductionBatchRepository batches;
    private final InductionEnrolmentRepository enrolments;
    private final TeacherDirectory teacherDirectory;
    private final TeacherRegistry registry;
    private final ChangeRecorder changes;
    private final Clock clock;

    public BatchService(
            InductionBatchRepository batches,
            InductionEnrolmentRepository enrolments,
            TeacherDirectory teacherDirectory,
            TeacherRegistry registry,
            ChangeRecorder changes,
            Clock clock) {
        this.batches = batches;
        this.enrolments = enrolments;
        this.teacherDirectory = teacherDirectory;
        this.registry = registry;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BatchDto> list() {
        List<InductionBatch> rows = batches.findAllByOrderByStartsOnDesc();
        Map<UUID, Long> counts = enrolments.findByBatchIdIn(rows.stream().map(InductionBatch::getId).toList()).stream()
                .collect(Collectors.groupingBy(InductionEnrolment::getBatchId, Collectors.counting()));
        return rows.stream().map(b -> view(b, counts.getOrDefault(b.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public BatchDto get(UUID id) {
        InductionBatch batch = find(id);
        return view(batch, enrolments.countByBatchId(id));
    }

    @Transactional
    public BatchDto create(UUID actor, BatchRequest request) {
        String name = text(request.name(), 120, "Name", true);
        if (request.startsOn() == null || request.endsOn() == null) {
            throw new InvalidInputException("The start and end dates are required.");
        }
        if (request.endsOn().isBefore(request.startsOn())) {
            throw new InvalidInputException("The end date cannot be before the start date.");
        }
        if (request.seatLimit() == null || request.seatLimit() < 1) {
            throw new InvalidInputException("The seat limit must be at least 1.");
        }
        String venueType = request.venueType() == null ? "PHYSICAL" : request.venueType().trim().toUpperCase();
        if (!venueType.equals("PHYSICAL") && !venueType.equals("VIRTUAL")) {
            throw new InvalidInputException("The venue type must be PHYSICAL or VIRTUAL.");
        }
        InductionBatch batch = batches.saveAndFlush(new InductionBatch(
                name,
                request.startsOn(),
                request.endsOn(),
                text(request.trainer(), 160, "Trainer", false),
                venueType,
                text(request.venue(), 200, "Venue", false),
                request.seatLimit(),
                actor,
                clock.instant()));
        changes.recordLifecycle(actor, "INDUCTION_BATCH", batch.getId(), "created", name + " " + batch.getStartsOn() + ".." + batch.getEndsOn());
        return view(batch, 0);
    }

    /** Cancels a batch that has no attendance; its recruits go back to the "to be enrolled" list. */
    @Transactional
    public BatchDto cancel(UUID actor, UUID id) {
        InductionBatch batch = batches.findForUpdate(id).orElseThrow(() -> new NotFoundException("Batch not found."));
        if (batch.isCancelled()) {
            throw new ConflictException("The batch is already cancelled.");
        }
        List<InductionEnrolment> own = enrolments.findByBatchIdOrderByEnrolledAt(id);
        if (own.stream().anyMatch(InductionEnrolment::isSignedOff)) {
            throw new ConflictException("A batch with signed-off recruits cannot be cancelled.");
        }
        own.forEach(e -> changes.record(actor, "INDUCTION_ENROLMENT", e.getId(), "released", "enrolled", "batch cancelled"));
        enrolments.deleteAll(own);
        batch.cancel();
        batches.saveAndFlush(batch);
        changes.record(actor, "INDUCTION_BATCH", id, "status", "PLANNED", "CANCELLED");
        return view(batch, 0);
    }

    /** Enrols a recruit (a Teacher in training) in the batch, under the batch row lock. */
    @Transactional
    public InductionEnrolment enrol(UUID actor, UUID batchId, UUID teacherId) {
        InductionBatch batch = batches.findForUpdate(batchId).orElseThrow(() -> new NotFoundException("Batch not found."));
        return enrolLocked(actor, batch, teacherId);
    }

    InductionEnrolment enrolLocked(UUID actor, InductionBatch batch, UUID teacherId) {
        if (batch.isCancelled()) {
            throw new ConflictException("This batch was cancelled.");
        }
        if (batch.getEndsOn().isBefore(LocalDate.now(clock))) {
            throw new ConflictException("This batch has already ended.");
        }
        TeacherInfo teacher = teacherDirectory.teacherInfo(List.of(teacherId)).get(teacherId);
        if (teacher == null) {
            throw new NotFoundException("Teacher not found.");
        }
        if (!"IN_TRAINING".equals(teacher.status())) {
            throw new ConflictException("Only a recruit in training can be enrolled in a batch.");
        }
        if (enrolments.countByBatchId(batch.getId()) >= batch.getSeatLimit()) {
            throw new ConflictException("This batch is full (" + batch.getSeatLimit() + " seats).");
        }
        if (!enrolments.overlapping(teacherId, batch.getStartsOn(), batch.getEndsOn()).isEmpty()) {
            throw new ConflictException("This recruit is already in a batch on overlapping dates.");
        }
        try {
            InductionEnrolment saved = enrolments.saveAndFlush(
                    new InductionEnrolment(batch.getId(), teacherId, batch.getStartsOn(), batch.getEndsOn(), actor, clock.instant()));
            changes.recordLifecycle(actor, "INDUCTION_ENROLMENT", saved.getId(), "created", teacher.name() + " in " + batch.getName());
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This recruit is already in this batch or in another on overlapping dates.");
        }
    }

    /** The earliest batch that has not ended, is not cancelled and has a free seat, excluding {@code except}. */
    Optional<InductionBatch> nextWithRoom(UUID except) {
        for (InductionBatch candidate : batches.openFrom(LocalDate.now(clock))) {
            if (candidate.getId().equals(except)) {
                continue;
            }
            if (enrolments.countByBatchId(candidate.getId()) < candidate.getSeatLimit()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Recruits in training who are in no batch: waiting for a batch, or just released from a cancelled one. */
    @Transactional(readOnly = true)
    public List<RecruitRef> toBeEnrolled() {
        Set<UUID> inTraining = registry.inTrainingTeacherIds();
        Set<UUID> held = new HashSet<>();
        for (UUID id : inTraining) {
            boolean engaged = enrolments.findByTeacherId(id).stream()
                    .anyMatch(e -> e.getResult() == null || ("NOT_COMPLETED".equals(e.getResult()) && e.getFollowUp() == null));
            if (engaged) {
                held.add(id);
            }
        }
        List<UUID> waiting = inTraining.stream().filter(id -> !held.contains(id)).toList();
        Map<UUID, TeacherInfo> infos = teacherDirectory.teacherInfo(waiting);
        List<RecruitRef> out = new ArrayList<>();
        for (UUID id : waiting) {
            TeacherInfo info = infos.get(id);
            if (info != null) {
                out.add(new RecruitRef(id, info.name(), info.status()));
            }
        }
        out.sort(Comparator.comparing(RecruitRef::name));
        return out;
    }

    /** Active Teachers who completed an induction and have no School assignment today. */
    @Transactional(readOnly = true)
    public List<RecruitRef> readyToDeploy() {
        LocalDate today = LocalDate.now(clock);
        Set<UUID> ready = new HashSet<>(enrolments.completedTeacherIds());
        ready.retainAll(registry.activeTeacherIds());
        ready.removeAll(teacherDirectory.teachersPlacedDuring(today, today));
        Map<UUID, TeacherInfo> infos = teacherDirectory.teacherInfo(ready);
        return ready.stream()
                .map(infos::get)
                .filter(java.util.Objects::nonNull)
                .map(i -> new RecruitRef(i.id(), i.name(), i.status()))
                .sorted(Comparator.comparing(RecruitRef::name))
                .toList();
    }

    InductionBatch find(UUID id) {
        return batches.findById(id).orElseThrow(() -> new NotFoundException("Batch not found."));
    }

    BatchDto view(InductionBatch b, long enrolled) {
        LocalDate today = LocalDate.now(clock);
        String status = b.isCancelled()
                ? "CANCELLED"
                : b.getEndsOn().isBefore(today) ? "COMPLETED" : b.getStartsOn().isAfter(today) ? "PLANNED" : "RUNNING";
        return new BatchDto(
                b.getId(),
                b.getName(),
                b.getStartsOn(),
                b.getEndsOn(),
                b.getTrainer(),
                b.getVenueType(),
                b.getVenue(),
                b.getSeatLimit(),
                enrolled,
                status,
                b.getVersion());
    }

    private static String text(String value, int max, String label, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) {
                throw new InvalidInputException(label + " is required.");
            }
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new InvalidInputException(label + " must be at most " + max + " characters.");
        }
        return trimmed;
    }
}
