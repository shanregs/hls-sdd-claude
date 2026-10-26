package com.hls.files.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The multipart ceiling is raised for content uploads (spec 029: video up to 500 MB). Every other upload path keeps its
 * old 12 MB request ceiling, enforced here from {@code Content-Length} before the body is read, so a large body sent to an
 * expense bill or a task is refused at once instead of being buffered first.
 */
@Component
public class UploadSizeGuardFilter extends OncePerRequestFilter {

    /** The request ceiling that applied everywhere before spec 029. */
    static final long OTHER_PATHS_MAX_REQUEST_BYTES = 12L * 1024 * 1024;

    /** The only path prefix allowed to carry a larger body. */
    static final String LARGE_UPLOAD_PREFIX = "/api/v1/content/";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String type = request.getContentType();
        boolean multipart = type != null && type.toLowerCase().startsWith("multipart/");
        if (multipart
                && !request.getRequestURI().startsWith(LARGE_UPLOAD_PREFIX)
                && request.getContentLengthLong() > OTHER_PATHS_MAX_REQUEST_BYTES) {
            response.setStatus(413);
            response.setContentType("application/json");
            response.getOutputStream()
                    .write("{\"reason\":\"A file can be at most 10 MB.\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
