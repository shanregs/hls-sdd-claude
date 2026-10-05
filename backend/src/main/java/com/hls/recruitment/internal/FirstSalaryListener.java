package com.hls.recruitment.internal;

import com.hls.school.api.ChangeRecorder;
import com.hls.schoolbilling.api.TeacherFirstAssigned;
import com.hls.teacher.api.TeacherRegistry;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the salary of the accepted offer when its Teacher is first assigned to a School (decision D13: salary starts
 * at placement, not at induction). Runs inside the assignment transaction, so a refused assignment writes nothing, and
 * does nothing for a Teacher who did not come from an offer or who already has a salary entry.
 */
@Component
class FirstSalaryListener {

    private final JobOfferRepository offers;
    private final TeacherRegistry teachers;
    private final ChangeRecorder changes;

    FirstSalaryListener(JobOfferRepository offers, TeacherRegistry teachers, ChangeRecorder changes) {
        this.offers = offers;
        this.teachers = teachers;
        this.changes = changes;
    }

    @EventListener
    @Transactional
    void on(TeacherFirstAssigned event) {
        Optional<JobOffer> offer = offers.findFirstByTeacherIdAndStatus(event.teacherId(), OfferStatus.ACCEPTED);
        if (offer.isEmpty() || teachers.hasSalaryEntry(event.teacherId())) {
            return;
        }
        JobOffer accepted = offer.get();
        teachers.recordFirstSalary(accepted.getDecidedBy(), event.teacherId(), accepted.getMonthlySalary(), event.startsOn());
        changes.recordLifecycle(
                accepted.getDecidedBy(),
                "TEACHER_FIRST_SALARY",
                event.teacherId(),
                "recorded",
                accepted.getMonthlySalary().toPlainString() + " from " + event.startsOn());
    }
}
