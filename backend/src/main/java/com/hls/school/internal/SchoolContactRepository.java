package com.hls.school.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SchoolContactRepository extends JpaRepository<SchoolContact, SchoolContact.Key> {

    List<SchoolContact> findBySchoolId(UUID schoolId);
}
