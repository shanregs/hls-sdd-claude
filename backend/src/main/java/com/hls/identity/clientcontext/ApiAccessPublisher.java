package com.hls.identity.clientcontext;

import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes {@link ApiAccessRecorded}. {@code @Transactional} is deliberate even though nothing is
 * written here: Spring Modulith only delivers {@code @ApplicationModuleListener} events once the
 * publishing transaction commits, and a servlet filter has no transaction of its own, so without it
 * the event would be silently dropped (the same reason {@code LoginHistoryPublisher} has it).
 */
@Service
public class ApiAccessPublisher {

    static final int MAX_ROUTE_LENGTH = 200;

    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ApiAccessPublisher(ApplicationEventPublisher eventPublisher, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void record(
            UUID userId,
            UUID sessionId,
            String httpMethod,
            String routeTemplate,
            int statusCode,
            ClientContext clientContext) {
        eventPublisher.publishEvent(new ApiAccessRecorded(
                UUID.randomUUID(),
                clock.instant(),
                userId,
                sessionId,
                httpMethod.length() > 10 ? httpMethod.substring(0, 10) : httpMethod,
                routeTemplate.length() > MAX_ROUTE_LENGTH ? routeTemplate.substring(0, MAX_ROUTE_LENGTH) : routeTemplate,
                statusCode,
                clientContext));
    }
}
