package com.hls.identity.clientcontext;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Access to the {@link ClientContext} of the request being handled (spec 018 research.md §7).
 * Event publishers read it at publish time, because the audit consumers run after the request on
 * another thread. Outside a request (a background job, a unit test) it returns the web context with
 * no location.
 */
public final class ClientContextHolder {

    private ClientContextHolder() {}

    public static ClientContext current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return ClientContext.web();
        }
        Object value = attributes.getAttribute(ClientContext.REQUEST_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof ClientContext context ? context : ClientContext.web();
    }
}
