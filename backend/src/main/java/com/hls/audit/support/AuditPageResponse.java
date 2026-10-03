package com.hls.audit.support;

import java.util.List;
import org.springframework.data.domain.Page;

/** Shared paginated response envelope for every audit list endpoint (contracts/audit-api.md). */
public record AuditPageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <T> AuditPageResponse<T> from(Page<T> page) {
        return new AuditPageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
