package com.hls.identity.clientcontext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs inside the security chain, after the bearer token has been validated, and copies the
 * authenticated user and session ids onto the request so the outer {@link ApiAccessFilter} can read
 * them once the chain returns (the security context is cleared by then). Only a token that passed
 * every check, including "session still active", ever reaches this filter's attributes, so an API
 * Access entry can never be attributed to a forged identity.
 */
public class ApiAccessPrincipalFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            request.setAttribute(ApiAccessFilter.USER_ID_ATTRIBUTE, jwt.getSubject());
            request.setAttribute(ApiAccessFilter.SESSION_ID_ATTRIBUTE, jwt.getClaimAsString("sid"));
        }
        chain.doFilter(request, response);
    }
}
