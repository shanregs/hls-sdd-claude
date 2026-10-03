package com.hls.identity.otp;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OneTimeCodeRepository extends JpaRepository<OneTimeCode, UUID> {

    List<OneTimeCode> findByDestinationAndPurposeOrderByExpiresAtDesc(String destination, OtpPurpose purpose);

    /** Finds sibling rows created by the same {@code channel: "BOTH"} request (research.md §14). */
    List<OneTimeCode> findByCodeHashAndPurpose(String codeHash, OtpPurpose purpose);
}
