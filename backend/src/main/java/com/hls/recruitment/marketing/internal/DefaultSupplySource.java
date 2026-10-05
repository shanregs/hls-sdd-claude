package com.hls.recruitment.marketing.internal;

import com.hls.recruitment.api.SupplySource;
import java.util.OptionalInt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Used when no module provides the number of recruits ready to deploy: the dashboard says "not available". */
@Configuration(proxyBeanMethods = false)
class DefaultSupplySource {

    @Bean
    @ConditionalOnMissingBean(SupplySource.class)
    SupplySource noSupplySource() {
        return OptionalInt::empty;
    }
}
