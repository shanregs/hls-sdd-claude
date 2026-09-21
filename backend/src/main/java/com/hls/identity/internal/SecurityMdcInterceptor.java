package com.hls.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * T051 / Constitution Principle VIII: puts {@code userId} and {@code role} into
 * MDC for the duration of an authenticated request, so every log line (not just
 * the ones this module writes explicitly) carries who made the request — the
 * same "structured logging with request correlation" discipline
 * {@code CorrelationIdFilter} already applies to the correlation id.
 */
@Component
public class SecurityMdcInterceptor implements HandlerInterceptor {

    static final String USER_ID_KEY = "userId";
    static final String ROLE_KEY = "role";

    private final CurrentUserExtractor currentUserExtractor;

    public SecurityMdcInterceptor(CurrentUserExtractor currentUserExtractor) {
        this.currentUserExtractor = currentUserExtractor;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Jwt jwt = jwtAuthentication.getToken();
            MDC.put(USER_ID_KEY, currentUserExtractor.userId(jwt).toString());
            MDC.put(ROLE_KEY, currentUserExtractor.roles(jwt).toString());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(USER_ID_KEY);
        MDC.remove(ROLE_KEY);
    }
}
