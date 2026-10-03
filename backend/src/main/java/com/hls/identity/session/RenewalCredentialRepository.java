package com.hls.identity.session;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RenewalCredentialRepository extends JpaRepository<RenewalCredential, UUID> {

    Optional<RenewalCredential> findByCredentialHash(String credentialHash);
}
