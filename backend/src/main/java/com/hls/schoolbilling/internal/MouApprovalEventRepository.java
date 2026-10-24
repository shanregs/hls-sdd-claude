package com.hls.schoolbilling.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MouApprovalEventRepository extends JpaRepository<MouApprovalEvent, UUID> {

    List<MouApprovalEvent> findByContractIdOrderBySeqAsc(UUID contractId);
}
