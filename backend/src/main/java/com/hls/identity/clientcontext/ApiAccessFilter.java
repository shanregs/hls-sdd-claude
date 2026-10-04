package com.hls.identity.clientcontext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Records one API Access entry for every request made by the Android app (spec 018 FR-023a,
 * research.md §7a). It is the outermost filter, so responses produced before the application sees
 * the request (401 from security, 426 from the version gate) are recorded too, and it publishes
 * after the response is written so the status code is known.
 *
 * <p>Only the method, the matched route pattern, the status and the client context are used: never
 * the query string, a header value other than through the client context, an id from the path, or a
 * body. A failure while recording is logged and swallowed, so it can never change the response
 * (FR-025).
 */
public class ApiAccessFilter extends OncePerRequestFilter {

    /** Request attributes set by {@link ApiAccessPrincipalFilter} once a valid token is authenticated. */
    public static final String USER_ID_ATTRIBUTE = "com.hls.identity.clientcontext.userId";

    public static final String SESSION_ID_ATTRIBUTE = "com.hls.identity.clientcontext.sessionId";

    static final String APP_CONFIG_PATH = "/api/v1/mobile/app-config";
    static final String UNMATCHED = "UNMATCHED";

    private static final Logger log = LoggerFactory.getLogger(ApiAccessFilter.class);

    private final ApiAccessPublisher publisher;

    public ApiAccessFilter(ApiAccessPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            chain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            record(request, status);
        }
    }

    private void record(HttpServletRequest request, int status) {
        try {
            if (!(request.getAttribute(ClientContext.REQUEST_ATTRIBUTE) instanceof ClientContext context)
                    || !context.isAndroid()
                    || APP_CONFIG_PATH.equals(request.getRequestURI())) {
                return;
            }
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            publisher.record(
                    uuidAttribute(request, USER_ID_ATTRIBUTE),
                    uuidAttribute(request, SESSION_ID_ATTRIBUTE),
                    request.getMethod(),
                    pattern instanceof String route && !route.isBlank() ? route : UNMATCHED,
                    status,
                    context);
        } catch (RuntimeException e) {
            log.warn("Could not record API access entry: {}", e.toString());
        }
    }

    private static UUID uuidAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        if (value instanceof String text) {
            try {
                return UUID.fromString(text);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }
}
