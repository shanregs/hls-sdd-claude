package com.hls.recruitment.marketing.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OverdueNoticeRepository extends JpaRepository<OverdueNotice, OverdueNotice.Key> {

    boolean existsByProspectIdAndLimitDays(UUID prospectId, int limitDays);
}
