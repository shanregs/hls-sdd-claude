package com.hls.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    List<OtpChallenge> findByIdentifierOrderByExpiresAtDesc(String identifier);
}
