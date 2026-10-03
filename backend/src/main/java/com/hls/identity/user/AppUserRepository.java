package com.hls.identity.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByPhone(String phone);

    Optional<AppUser> findByUsernameLower(String usernameLower);

    Optional<AppUser> findByEmail(String email);
}
