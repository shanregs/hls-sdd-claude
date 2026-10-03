package com.hls.teacher.internal;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {

    Optional<Teacher> findByUserId(UUID userId);

    /**
     * Text/status search restricted to an optional id set (scope). {@code filterIds = false} means
     * unrestricted (the {@code ids} placeholder is then unused).
     */
    @Query("""
            select t from Teacher t
            where (:term = ''
                   or lower(t.name) like lower(concat('%', :term, '%'))
                   or t.phone like concat('%', :term, '%')
                   or lower(t.email) like lower(concat('%', :term, '%')))
              and (:filterStatus = false or t.status = :status)
              and (:filterIds = false or t.id in :ids)
            """)
    Page<Teacher> search(
            @Param("term") String term,
            @Param("filterStatus") boolean filterStatus,
            @Param("status") TeacherStatus status,
            @Param("filterIds") boolean filterIds,
            @Param("ids") Collection<UUID> ids,
            Pageable pageable);
}
