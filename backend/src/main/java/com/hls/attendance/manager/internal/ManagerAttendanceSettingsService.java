package com.hls.attendance.manager.internal;

import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The cut-off and late time (spec 032 data-model.md): read, and change with a version check and an audit entry. */
@Service
public class ManagerAttendanceSettingsService {

    public static final String AUDIT_TYPE = "MANAGER_ATTENDANCE_SETTING";
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    public record SettingsView(String missedCutoff, String lateAfter, long version) {}

    public record UpdateInput(String missedCutoff, String lateAfter, Long version) {}

    private final ManagerAttendanceSettingsRepository settings;
    private final ManagerAttendanceAudit audit;
    private final Clock clock;

    public ManagerAttendanceSettingsService(
            ManagerAttendanceSettingsRepository settings, ManagerAttendanceAudit audit, Clock clock) {
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SettingsView get() {
        return view(row());
    }

    @Transactional
    public SettingsView update(UUID actor, UpdateInput in) {
        LocalTime cutoff = parse(in.missedCutoff(), "The cut-off time must look like 10:30.");
        LocalTime late = parse(in.lateAfter(), "The late time must look like 09:30.");
        if (!late.isBefore(cutoff)) {
            throw new InvalidInputException("The late time must be before the cut-off time.");
        }
        if (in.version() == null) {
            throw new InvalidInputException("The version is required.");
        }
        ManagerAttendanceSettings row = row();
        if (!in.version().equals(row.getVersion())) {
            throw new ConflictException("The settings were changed by someone else. Reload and try again.");
        }
        LocalTime beforeCutoff = row.getMissedCutoff();
        LocalTime beforeLate = row.getLateAfter();
        row.change(cutoff, late, actor, clock.instant());
        try {
            settings.saveAndFlush(row);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException("The settings were changed by someone else. Reload and try again.");
        }
        audit.changed(actor, AUDIT_TYPE, ManagerAttendanceSettings.ROW, "missedCutoff", beforeCutoff, cutoff);
        audit.changed(actor, AUDIT_TYPE, ManagerAttendanceSettings.ROW, "lateAfter", beforeLate, late);
        return view(row);
    }

    private ManagerAttendanceSettings row() {
        return settings.findById(ManagerAttendanceSettings.ROW)
                .orElseThrow(() -> new IllegalStateException("The Manager attendance settings row is missing."));
    }

    private static SettingsView view(ManagerAttendanceSettings s) {
        return new SettingsView(s.getMissedCutoff().format(HM), s.getLateAfter().format(HM), s.getVersion());
    }

    private static LocalTime parse(String value, String message) {
        try {
            return LocalTime.parse(value == null ? "" : value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(message);
        }
    }
}
