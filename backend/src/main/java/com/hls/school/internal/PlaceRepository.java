package com.hls.school.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

    long countByZoneId(UUID zoneId);

    boolean existsByZoneId(UUID zoneId);

    List<Place> findByZoneId(UUID zoneId);

    List<Place> findByPinCode(String pinCode);

    List<Place> findByNameContainingIgnoreCase(String name);

    @Query("""
            select p from Place p
            where p.zoneId = :zoneId
              and (:term = ''
                   or lower(p.name) like lower(concat('%', :term, '%'))
                   or p.pinCode like concat('%', :term, '%'))
            """)
    Page<Place> searchInZone(@Param("zoneId") UUID zoneId, @Param("term") String term, Pageable pageable);
}
