package com.hls.recruitment.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CampusDriveRepository extends JpaRepository<CampusDrive, UUID> {

    List<CampusDrive> findByCollegeId(UUID collegeId);
}
