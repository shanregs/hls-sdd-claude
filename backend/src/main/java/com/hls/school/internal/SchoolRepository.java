package com.hls.school.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolRepository extends JpaRepository<School, UUID> {

    boolean existsByPlaceId(UUID placeId);

    @Query("select count(s) from School s, Place p where s.placeId = p.id and p.zoneId = :zoneId")
    long countInZone(@Param("zoneId") UUID zoneId);

    @Query("select s from School s, Place p where s.placeId = p.id and p.zoneId = :zoneId")
    List<School> findInZone(@Param("zoneId") UUID zoneId);

    /**
     * Schools filtered by optional text/Zone/Place/active and an optional id restriction (scope).
     * {@code filterIds = false} means unrestricted (the {@code ids} placeholder is then unused).
     */
    @Query("""
            select s from School s, Place p
            where s.placeId = p.id
              and (:term = '' or lower(s.name) like lower(concat('%', :term, '%')))
              and (:filterZone = false or p.zoneId = :zoneId)
              and (:filterPlace = false or s.placeId = :placeId)
              and (:filterActive = false or s.active = :active)
              and (:filterIds = false or s.id in :ids)
            """)
    Page<School> search(
            @Param("term") String term,
            @Param("filterZone") boolean filterZone,
            @Param("zoneId") UUID zoneId,
            @Param("filterPlace") boolean filterPlace,
            @Param("placeId") UUID placeId,
            @Param("filterActive") boolean filterActive,
            @Param("active") boolean active,
            @Param("filterIds") boolean filterIds,
            @Param("ids") Collection<UUID> ids,
            Pageable pageable);
}
