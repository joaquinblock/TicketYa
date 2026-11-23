package org.group10.queue.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QueueJwksController.class)
@Import(QueueJwksControllerTest.KeyConfig.class)
class QueueJwksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void jwks_returnsOnlyPublicRsaKey() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    @TestConfiguration
    static class KeyConfig {

        @Bean
        JWKSet publicJwkSet() throws Exception {
            RSAKey key = new RSAKeyGenerator(2048).keyID("queue-test-key").generate();
            return new JWKSet(key.toPublicJWK());
        }
    }
}
