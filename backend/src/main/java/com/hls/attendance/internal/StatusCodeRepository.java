package com.hls.attendance.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StatusCodeRepository extends JpaRepository<StatusCode, UUID> {

    Optional<StatusCode> findByShortCodeIgnoreCase(String shortCode);

    List<StatusCode> findByActiveTrueOrderBySortOrder();

    List<StatusCode> findAllByOrderBySortOrder();

    @Query("select coalesce(max(c.sortOrder), 0) from StatusCode c")
    int maxSortOrder();
}
