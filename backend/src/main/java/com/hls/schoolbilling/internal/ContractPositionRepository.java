package com.hls.schoolbilling.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractPositionRepository extends JpaRepository<ContractPosition, UUID> {

    List<ContractPosition> findByContractIdOrderByNumber(UUID contractId);

    List<ContractPosition> findByContractIdInOrderByContractIdAscNumberAsc(Collection<UUID> contractIds);
}
