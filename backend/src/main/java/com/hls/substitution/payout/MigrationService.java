package com.hls.substitution.payout;

import com.hls.attendance.api.WorkingCalendar;
import com.hls.school.api.ChangeRecorder;
import com.hls.school.api.NotFoundException;
import com.hls.school.api.SchoolDirectory;
import com.hls.substitution.internal.SubstitutionRepository;
import com.hls.substitution.internal.SubstitutionRequirement;
import com.hls.substitution.payout.DayEvent.Cause;
import com.hls.substitution.payout.DayEvent.Field;
import com.hls.teacher.api.TeacherDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The go-live move (spec 017b FR-015, N5 option A): every live credit of spec 017a becomes a day record. A credit for a
 * day that has passed is recorded as Present and flagged for review, because nobody marked it; a credit for a day still to
 * come is an ordinary allocation. A credit held by a Teacher who is not a Substitute is not created as payable: it is
 * listed "settle first" for the Admin. Safe to run again: a credit that already has its day record is counted and left
 * alone.
 */
@Service
public class MigrationService {

    /** A Teacher who holds credits but is not a Substitute, so none were created for them. */
    public record SettleFirst(UUID teacherId, String name, int credits) {}

    public record Result(int migrated, int futureAllocations, int alreadyRecorded, List<SettleFirst> settleFirst) {}

    private final SubstitutionRepository requirements;
    private final SubstituteDayRepository days;
    private final DayEventRepository events;
    private final DayService dayService;
    private final SchoolDirectory schools;
    private final TeacherDirectory teachers;
    private final WorkingCalendar calendar;
    private final ChangeRecorder changes;
    private final Clock clock;

    MigrationService(
            SubstitutionRepository requirements,
            SubstituteDayRepository days,
            DayEventRepository events,
            DayService dayService,
            SchoolDirectory schools,
            TeacherDirectory teachers,
            WorkingCalendar calendar,
            ChangeRecorder changes,
            Clock clock) {
        this.requirements = requirements;
        this.days = days;
        this.events = events;
        this.dayService = dayService;
        this.schools = schools;
        this.teachers = teachers;
        this.calendar = calendar;
        this.changes = changes;
        this.clock = clock;
    }

    @Transactional
    public Result run(UUID actor) {
        Set<UUID> substitutes = teachers.substituteTeacherIds();
        LocalDate today = calendar.businessToday();
        Instant now = clock.instant();
        int migrated = 0;
        int future = 0;
        int already = 0;
        Map<UUID, Integer> settle = new HashMap<>();
        for (SubstitutionRequirement credit : requirements.allCredits()) {
            UUID teacher = credit.getSubstituteTeacherId();
            if (!substitutes.contains(teacher)) {
                settle.merge(teacher, 1, Integer::sum);
                continue;
            }
            if (days.findByRequirementIdAndSubstituteTeacherId(credit.getId(), teacher).isPresent()) {
                already++;
                continue;
            }
            UUID zone = schools.school(credit.getSchoolId())
                    .map(SchoolDirectory.SchoolInfo::zoneId)
                    .orElseThrow(() -> new NotFoundException("School not found."));
            boolean passed = !credit.getDutyDate().isAfter(today);
            SubstituteDay day = new SubstituteDay(
                    credit.getId(),
                    teacher,
                    credit.getSchoolId(),
                    zone,
                    credit.getTeacherId(),
                    credit.getDutyDate(),
                    credit.getDayValue(),
                    passed ? "MIGRATED" : "ALLOCATION",
                    now);
            if (passed) {
                day.mark(DayAttendance.PRESENT, actor, now);
                day.setNeedsReview(true);
            }
            day = days.saveAndFlush(day);
            events.save(new DayEvent(
                    day.getId(), now, Field.MIGRATED, null, passed ? "PRESENT" : "ALLOCATED", actor, Cause.MIGRATION,
                    passed ? "credit of spec 017a, marked Present for review" : "credit for a day still to come"));
            dayService.refresh(day, now, Cause.MIGRATION);
            days.save(day);
            if (passed) {
                migrated++;
            } else {
                future++;
            }
        }
        List<SettleFirst> settleFirst = new ArrayList<>();
        Map<UUID, TeacherDirectory.TeacherInfo> info = teachers.teacherInfo(settle.keySet());
        settle.forEach((id, count) ->
                settleFirst.add(new SettleFirst(id, info.containsKey(id) ? info.get(id).name() : "?", count)));
        settleFirst.sort(java.util.Comparator.comparing(SettleFirst::name, String.CASE_INSENSITIVE_ORDER));
        changes.recordLifecycle(
                actor,
                DayService.ENTITY,
                actor,
                "migration",
                migrated + " migrated, " + future + " future, " + already + " already recorded, " + settleFirst.size()
                        + " to settle first");
        return new Result(migrated, future, already, settleFirst);
    }
}
