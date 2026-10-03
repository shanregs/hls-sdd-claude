package com.hls.school.api;

import java.util.List;
import org.springframework.data.domain.Page;

/** The {@code {content,page,size,totalElements}} list envelope shared by the master-data modules. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
