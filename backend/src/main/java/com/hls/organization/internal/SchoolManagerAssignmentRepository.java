package com.hls.organization.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolManagerAssignmentRepository extends JpaRepository<SchoolManagerAssignment, UUID> {

    Optional<SchoolManagerAssignment> findBySchoolIdAndEndsOnIsNull(UUID schoolId);

    List<SchoolManagerAssignment> findBySchoolIdInAndEndsOnIsNull(Collection<UUID> schoolIds);

    List<SchoolManagerAssignment> findByManagerIdAndEndsOnIsNull(UUID managerId);

    List<SchoolManagerAssignment> findByManagerIdInAndEndsOnIsNull(Collection<UUID> managerIds);

    List<SchoolManagerAssignment> findBySchoolIdOrderByStartsOnDesc(UUID schoolId);

    List<SchoolManagerAssignment> findByManagerIdOrderByStartsOnDesc(UUID managerId);
}
