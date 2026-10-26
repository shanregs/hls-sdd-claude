package com.hls.content.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ContentRepository extends JpaRepository<Content, UUID>, JpaSpecificationExecutor<Content> {}
