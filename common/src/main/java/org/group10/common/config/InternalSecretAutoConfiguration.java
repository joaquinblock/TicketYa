package org.group10.common.config;

import org.group10.common.security.InternalSecretInterceptor;
import org.group10.common.security.InternalSecretRequestInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * El secreto compartido de las rutas {@code /internal/**} (US-PAY-14), en cada servicio que depende de {@code common}:
 * lo exige al recibirlas y lo agrega en las llamadas a las de otro servicio (los clientes {@code @HttpExchange}).
 * <p>
 * Sale de {@code INTERNAL_SECRET}. Sin la variable se usa un valor de desarrollo, que está en el repo: sirve para
 * levantar todo en local y en los tests, y el {@code docker-compose} general lo reemplaza por uno real. Se loguea un
 * {@code WARN} para que no pase desapercibido.
 * <p>
 * No aplica al api-gateway: no tiene rutas {@code /internal} ni llama a las de nadie, y para él tienen que seguir
 * dando 404 ({@code InternalRoutesTest}).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnMissingClass("org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions")
public class InternalSecretAutoConfiguration {

    static final String DEVELOPMENT_SECRET = "dev-internal-secret";

    private static final Logger log = LoggerFactory.getLogger(InternalSecretAutoConfiguration.class);

    private final String secret;

    public InternalSecretAutoConfiguration(
            @Value("${ticketya.internal.secret:${INTERNAL_SECRET:" + DEVELOPMENT_SECRET + "}}") String secret) {
        if (DEVELOPMENT_SECRET.equals(secret)) {
            log.warn("INTERNAL_SECRET no está configurada: /internal/** usa el secreto de desarrollo, que está en el repo");
        }
        this.secret = secret;
    }

    @Bean
    WebMvcConfigurer ticketyaInternalSecretWebMvcConfigurer() {
        InternalSecretInterceptor interceptor = new InternalSecretInterceptor(secret);
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor).addPathPatterns("/internal/**");
            }
        };
    }

    /**
     * Los clientes {@code @HttpExchange} de cada servicio mandan el secreto solos: ningún servicio tiene que tocar
     * sus clientes.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestClientHttpServiceGroupConfigurer.class)
    static class SendTheSecret {

        @Bean
        RestClientHttpServiceGroupConfigurer ticketyaInternalSecretConfigurer(InternalSecretAutoConfiguration config) {
            InternalSecretRequestInterceptor interceptor = new InternalSecretRequestInterceptor(config.secret);
            return groups -> groups.forEachClient((group, builder) -> builder.requestInterceptor(interceptor));
        }
    }
}
