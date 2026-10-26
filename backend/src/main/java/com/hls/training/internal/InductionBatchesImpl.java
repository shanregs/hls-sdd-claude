package com.hls.training.internal;

import com.hls.training.api.InductionBatchView;
import com.hls.training.api.InductionBatches;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InductionBatchesImpl implements InductionBatches {

    private final InductionBatchRepository batches;
    private final InductionEnrolmentRepository enrolments;

    InductionBatchesImpl(InductionBatchRepository batches, InductionEnrolmentRepository enrolments) {
        this.batches = batches;
        this.enrolments = enrolments;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InductionBatchView> batch(UUID id) {
        return batches.findById(id).map(InductionBatchesImpl::view);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InductionBatchView> openFrom(LocalDate today) {
        return batches.openFrom(today).stream().map(InductionBatchesImpl::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InductionBatchView> endingBetween(LocalDate from, LocalDate to) {
        return batches.endingBetween(from, to).stream().map(InductionBatchesImpl::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public int completedTeachers(UUID batchId) {
        return (int) enrolments.countCompleted(batchId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InductionRecord> recordsOf(UUID teacherId) {
        List<InductionEnrolment> mine = enrolments.findByTeacherId(teacherId);
        java.util.Map<UUID, InductionBatch> byId = new java.util.HashMap<>();
        batches.findAllById(mine.stream().map(InductionEnrolment::getBatchId).toList()).forEach(b -> byId.put(b.getId(), b));
        return mine.stream()
                .filter(e -> byId.containsKey(e.getBatchId()))
                .map(e -> new InductionRecord(
                        e.getBatchId(), byId.get(e.getBatchId()).getName(), e.getStartsOn(), e.getEndsOn(), e.getResult(), e.getSignedAt()))
                .sorted(java.util.Comparator.comparing(InductionRecord::startsOn).reversed())
                .toList();
    }

    private static InductionBatchView view(InductionBatch b) {
        return new InductionBatchView(b.getId(), b.getName(), b.getStartsOn(), b.getEndsOn());
    }
}
