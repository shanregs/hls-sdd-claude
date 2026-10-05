package com.hls.recruitment.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CollegeContactRepository extends JpaRepository<CollegeContact, CollegeContact.Key> {

    List<CollegeContact> findByCollegeIdIn(Collection<UUID> collegeIds);
}
