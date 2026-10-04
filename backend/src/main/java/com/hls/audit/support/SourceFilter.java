package com.hls.audit.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The optional {@code source=WEB|ANDROID} filter shared by the audit list and export endpoints (spec 018). */
public final class SourceFilter {

    private SourceFilter() {}

    /** Returns the normalized value, null when absent, or throws 400 for anything but WEB or ANDROID. */
    public static String parse(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String normalized = source.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.equals("WEB") && !normalized.equals("ANDROID")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source must be WEB or ANDROID");
        }
        return normalized;
    }
}
