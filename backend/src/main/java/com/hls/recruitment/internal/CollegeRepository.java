package com.hls.recruitment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CollegeRepository extends JpaRepository<College, UUID> {

    @Query("select c from College c where lower(c.name) = lower(:name) and lower(c.city) = lower(:city)")
    Optional<College> findByNameAndCity(@Param("name") String name, @Param("city") String city);

    @Query("""
            select c from College c
            where :term = '' or lower(c.name) like lower(concat('%', :term, '%'))
               or lower(c.city) like lower(concat('%', :term, '%'))
            order by lower(c.name)
            """)
    List<College> search(@Param("term") String term);
}
