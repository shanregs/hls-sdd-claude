package com.hls.recruitment.marketing.internal;

import com.hls.recruitment.api.WonSchools;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads {@code Prospect.wonAt} as a business date (spec 015). */
@Service
class WonSchoolsImpl implements WonSchools {

    private final ProspectRepository prospects;
    private final ZoneId zone;

    WonSchoolsImpl(ProspectRepository prospects, @Value("${hls.business-timezone:Asia/Kolkata}") String zoneId) {
        this.prospects = prospects;
        this.zone = ZoneId.of(zoneId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WonSchool> wonBetween(LocalDate from, LocalDate to, UUID zoneIdOrNull) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        return prospects
                .findByWonAtGreaterThanEqualAndWonAtLessThan(
                        from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant())
                .stream()
                .filter(p -> zoneIdOrNull == null || zoneIdOrNull.equals(p.getZoneId()))
                .map(p -> new WonSchool(p.getId(), p.getSchoolId(), p.getWonAt().atZone(zone).toLocalDate(), p.getZoneId()))
                .toList();
    }
}
