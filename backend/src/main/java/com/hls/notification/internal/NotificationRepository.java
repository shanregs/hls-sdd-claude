package com.hls.notification.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every read and write is scoped by the recipient: nobody reaches another user's notifications. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    @Query("select n from Notification n where n.recipientUserId = :user order by n.updatedAt desc, n.id")
    Page<Notification> findAllOf(@Param("user") UUID user, Pageable pageable);

    @Query("select n from Notification n where n.recipientUserId = :user and n.readAt is null"
            + " order by n.updatedAt desc, n.id")
    Page<Notification> findUnreadOf(@Param("user") UUID user, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    /** Unread notifications of the same group whose last activity is not older than {@code since}. */
    @Query("select n from Notification n where n.recipientUserId = :user and n.groupKey = :key"
            + " and n.readAt is null and n.updatedAt >= :since order by n.updatedAt desc")
    List<Notification> findMergeable(
            @Param("user") UUID user, @Param("key") String key, @Param("since") Instant since);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientUserId = :user and n.readAt is null")
    int markAllRead(@Param("user") UUID user, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.recipientUserId = :user and n.readAt is not null")
    int deleteRead(@Param("user") UUID user);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
