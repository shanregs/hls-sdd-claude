package com.hls.recruitment.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DriveDateChangeRepository extends JpaRepository<DriveDateChange, UUID> {

    List<DriveDateChange> findByDriveIdInOrderByChangedAtAsc(Collection<UUID> driveIds);
}
