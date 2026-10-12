package com.hls.attendance.manager.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Marks that the missed check-in alert for a Manager and a date has been raised, so it is raised only once. */
@Entity
@Table(name = "manager_missed_alert")
public class MissedAlert {

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "manager_id", nullable = false)
        private UUID managerId;

        @Column(name = "alert_date", nullable = false)
        private LocalDate alertDate;

        protected Key() {
            // JPA
        }

        public Key(UUID managerId, LocalDate alertDate) {
            this.managerId = managerId;
            this.alertDate = alertDate;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && managerId.equals(key.managerId) && alertDate.equals(key.alertDate);
        }

        @Override
        public int hashCode() {
            return 31 * managerId.hashCode() + alertDate.hashCode();
        }
    }

    @EmbeddedId
    private Key id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MissedAlert() {
        // JPA
    }

    public MissedAlert(UUID managerId, LocalDate alertDate, Instant at) {
        this.id = new Key(managerId, alertDate);
        this.createdAt = at;
    }
}
