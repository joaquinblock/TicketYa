package org.group10.common.config;

import org.group10.common.security.CallerArgumentResolver;
import org.group10.common.security.PermissionInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Registra el chequeo de {@code @RequiresPermission} en cada microservicio que depende de {@code common}, para las
 * rutas {@code /api/**}. Las {@code /internal/**} no tienen usuario: las protege otro mecanismo.
 * <p>
 * El interceptor no se registra como bean, así no choca con el {@code permissionInterceptor} propio que tiene
 * event-service hasta migrar.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PermissionAutoConfiguration {

    @Bean
    WebMvcConfigurer ticketyaPermissionWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new PermissionInterceptor()).addPathPatterns("/api/**");
            }

            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CallerArgumentResolver());
            }
        };
    }
}
