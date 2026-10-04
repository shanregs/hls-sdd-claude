package com.hls.identity.mobile;

import com.hls.identity.clientcontext.ClientContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses an Android app older than the configured minimum with {@code 426 APP_UPDATE_REQUIRED}
 * (spec 018 FR-029). Every endpoint is gated except the public app-configuration request, which is
 * how the app learns the minimum in the first place. Web requests are never affected.
 */
public class MobileVersionGate extends OncePerRequestFilter {

    static final String APP_CONFIG_PATH = "/api/v1/mobile/app-config";

    private final AppVersion minimumVersion;

    public MobileVersionGate(AppVersion minimumVersion) {
        this.minimumVersion = minimumVersion;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object attribute = request.getAttribute(ClientContext.REQUEST_ATTRIBUTE);
        if (attribute instanceof ClientContext context
                && context.isAndroid()
                && !APP_CONFIG_PATH.equals(request.getRequestURI())
                && isBelowMinimum(context.appVersion())) {
            response.setStatus(426);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter()
                    .write("{\"code\":\"APP_UPDATE_REQUIRED\","
                            + "\"message\":\"Please update the HLS app to continue.\","
                            + "\"minimumVersion\":\"" + minimumVersion + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isBelowMinimum(String appVersion) {
        try {
            return AppVersion.parse(appVersion).compareTo(minimumVersion) < 0;
        } catch (IllegalArgumentException e) {
            // The parser only yields well-formed versions; an unreadable one is treated as too old.
            return true;
        }
    }
}
