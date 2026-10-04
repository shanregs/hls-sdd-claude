package com.hls.identity.session;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    List<Session> findByUserIdAndStatus(UUID userId, SessionStatus status);

    List<Session> findByStatus(SessionStatus status);

    Page<Session> findByStatus(SessionStatus status, Pageable pageable);

    Page<Session> findByUserIdAndStatus(UUID userId, SessionStatus status, Pageable pageable);
}
