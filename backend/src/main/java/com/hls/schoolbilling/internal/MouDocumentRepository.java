package com.hls.schoolbilling.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MouDocumentRepository extends JpaRepository<MouDocument, UUID> {

    List<MouDocument> findByContractIdOrderByVersionNoDesc(UUID contractId);

    /** The versions that are not removed, for the batch summaries of the list and the School page. */
    List<MouDocument> findByContractIdInAndRemovedAtIsNull(Collection<UUID> contractIds);

    @Query("select coalesce(max(d.versionNo), 0) from MouDocument d where d.contractId = :contractId")
    int maxVersion(@Param("contractId") UUID contractId);

    /** Contract ids (of those given) that have at least one document that is not removed. */
    @Query("select distinct d.contractId from MouDocument d where d.contractId in :ids and d.removedAt is null")
    List<UUID> contractsWithDocument(@Param("ids") Collection<UUID> ids);
}
