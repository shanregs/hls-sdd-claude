package com.hls.recruitment.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    List<Candidate> findByDriveIdIn(Collection<UUID> driveIds);

    Optional<Candidate> findByDriveIdAndPhoneKey(UUID driveId, String phoneKey);

    long countByDriveId(UUID driveId);

    @Query("""
            select c from Candidate c
            where (:driveId is null or c.driveId = :driveId)
              and (:outcome is null or c.outcome = :outcome)
              and (:term = '' or lower(c.name) like lower(concat('%', :term, '%')) or c.phoneKey like concat('%', :term, '%'))
            order by c.name
            """)
    List<Candidate> search(@Param("driveId") UUID driveId, @Param("outcome") Outcome outcome, @Param("term") String term);
}
