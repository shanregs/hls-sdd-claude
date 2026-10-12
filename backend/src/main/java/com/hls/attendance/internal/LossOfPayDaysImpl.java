package com.hls.attendance.internal;

import com.hls.attendance.api.LossOfPayDay;
import com.hls.attendance.api.LossOfPayDays;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class LossOfPayDaysImpl implements LossOfPayDays {

    private final AttendanceMarkRepository marks;

    LossOfPayDaysImpl(AttendanceMarkRepository marks) {
        this.marks = marks;
    }

    @Override
    public List<LossOfPayDay> lossOfPayDaysOf(UUID teacherId, YearMonth month) {
        return lossOfPayDaysOf(List.of(teacherId), month.atDay(1), month.atEndOfMonth())
                .getOrDefault(teacherId, List.of());
    }

    @Override
    public Map<UUID, List<LossOfPayDay>> lossOfPayDaysOf(Collection<UUID> teacherIds, LocalDate from, LocalDate to) {
        if (teacherIds.isEmpty() || from.isAfter(to)) {
            return Map.of();
        }
        Map<UUID, List<LossOfPayDay>> result = new LinkedHashMap<>();
        marks.findLossOfPayDays(teacherIds, from, to).stream()
                .sorted(Comparator.comparing(AttendanceMark::getMarkDate))
                .forEach(m -> result.computeIfAbsent(m.getTeacherId(), k -> new ArrayList<>())
                        .add(new LossOfPayDay(m.getMarkDate(), m.getDayValue(), m.getSchoolId())));
        return result;
    }
}
