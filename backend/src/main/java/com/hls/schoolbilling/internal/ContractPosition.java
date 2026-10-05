package com.hls.schoolbilling.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One Teacher position of a contract with the monthly salary the School pays for it. Insert-only. */
@Entity
@Table(name = "contract_position")
public class ContractPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private UUID contractId;

    @Column(name = "number", nullable = false, updatable = false)
    private int number;

    @Column(name = "title", updatable = false)
    private String title;

    @Column(name = "salary", nullable = false, updatable = false)
    private BigDecimal salary;

    protected ContractPosition() {
        // JPA
    }

    public ContractPosition(UUID contractId, int number, String title, BigDecimal salary) {
        this.contractId = contractId;
        this.number = number;
        this.title = title;
        this.salary = salary;
    }

    public UUID getId() {
        return id;
    }

    public UUID getContractId() {
        return contractId;
    }

    public int getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public BigDecimal getSalary() {
        return salary;
    }
}
