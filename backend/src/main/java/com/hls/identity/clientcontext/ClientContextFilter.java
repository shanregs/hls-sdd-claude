package com.hls.identity.clientcontext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Parses the {@code X-HLS-*} headers once per request and stores the result as a request attribute
 * (spec 018 T011). It only records facts; it never blocks or changes a request.
 */
public class ClientContextFilter extends OncePerRequestFilter {

    private final ClientContextParser parser;

    public ClientContextFilter(ClientContextParser parser) {
        this.parser = parser;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(ClientContext.REQUEST_ATTRIBUTE, parser.parse(request));
        chain.doFilter(request, response);
    }
}
