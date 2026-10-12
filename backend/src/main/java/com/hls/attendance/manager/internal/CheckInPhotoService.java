package com.hls.attendance.manager.internal;

import com.hls.attendance.internal.BusinessCalendar;
import com.hls.attendance.manager.internal.ManagerContext.Acting;
import com.hls.files.api.FileStore;
import com.hls.files.api.FileStore.FileContent;
import com.hls.files.api.FileStore.FileRef;
import com.hls.school.api.ConflictException;
import com.hls.school.api.InvalidInputException;
import com.hls.school.api.NotFoundException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The optional photo of a Manager's own check-in (spec 032 FR-005): one per day, jpg or png, stored through the shared
 * file store. The check-in never waits on it. It is opened by the Manager, an Admin and a Director only.
 */
@Service
public class CheckInPhotoService {

    private static final String OWNER_TYPE = "MANAGER_DAY";
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");

    private final ManagerContext context;
    private final ManagerMonthGuard guard;
    private final ManagerDayRepository days;
    private final FileStore files;
    private final ManagerAttendanceAudit audit;
    private final BusinessCalendar calendar;

    public CheckInPhotoService(
            ManagerContext context,
            ManagerMonthGuard guard,
            ManagerDayRepository days,
            FileStore files,
            ManagerAttendanceAudit audit,
            BusinessCalendar calendar) {
        this.context = context;
        this.guard = guard;
        this.days = days;
        this.files = files;
        this.audit = audit;
        this.calendar = calendar;
    }

    @Transactional
    public ManagerDayView attach(UUID userId, LocalDate date, String originalName, byte[] bytes) {
        Acting manager = context.resolve(userId);
        String name = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) {
            throw new InvalidInputException("A photo must be a jpg or png file.");
        }
        ManagerDay day = days.findByManagerIdAndDayDate(manager.managerId(), date)
                .filter(ManagerDay::hasCheckIn)
                .orElseThrow(() -> new NotFoundException("There is no check-in on " + date + " to attach a photo to."));
        guard.requireOpen(manager.managerId(), YearMonth.from(date));
        if (day.getPhotoFileId() != null) {
            throw new ConflictException("This check-in already has a photo.");
        }
        FileRef ref = files.store(userId, OWNER_TYPE, day.getId(), originalName, bytes);
        if (!IMAGE_TYPES.contains(ref.contentType())) {
            files.remove(userId, ref.id(), "Not a jpg or png photo");
            throw new InvalidInputException("A photo must be a jpg or png file.");
        }
        day.setPhotoFileId(ref.id());
        ManagerDay saved = days.saveAndFlush(day);
        audit.changed(userId, ManagerAttendanceAudit.DAY, saved.getId(), "photo", null, ref.id());
        return ManagerDayView.of(saved, calendar.today());
    }

    /** The photo of a Manager's day; the caller has already checked it is the Manager themself or an Admin or Director. */
    @Transactional(readOnly = true)
    public FileContent open(UUID managerId, LocalDate date) {
        UUID fileId = days.findByManagerIdAndDayDate(managerId, date)
                .map(ManagerDay::getPhotoFileId)
                .orElseThrow(() -> new NotFoundException("There is no photo for this day."));
        return files.open(fileId).orElseThrow(() -> new NotFoundException("There is no photo for this day."));
    }

    /** The Manager id of the signed-in user, for the own-photo path. */
    public UUID ownManagerId(UUID userId) {
        return context.resolve(userId).managerId();
    }
}
