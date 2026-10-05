package com.hls.recruitment.marketing.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ActivityAttachmentRepository extends JpaRepository<ActivityAttachment, UUID> {

    List<ActivityAttachment> findByActivityId(UUID activityId);

    Optional<ActivityAttachment> findByFileId(UUID fileId);
}
