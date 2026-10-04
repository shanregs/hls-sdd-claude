package com.hls.attendance.internal;

import com.hls.attendance.api.MarkView;
import com.hls.identity.user.AppUser;
import com.hls.identity.user.AppUserRepository;
import com.hls.school.api.SchoolDirectory;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds {@link MarkView}s, resolving status code, School and setter names in bulk. */
@Component
public class MarkViewFactory {

    private final StatusCodeRepository codes;
    private final SchoolDirectory schools;
    private final AppUserRepository users;

    public MarkViewFactory(StatusCodeRepository codes, SchoolDirectory schools, AppUserRepository users) {
        this.codes = codes;
        this.schools = schools;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public MarkView of(AttendanceMark mark) {
        return ofAll(List.of(mark)).get(0);
    }

    @Transactional(readOnly = true)
    public List<MarkView> ofAll(Collection<AttendanceMark> marks) {
        if (marks.isEmpty()) {
            return List.of();
        }
        Map<UUID, StatusCode> codeById =
                codes.findAll().stream().collect(Collectors.toMap(StatusCode::getId, Function.identity()));
        Map<UUID, String> schoolNames = schools
                .schools(marks.stream().map(AttendanceMark::getSchoolId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(SchoolDirectory.SchoolInfo::id, SchoolDirectory.SchoolInfo::name));
        Map<UUID, String> userNames = users
                .findAllById(marks.stream().map(AttendanceMark::getSetByUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName));
        return marks.stream()
                .map(m -> {
                    StatusCode code = codeById.get(m.getStatusCodeId());
                    return new MarkView(
                            m.getMarkDate(),
                            code.getShortCode(),
                            code.getName(),
                            code.getCategory().name(),
                            m.getDayValue(),
                            m.getSchoolId(),
                            schoolNames.getOrDefault(m.getSchoolId(), "-"),
                            m.getSetByKind().name(),
                            m.getSetByUserId(),
                            userNames.getOrDefault(m.getSetByUserId(), "Unknown user"),
                            m.getSetAt(),
                            m.getNote(),
                            m.getVersion(),
                            m.getLeaveRequestId());
                })
                .toList();
    }
}
