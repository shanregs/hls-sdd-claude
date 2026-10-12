package com.hls.substitution.internal;

import com.hls.attendance.api.TeacherAbsenceRecorded;
import com.hls.attendance.api.TeacherAbsenceWithdrawn;
import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.substitution.api.SubstitutionEnded;
import com.hls.substitution.api.SubstitutionNeeded;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the attendance absence events (amendment A2 to spec 008) into requirements (spec 017a FR-004, FR-005). Runs
 * inside the publisher's transaction, so a refused mark leaves no requirement. Idempotent: the same event twice changes
 * nothing the second time.
 */
@Component
class AbsenceListener {

    static final String WITHDRAWN = "absence withdrawn";

    private final SubstitutionRepository requirements;
    private final SubstitutionHistoryRepository history;
    private final SubstitutionAudit audit;
    private final TeacherDirectory teachers;
    private final SchoolDirectory schools;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AbsenceListener(
            SubstitutionRepository requirements,
            SubstitutionHistoryRepository history,
            SubstitutionAudit audit,
            TeacherDirectory teachers,
            SchoolDirectory schools,
            ApplicationEventPublisher events,
            Clock clock) {
        this.requirements = requirements;
        this.history = history;
        this.audit = audit;
        this.teachers = teachers;
        this.schools = schools;
        this.events = events;
        this.clock = clock;
    }

    @EventListener
    void onRecorded(TeacherAbsenceRecorded event) {
        Instant now = clock.instant();
        SubstitutionRequirement existing =
                requirements.findByTeacherIdAndDutyDate(event.teacherId(), event.date()).orElse(null);
        if (existing == null) {
            SubstitutionRequirement created = requirements.saveAndFlush(new SubstitutionRequirement(
                    event.teacherId(), event.schoolId(), event.date(), event.dayValue(), event.kind(), now));
            history.save(new SubstitutionHistoryEntry(
                    created.getId(), HistoryAction.RAISED, null, RequirementStatus.OPEN, null, event.actorUserId(), event.kind(), now));
            audit.created(
                    event.actorUserId(),
                    created,
                    "teacher " + event.teacherId() + " " + event.date() + " " + event.dayValue().toPlainString() + " day");
            needed(created);
            return;
        }
        if (existing.getStatus() == RequirementStatus.CANCELLED) {
            RequirementStatus before = existing.getStatus();
            existing.reopen(event.schoolId(), event.dayValue(), event.kind(), now);
            requirements.saveAndFlush(existing);
            history.save(new SubstitutionHistoryEntry(
                    existing.getId(), HistoryAction.REOPENED, before, RequirementStatus.OPEN, null, event.actorUserId(), event.kind(), now));
            audit.status(event.actorUserId(), existing.getId(), before, RequirementStatus.OPEN, "absence again");
            needed(existing);
            return;
        }
        boolean changed = existing.getDayValue().compareTo(event.dayValue()) != 0
                || !Objects.equals(existing.getSchoolId(), event.schoolId())
                || !Objects.equals(existing.getKind(), event.kind());
        if (!changed) {
            return;
        }
        var oldValue = existing.getDayValue();
        existing.refresh(event.schoolId(), event.dayValue(), event.kind(), now);
        requirements.saveAndFlush(existing);
        history.save(new SubstitutionHistoryEntry(
                existing.getId(),
                HistoryAction.UPDATED,
                existing.getStatus(),
                existing.getStatus(),
                existing.getSubstituteTeacherId(),
                event.actorUserId(),
                event.kind() + " " + event.dayValue().toPlainString(),
                now));
        audit.dayValue(event.actorUserId(), existing.getId(), oldValue.toPlainString(), event.dayValue().toPlainString());
    }

    @EventListener
    void onWithdrawn(TeacherAbsenceWithdrawn event) {
        SubstitutionRequirement existing =
                requirements.findByTeacherIdAndDutyDate(event.teacherId(), event.date()).orElse(null);
        if (existing == null || existing.getStatus() == RequirementStatus.CANCELLED) {
            return;
        }
        Instant now = clock.instant();
        RequirementStatus before = existing.getStatus();
        UUID substitute = existing.getSubstituteTeacherId();
        existing.cancel(now);
        requirements.saveAndFlush(existing);
        history.save(new SubstitutionHistoryEntry(
                existing.getId(), HistoryAction.CANCELLED, before, RequirementStatus.CANCELLED, substitute, event.actorUserId(), WITHDRAWN, now));
        audit.status(event.actorUserId(), existing.getId(), before, RequirementStatus.CANCELLED, WITHDRAWN);
        if (substitute != null) {
            audit.substitute(event.actorUserId(), existing.getId(), substitute, null);
            events.publishEvent(new SubstitutionEnded(
                    existing.getId(), substitute, existing.getSchoolId(), schoolName(existing.getSchoolId()), existing.getDutyDate(), WITHDRAWN));
        }
    }

    private void needed(SubstitutionRequirement r) {
        String teacherName = teachers.teacherInfo(java.util.List.of(r.getTeacherId())).values().stream()
                .findFirst()
                .map(TeacherInfo::name)
                .orElse("A Teacher");
        events.publishEvent(new SubstitutionNeeded(
                r.getId(), r.getSchoolId(), schoolName(r.getSchoolId()), r.getTeacherId(), teacherName, r.getDutyDate()));
    }

    private String schoolName(UUID schoolId) {
        return schools.school(schoolId).map(SchoolInfo::name).orElse("the School");
    }
}
