package com.hls.schoolbilling.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractSignatoryRepository extends JpaRepository<ContractSignatory, UUID> {

    List<ContractSignatory> findByContractIdIn(Collection<UUID> contractIds);
}
