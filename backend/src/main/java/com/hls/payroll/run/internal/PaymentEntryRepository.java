package com.hls.payroll.run.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentEntryRepository extends JpaRepository<PaymentEntry, UUID> {

    List<PaymentEntry> findByLineIdOrderByEnteredAtDesc(UUID lineId);

    List<PaymentEntry> findByLineIdInOrderByEnteredAtDesc(Collection<UUID> lineIds);
}
