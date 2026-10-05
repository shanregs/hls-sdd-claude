package com.hls.identity.otp;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OneTimeCodeRepository extends JpaRepository<OneTimeCode, UUID> {

    List<OneTimeCode> findByDestinationAndPurposeOrderByExpiresAtDesc(String destination, OtpPurpose purpose);

    /**
     * Finds the sibling rows of one {@code channel: "BOTH"} request (research.md §14): the same code sent to the same
     * person's other destination. Always pass only that person's destinations: the code is a short number, so
     * another user's unrelated code can share the hash.
     */
    List<OneTimeCode> findByDestinationInAndPurposeAndCodeHash(
            Collection<String> destinations, OtpPurpose purpose, String codeHash);
}
