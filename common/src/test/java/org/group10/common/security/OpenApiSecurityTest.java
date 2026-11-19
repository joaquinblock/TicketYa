package org.group10.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Swagger muestra qué endpoints piden token y qué permiso: lo arma {@code common} leyendo
 * {@link RequiresPermission}, en todos los servicios.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_declareTheBearerTokenScheme() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"));
    }

    @Test
    void apiDocs_serverIsRelative_soTryItOutGoesThroughTheGatewayThatServesTheSwagger() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.servers.length()").value(1))
                .andExpect(jsonPath("$.servers[0].url").value("/"));
    }

    @Test
    void protectedEndpoint_asksForTheTokenAndSaysWhichPermission() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/things'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/things'].post.description",
                        containsString("Requiere el permiso `EVENT_MANAGE`")))
                .andExpect(jsonPath("$.paths['/api/things'].post.responses['401'].description").exists())
                .andExpect(jsonPath("$.paths['/api/things'].post.responses['403'].description",
                        containsString("EVENT_MANAGE")));
    }

    @Test
    void endpointWithSeveralPermissions_saysThatAnyOfThemIsEnough() throws Exception {
        apiDocs().andExpect(jsonPath("$.paths['/api/things/suspend'].post.description",
                containsString("Requiere uno de estos permisos: `EVENT_ADMIN`, `EVENT_MANAGE`")));
    }

    @Test
    void publicEndpoint_hasNoLock() throws Exception {
        apiDocs()
                .andExpect(jsonPath("$.paths['/api/things'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/things'].get.responses['401']").doesNotExist());
    }

    private ResultActions apiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
