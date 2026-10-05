package com.hls.schoolbilling.internal;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

    List<Contract> findBySchoolIdOrderByStartsOnDesc(UUID schoolId);

    /** The live (not cancelled) contract of the School whose dates include {@code date}. */
    @Query("""
            select c from Contract c
            where c.schoolId = :schoolId
              and c.state <> com.hls.schoolbilling.internal.ContractState.CANCELLED
              and c.startsOn <= :date
              and (c.endsOn is null or c.endsOn >= :date)
            """)
    Optional<Contract> liveOn(@Param("schoolId") UUID schoolId, @Param("date") LocalDate date);

    /** Live contracts of the Schools that overlap {@code from..to} inclusive, oldest first. */
    @Query("""
            select c from Contract c
            where c.schoolId in :schoolIds
              and c.state <> com.hls.schoolbilling.internal.ContractState.CANCELLED
              and c.startsOn <= :to
              and (c.endsOn is null or c.endsOn >= :from)
            order by c.startsOn
            """)
    List<Contract> liveOverlapping(
            @Param("schoolIds") Collection<UUID> schoolIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** The live contract of the School starting latest, whatever its dates. */
    @Query("""
            select c from Contract c
            where c.schoolId = :schoolId
              and c.state <> com.hls.schoolbilling.internal.ContractState.CANCELLED
            order by c.startsOn desc
            """)
    List<Contract> liveNewestFirst(@Param("schoolId") UUID schoolId);

    /** Every live (not cancelled) contract, for the list over all Schools. */
    @Query("select c from Contract c where c.state <> com.hls.schoolbilling.internal.ContractState.CANCELLED")
    List<Contract> findAllLive();

    /** The latest-starting live contract of each School. */
    @Query("""
            select c from Contract c
            where c.state <> com.hls.schoolbilling.internal.ContractState.CANCELLED
              and c.startsOn = (
                  select max(c2.startsOn) from Contract c2
                  where c2.schoolId = c.schoolId
                    and c2.state <> com.hls.schoolbilling.internal.ContractState.CANCELLED)
            """)
    List<Contract> latestLivePerSchool();
}
