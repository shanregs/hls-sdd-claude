package com.hls.attendance.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Weekly off days: the organization default (schoolId null) or one School override. */
@Entity
@Table(name = "attendance_calendar_setting")
public class CalendarSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "school_id")
    private UUID schoolId;

    @Column(name = "weekly_off_days", nullable = false)
    private String weeklyOffDays;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CalendarSetting() {
        // JPA
    }

    public CalendarSetting(UUID schoolId, Set<DayOfWeek> days) {
        this.schoolId = schoolId;
        setWeeklyOff(days);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public Long getVersion() {
        return version;
    }

    public Set<DayOfWeek> weeklyOff() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        Arrays.stream(weeklyOffDays.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(s -> days.add(fromCode(s)));
        return days;
    }

    public void setWeeklyOff(Set<DayOfWeek> days) {
        this.weeklyOffDays = days.stream().sorted().map(CalendarSetting::code).collect(Collectors.joining(","));
    }

    public static String code(DayOfWeek day) {
        return day.name().substring(0, 3);
    }

    public static DayOfWeek fromCode(String code) {
        for (DayOfWeek d : DayOfWeek.values()) {
            if (code(d).equalsIgnoreCase(code)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Unknown weekday: " + code);
    }
}
