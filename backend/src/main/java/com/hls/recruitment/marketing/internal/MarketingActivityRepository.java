package com.hls.recruitment.marketing.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MarketingActivityRepository extends JpaRepository<MarketingActivity, UUID> {

    List<MarketingActivity> findByProspectIdOrderByActivityDateDesc(UUID prospectId);

    List<MarketingActivity> findBySchoolIdOrderByActivityDateDesc(UUID schoolId);

    List<MarketingActivity> findByActivityDateBetweenOrderByActivityDate(java.time.LocalDate from, java.time.LocalDate to);

    List<MarketingActivity> findByProspectIdIn(java.util.Collection<UUID> prospectIds);
}
