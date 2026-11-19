package org.group10.common.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las rutas {@code /internal/**} solo las usan los servicios entre sí (US-PAY-14): sin el secreto compartido, 401.
 */
class InternalSecretInterceptorTest {

    @Nested
    @WebMvcTest(value = FakeController.class, properties = "ticketya.internal.secret=s3cret-de-prueba")
    class WithConfiguredSecret {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void internalRoute_withTheSecret_passes() throws Exception {
            mockMvc.perform(post("/internal/things").header("X-Internal-Secret", "s3cret-de-prueba"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("internal"));
        }

        @Test
        void internalRoute_withoutTheSecret_returns401() throws Exception {
            mockMvc.perform(post("/internal/things"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("unauthorized"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"otro", "s3cret-de-prueb", "s3cret-de-prueba ", "", "dev-internal-secret"})
        void internalRoute_withAnotherSecret_returns401(String secret) throws Exception {
            mockMvc.perform(post("/internal/things").header("X-Internal-Secret", secret))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("unauthorized"));
        }

        @Test
        void apiRoute_doesNotNeedTheSecret() throws Exception {
            mockMvc.perform(get("/api/things")).andExpect(status().isOk());
        }
    }

    @Nested
    @WebMvcTest(FakeController.class)
    class WithoutConfiguredSecret {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void internalRoute_usesTheDevelopmentSecret() throws Exception {
            mockMvc.perform(post("/internal/things").header("X-Internal-Secret", "dev-internal-secret"))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/internal/things")).andExpect(status().isUnauthorized());
        }
    }
}
