package org.group10.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void optimisticLockFailure_returns409() throws Exception {
        mockMvc.perform(get("/things/concurrent-update"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("data_conflict"));
    }

    @Test
    void downstream4xxWithErrorDto_isForwardedAsIs() throws Exception {
        mockMvc.perform(get("/things/downstream-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("event_not_found"))
                .andExpect(jsonPath("$.message").value("No se encontró el evento con id 7"));
    }

    @Test
    void downstream5xx_returns503() throws Exception {
        mockMvc.perform(get("/things/downstream-error"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
    }

    @Test
    void downstreamUnreachable_returns503() throws Exception {
        mockMvc.perform(get("/things/downstream-down"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
    }

    @Test
    void unexpectedException_returns500WithoutLeakingItsMessage() throws Exception {
        mockMvc.perform(get("/things/bug"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal_error"))
                .andExpect(jsonPath("$.message").value("Error interno del servidor"));
    }

    @RestController
    static class FakeController {

        @GetMapping("/things/concurrent-update")
        void concurrentUpdate() {
            throw new OptimisticLockingFailureException("Row was updated by another transaction");
        }

        @GetMapping("/things/downstream-not-found")
        void downstreamNotFound() {
            String body = "{\"error\":\"event_not_found\",\"message\":\"No se encontró el evento con id 7\",\"status\":404}";
            HttpClientErrorException ex = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                    jsonHeaders(), body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            ex.setBodyConvertFunction(type -> MAPPER.readValue(body, MAPPER.constructType(type.getType())));
            throw ex;
        }

        @GetMapping("/things/downstream-error")
        void downstreamError() {
            throw HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                    jsonHeaders(), new byte[0], StandardCharsets.UTF_8);
        }

        @GetMapping("/things/downstream-down")
        void downstreamDown() {
            throw new ResourceAccessException("Connection refused");
        }

        @GetMapping("/things/bug")
        void bug() {
            throw new IllegalStateException("detalle interno que no debe llegar al cliente");
        }

        private static HttpHeaders jsonHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return headers;
        }
    }
}
