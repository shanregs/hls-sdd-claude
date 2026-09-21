package com.hls.identity.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SecurityMdcInterceptor securityMdcInterceptor;

    public WebConfig(SecurityMdcInterceptor securityMdcInterceptor) {
        this.securityMdcInterceptor = securityMdcInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(securityMdcInterceptor);
    }
}
