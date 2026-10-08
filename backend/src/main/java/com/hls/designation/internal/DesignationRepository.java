package com.hls.designation.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DesignationRepository extends JpaRepository<Designation, UUID> {

    List<Designation> findAllByOrderByKindAscNameAsc();

    /** Designations of the same kind with the same normalized name (capitals and extra spaces ignored). */
    @Query(
            value = """
            select * from designation d
            where d.kind = :kind
              and lower(regexp_replace(btrim(d.name), '\\s+', ' ', 'g')) = :key
            """,
            nativeQuery = true)
    List<Designation> findByKindAndKey(@Param("kind") String kind, @Param("key") String key);

    /** Sets the held flag without touching the version, so assigning never makes a rename or retire request stale. */
    @Modifying
    @Query(value = "update designation set held_ever = true where id = :id and held_ever = false", nativeQuery = true)
    int markHeld(@Param("id") UUID id);
}
