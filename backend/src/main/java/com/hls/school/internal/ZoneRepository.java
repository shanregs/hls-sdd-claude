package com.hls.school.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {

    Page<Zone> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Optional<Zone> findByNameIgnoreCase(String name);
}
