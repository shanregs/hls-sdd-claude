package com.hls.identity.mobile;

import com.hls.identity.clientcontext.ApiAccessFilter;
import com.hls.identity.clientcontext.ApiAccessPublisher;
import com.hls.identity.clientcontext.ClientContextFilter;
import com.hls.identity.clientcontext.ClientContextParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers spec 018's request filters ahead of Spring Security (whose chain runs at order -100) so
 * that requests the security chain rejects, and apps the version gate turns away, are still seen by
 * the API Access trail (research.md §7a).
 */
@Configuration
public class MobileFilterConfig {

    /** Runs first among spec 018's filters; the API Access filter (later task) wraps outside it. */
    public static final int API_ACCESS_ORDER = -300;
    public static final int CLIENT_CONTEXT_ORDER = -250;
    public static final int VERSION_GATE_ORDER = -240;

    /** Outermost: wraps everything so rejected and gated requests are recorded too (FR-023a). */
    @Bean
    FilterRegistrationBean<ApiAccessFilter> apiAccessFilterRegistration(ApiAccessPublisher publisher) {
        var registration = new FilterRegistrationBean<>(new ApiAccessFilter(publisher));
        registration.addUrlPatterns("/*");
        registration.setOrder(API_ACCESS_ORDER);
        return registration;
    }

    @Bean
    FilterRegistrationBean<ClientContextFilter> clientContextFilterRegistration(ClientContextParser parser) {
        var registration = new FilterRegistrationBean<>(new ClientContextFilter(parser));
        registration.addUrlPatterns("/*");
        registration.setOrder(CLIENT_CONTEXT_ORDER);
        return registration;
    }

    @Bean
    FilterRegistrationBean<MobileVersionGate> mobileVersionGateRegistration(
            @Value("${hls.mobile.min-app-version:0.0.0}") String minimumVersion) {
        var registration = new FilterRegistrationBean<>(new MobileVersionGate(AppVersion.parse(minimumVersion)));
        registration.addUrlPatterns("/*");
        registration.setOrder(VERSION_GATE_ORDER);
        return registration;
    }
}
