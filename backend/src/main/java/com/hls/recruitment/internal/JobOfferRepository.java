package com.hls.recruitment.internal;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JobOfferRepository extends JpaRepository<JobOffer, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from JobOffer o where o.id = :id")
    Optional<JobOffer> findForUpdate(@Param("id") UUID id);

    boolean existsByCandidateIdAndStatusIn(UUID candidateId, Collection<OfferStatus> statuses);

    Optional<JobOffer> findFirstByPhoneKeyAndStatusIn(String phoneKey, Collection<OfferStatus> statuses);

    Optional<JobOffer> findFirstByTeacherIdAndStatus(UUID teacherId, OfferStatus status);

    List<JobOffer> findByCandidateIdIn(Collection<UUID> candidateIds);

    @Query("""
            select o from JobOffer o
            where (:status is null or o.status = :status)
              and (:candidateId is null or o.candidateId = :candidateId)
            order by o.createdAt desc
            """)
    List<JobOffer> search(@Param("status") OfferStatus status, @Param("candidateId") UUID candidateId);

    List<JobOffer> findByStatusAndResponseDeadlineBefore(OfferStatus status, LocalDate date);
}
