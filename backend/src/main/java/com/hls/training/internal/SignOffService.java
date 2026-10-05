package com.hls.training.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import com.hls.teacher.api.TeacherRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-off of an induction, and what happens to a recruit who did not complete it. */
@Service
public class SignOffService {

    public record SignOffRequest(String result, String remarks) {}

    public record FollowUpRequest(String action, String reason) {}

    public record EnrolmentDto(UUID id, UUID batchId, UUID teacherId, String result, String remarks, String followUp) {}

    private final InductionEnrolmentRepository enrolments;
    private final InductionBatchRepository batches;
    private final BatchService batchService;
    private final TeacherRegistry registry;
    private final ChangeRecorder changes;
    private final Clock clock;

    public SignOffService(
            InductionEnrolmentRepository enrolments,
            InductionBatchRepository batches,
            BatchService batchService,
            TeacherRegistry registry,
            ChangeRecorder changes,
            Clock clock) {
        this.enrolments = enrolments;
        this.batches = batches;
        this.batchService = batchService;
        this.registry = registry;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional
    public EnrolmentDto signOff(UUID actor, UUID enrolmentId, SignOffRequest request) {
        InductionEnrolment enrolment = find(enrolmentId);
        String result = request.result() == null ? "" : request.result().trim().toUpperCase();
        if (!result.equals("COMPLETED") && !result.equals("NOT_COMPLETED")) {
            throw new InvalidInputException("The result must be COMPLETED or NOT_COMPLETED.");
        }
        if (enrolment.isSignedOff()) {
            throw new ConflictException("This recruit has already been signed off.");
        }
        InductionBatch batch = batchService.find(enrolment.getBatchId());
        if (batch.getStartsOn().isAfter(LocalDate.now(clock))) {
            throw new ConflictException("The batch has not started yet.");
        }
        String remarks = request.remarks() == null || request.remarks().isBlank() ? null : request.remarks().trim();
        if (remarks != null && remarks.length() > 300) {
            throw new InvalidInputException("The remarks must be at most 300 characters.");
        }
        enrolment.signOff(result, remarks, actor, clock.instant());
        enrolments.saveAndFlush(enrolment);
        if (result.equals("COMPLETED")) {
            registry.activate(actor, enrolment.getTeacherId());
        }
        changes.record(actor, "INDUCTION_SIGNOFF", enrolmentId, "result", null, result);
        return view(enrolment);
    }

    /** After NOT_COMPLETED: enrol in the next batch with room, or release the Teacher with a reason. */
    @Transactional
    public EnrolmentDto followUp(UUID actor, UUID enrolmentId, FollowUpRequest request) {
        InductionEnrolment enrolment = find(enrolmentId);
        if (!"NOT_COMPLETED".equals(enrolment.getResult())) {
            throw new ConflictException("A follow-up applies only to a recruit signed off as not completed.");
        }
        if (enrolment.getFollowUp() != null) {
            throw new ConflictException("This recruit already has a follow-up (" + enrolment.getFollowUp() + ").");
        }
        String action = request.action() == null ? "" : request.action().trim().toUpperCase();
        switch (action) {
            case "NEXT_BATCH" -> {
                InductionBatch next = batchService.nextWithRoom(enrolment.getBatchId())
                        .orElseThrow(() -> new ConflictException("There is no batch with room; create one first."));
                batches.findForUpdate(next.getId());
                batchService.enrolLocked(actor, next, enrolment.getTeacherId());
                enrolment.followUp("NEXT_BATCH");
            }
            case "RELEASE" -> {
                String reason = request.reason() == null ? "" : request.reason().trim();
                if (reason.isEmpty()) {
                    throw new InvalidInputException("A reason is required to release a recruit.");
                }
                registry.exit(actor, enrolment.getTeacherId(), LocalDate.now(clock), reason);
                enrolment.followUp("RELEASED");
            }
            default -> throw new InvalidInputException("The action must be NEXT_BATCH or RELEASE.");
        }
        enrolments.saveAndFlush(enrolment);
        changes.record(actor, "INDUCTION_SIGNOFF", enrolmentId, "followUp", null, enrolment.getFollowUp());
        return view(enrolment);
    }

    private InductionEnrolment find(UUID id) {
        return enrolments.findById(id).orElseThrow(() -> new NotFoundException("Enrolment not found."));
    }

    private static EnrolmentDto view(InductionEnrolment e) {
        return new EnrolmentDto(e.getId(), e.getBatchId(), e.getTeacherId(), e.getResult(), e.getRemarks(), e.getFollowUp());
    }
}
