package com.hls.training.calendar.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ReminderSentRepository extends JpaRepository<ReminderSent, UUID> {

    List<ReminderSent> findBySessionIdIn(Collection<UUID> sessionIds);

    @Modifying
    @Query("delete from ReminderSent r where r.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") UUID sessionId);
}
