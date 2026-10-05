package com.hls.attendance.internal;

import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.SchoolDirectory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every value one date has had, newest first (spec 008 FR-003). */
@Service
public class MarkHistoryService {

    public record HistoryView(
            MarkAction action,
            String code,
            String codeName,
            BigDecimal dayValue,
            String schoolName,
            String note,
            String setByName,
            SetByKind setByKind,
            Instant setAt) {}

    private final MarkHistoryRepository history;
    private final StatusCodeCatalog codes;
    private final SchoolDirectory schools;
    private final AppUserRepository users;

    public MarkHistoryService(
            MarkHistoryRepository history, StatusCodeCatalog codes, SchoolDirectory schools, AppUserRepository users) {
        this.history = history;
        this.codes = codes;
        this.schools = schools;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<HistoryView> of(UUID teacherId, LocalDate date) {
        List<MarkHistoryEntry> entries = history.findByTeacherIdAndMarkDateOrderBySetAtDesc(teacherId, date);
        Map<UUID, StatusCode> codeById =
                codes.byId();
        Map<UUID, String> schoolNames = schools
                .schools(entries.stream().map(MarkHistoryEntry::getSchoolId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream()
                .collect(Collectors.toMap(SchoolDirectory.SchoolInfo::id, SchoolDirectory.SchoolInfo::name));
        Map<UUID, String> userNames = users
                .findAllById(entries.stream().map(MarkHistoryEntry::getSetByUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        return entries.stream()
                .map(e -> {
                    StatusCode code = e.getStatusCodeId() == null ? null : codeById.get(e.getStatusCodeId());
                    return new HistoryView(
                            e.getAction(),
                            code == null ? null : code.getShortCode(),
                            code == null ? null : code.getName(),
                            e.getDayValue(),
                            e.getSchoolId() == null ? null : schoolNames.getOrDefault(e.getSchoolId(), "-"),
                            e.getNote(),
                            userNames.getOrDefault(e.getSetByUserId(), "Unknown user"),
                            e.getSetByKind(),
                            e.getSetAt());
                })
                .toList();
    }
}
