package com.hls.payroll.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Save and query only: there is no update or delete, and the table refuses both (spec 013a FR-006). */
public interface PayPolicyRepository extends JpaRepository<PayPolicy, UUID> {

    /** The version in effect on {@code date}: latest effective date, ties to the version recorded last. */
    Optional<PayPolicy> findFirstByEffectiveOnLessThanEqualOrderByEffectiveOnDescRecordedAtDesc(LocalDate date);

    List<PayPolicy> findAllByOrderByEffectiveOnDescRecordedAtDesc();
}
