package com.hls;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Propagates a request-correlation id via MDC so every log line for a request can be
 * tied together (constitution Principle VIII: structured logging with request correlation,
 * built in from the first module).
 */
public class CorrelationIdFilter extends HttpFilter {

    static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    // Public: other modules (e.g. identity's AuthAuditLogger/ManagerScopeGuard/
    // TeacherScopeGuard) read this same key rather than duplicating the string —
    // found via /speckit-implement's T051 that three call sites had drifted to
    // a different literal ("requestId") and were silently always missing it.
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** The current request's correlation id, or {@code "unknown"} outside a request (e.g. a unit test). */
    public static String currentCorrelationId() {
        String value = MDC.get(MDC_KEY);
        return value != null ? value : "unknown";
    }
}
