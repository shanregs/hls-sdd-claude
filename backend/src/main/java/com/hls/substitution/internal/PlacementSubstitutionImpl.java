package com.hls.substitution.internal;

import com.hls.school.api.SchoolDirectory;
import com.hls.school.api.SchoolDirectory.SchoolInfo;
import com.hls.substitution.api.PlacementSubstitution;
import com.hls.substitution.api.SubstitutionEnded;
import com.hls.substitution.api.SubstitutionNeeded;
import com.hls.teacher.api.TeacherDirectory;
import com.hls.teacher.api.TeacherDirectory.TeacherInfo;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Relocates or cancels a moving Teacher's open requirements, with history and audit as the module's other changes. */
@Service
class PlacementSubstitutionImpl implements PlacementSubstitution {

    private final SubstitutionRepository requirements;
    private final SubstitutionHistoryRepository history;
    private final SubstitutionAudit audit;
    private final SchoolDirectory schools;
    private final TeacherDirectory teachers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PlacementSubstitutionImpl(
            SubstitutionRepository requirements,
            SubstitutionHistoryRepository history,
            SubstitutionAudit audit,
            SchoolDirectory schools,
            TeacherDirectory teachers,
            ApplicationEventPublisher events,
            Clock clock) {
        this.requirements = requirements;
        this.history = history;
        this.audit = audit;
        this.schools = schools;
        this.teachers = teachers;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int relocate(UUID actorUserId, UUID teacherId, LocalDate from, UUID newSchoolId, String reason) {
        List<SubstitutionRequirement> rows = requirements.liveFrom(teacherId, from);
        if (rows.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        String schoolName = newSchoolId == null ? null : schools.school(newSchoolId).map(SchoolInfo::name).orElse("");
        TeacherInfo teacher = teachers.teacherInfo(List.of(teacherId)).get(teacherId);
        String teacherName = teacher == null ? "" : teacher.name();
        for (SubstitutionRequirement r : rows) {
            RequirementStatus before = r.getStatus();
            UUID previousSubstitute = r.getSubstituteTeacherId();
            UUID oldSchool = r.getSchoolId();
            String oldSchoolName = schools.school(oldSchool).map(SchoolInfo::name).orElse("");
            if (newSchoolId == null) {
                r.cancel(now);
                requirements.saveAndFlush(r);
                history.save(new SubstitutionHistoryEntry(
                        r.getId(), HistoryAction.CANCELLED, before, RequirementStatus.CANCELLED, previousSubstitute, actorUserId, reason, now));
                audit.status(actorUserId, r.getId(), before, RequirementStatus.CANCELLED, reason);
            } else {
                if (before != RequirementStatus.OPEN) {
                    r.release(reason, now);
                }
                r.refresh(newSchoolId, r.getDayValue(), r.getKind(), now);
                requirements.saveAndFlush(r);
                if (before != RequirementStatus.OPEN) {
                    history.save(new SubstitutionHistoryEntry(
                            r.getId(), HistoryAction.RELEASED, before, RequirementStatus.OPEN, previousSubstitute, actorUserId, reason, now));
                    audit.status(actorUserId, r.getId(), before, RequirementStatus.OPEN, reason);
                    audit.substitute(actorUserId, r.getId(), previousSubstitute, null);
                } else {
                    history.save(new SubstitutionHistoryEntry(
                            r.getId(), HistoryAction.UPDATED, before, RequirementStatus.OPEN, null, actorUserId, reason, now));
                }
                events.publishEvent(new SubstitutionNeeded(
                        r.getId(), newSchoolId, schoolName, teacherId, teacherName, r.getDutyDate()));
            }
            if (previousSubstitute != null) {
                events.publishEvent(new SubstitutionEnded(
                        r.getId(), previousSubstitute, oldSchool, oldSchoolName, r.getDutyDate(), reason));
            }
        }
        return rows.size();
    }
}
