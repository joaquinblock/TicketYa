package org.group10.common.config;

import org.group10.common.tracing.CorrelationFilter;
import org.group10.common.tracing.CorrelationRestClientInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TracingAutoConfiguration {

    @Bean
    FilterRegistrationBean<CorrelationFilter> ticketyaCorrelationFilter() {
        FilterRegistrationBean<CorrelationFilter> registration = new FilterRegistrationBean<>(new CorrelationFilter());
        registration.setOrder(Integer.MIN_VALUE);
        return registration;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestClientHttpServiceGroupConfigurer.class)
    static class PropagateCorrelation {

        @Bean
        RestClientHttpServiceGroupConfigurer ticketyaCorrelationConfigurer() {
            CorrelationRestClientInterceptor interceptor = new CorrelationRestClientInterceptor();
            return groups -> groups.forEachClient((group, builder) -> builder.requestInterceptor(interceptor));
        }
    }
}
