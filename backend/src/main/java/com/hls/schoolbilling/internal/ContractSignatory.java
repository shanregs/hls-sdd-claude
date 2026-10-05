package com.hls.schoolbilling.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A person who signed a contract for one side. Part of the signed contract: insert-only. */
@Entity
@Table(name = "contract_signatory")
public class ContractSignatory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party", nullable = false, updatable = false)
    private SignatoryParty party;

    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Column(name = "designation", nullable = false, updatable = false)
    private String designation;

    @Column(name = "user_id", updatable = false)
    private UUID userId;

    protected ContractSignatory() {
        // JPA
    }

    public ContractSignatory(
            UUID contractId, SignatoryParty party, String name, String designation, UUID userId) {
        this.contractId = contractId;
        this.party = party;
        this.name = name;
        this.designation = designation;
        this.userId = userId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContractId() {
        return contractId;
    }

    public SignatoryParty getParty() {
        return party;
    }

    public String getName() {
        return name;
    }

    public String getDesignation() {
        return designation;
    }

    public UUID getUserId() {
        return userId;
    }
}
