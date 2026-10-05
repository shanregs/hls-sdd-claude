package com.hls.training.internal;

import com.hls.recruitment.api.InductionProgress;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InductionProgressImpl implements InductionProgress {

    private final InductionEnrolmentRepository enrolments;

    InductionProgressImpl(InductionEnrolmentRepository enrolments) {
        this.enrolments = enrolments;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> completedTeacherIds() {
        return Set.copyOf(enrolments.completedTeacherIds());
    }
}
