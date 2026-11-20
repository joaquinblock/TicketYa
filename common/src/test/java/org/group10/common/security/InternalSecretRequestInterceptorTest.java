package org.group10.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Quien llama a otro servicio manda el secreto solo a {@code /internal/**}: a una ruta pública no le hace falta y no
 * tiene por qué viajar.
 */
class InternalSecretRequestInterceptorTest {

    @Test
    void callToAnInternalRoute_carriesTheSecret() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payment-service")
                .requestInterceptor(new InternalSecretRequestInterceptor("s3cret"));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://payment-service/internal/payments"))
                .andExpect(header("X-Internal-Secret", "s3cret"))
                .andRespond(withSuccess());

        builder.build().post().uri("/internal/payments").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void callToAPublicRoute_doesNotCarryTheSecret() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://event-service")
                .requestInterceptor(new InternalSecretRequestInterceptor("s3cret"));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://event-service/api/events/1"))
                .andExpect(headerDoesNotExist("X-Internal-Secret"))
                .andRespond(withSuccess());

        builder.build().get().uri("/api/events/1").retrieve().toBodilessEntity();

        server.verify();
    }
}
