package com.hls.identity.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByPhone(String phone);

    Optional<AppUser> findByUsernameLower(String usernameLower);

    Optional<AppUser> findByEmail(String email);

    /**
     * Free-text search over display name or phone, with optional role/status filters (FR-002).
     * Pass {@code term = ""} for no text filter; {@code filterRole = false} / {@code filterActive =
     * false} to ignore {@code role} / {@code active} (the placeholder value is then unused).
     */
    @Query("""
            select u from AppUser u
            where (:term = ''
                   or lower(u.displayName) like lower(concat('%', :term, '%'))
                   or u.phone like concat('%', :term, '%'))
              and (:filterActive = false or u.active = :active)
              and (:filterRole = false
                   or exists (select 1 from RoleAssignment ra where ra.userId = u.id and ra.role = :role))
            """)
    Page<AppUser> search(
            @Param("term") String term,
            @Param("filterActive") boolean filterActive,
            @Param("active") boolean active,
            @Param("filterRole") boolean filterRole,
            @Param("role") Role role,
            Pageable pageable);
}
