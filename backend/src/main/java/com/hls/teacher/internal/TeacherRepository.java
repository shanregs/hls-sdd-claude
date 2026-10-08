package com.hls.teacher.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {

    Optional<Teacher> findByUserId(UUID userId);

    Optional<Teacher> findFirstByPhone(String phone);

    /** Teachers whose phone ends with the same ten digits or whose email matches ignoring case; either may be null. */
    @Query(
            value = """
            select * from teacher t
            where (cast(:phoneKey as text) is not null
                   and right(regexp_replace(coalesce(t.phone, ''), '[^0-9]', '', 'g'), 10) = cast(:phoneKey as text))
               or (cast(:email as text) is not null and lower(t.email) = cast(:email as text))
            order by t.created_at
            """,
            nativeQuery = true)
    List<Teacher> findMatching(@Param("phoneKey") String phoneKey, @Param("email") String email);

    @Query("select t.id from Teacher t where t.status = :status")
    List<UUID> findIdsByStatus(@Param("status") TeacherStatus status);

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
              and (:onlyMissing = false or t.designationId is null)
            """)
    Page<Teacher> search(
            @Param("term") String term,
            @Param("filterStatus") boolean filterStatus,
            @Param("status") TeacherStatus status,
            @Param("filterIds") boolean filterIds,
            @Param("ids") Collection<UUID> ids,
            @Param("onlyMissing") boolean onlyMissing,
            Pageable pageable);

    @Query("select t.designationId, count(t) from Teacher t where t.designationId is not null group by t.designationId")
    List<Object[]> countByDesignation();

    long countByDesignationIdIsNull();
}
