package org.group10.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void ticketYaException_returnsStatusCodeMessageAndDetailOfItsErrorCode() throws Exception {
        mockMvc.perform(get("/things/business-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("thing_not_found"))
                .andExpect(jsonPath("$.message").value("No se encontró la cosa con id 7"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail.id").value(7));
    }

    @Test
    void invalidBody_returns400WithEachInvalidField() throws Exception {
        mockMvc.perform(post("/things")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.detail.name").exists())
                .andExpect(jsonPath("$.detail.quantity").exists());
    }

    @Test
    void invalidEnumInBody_returns400WithFieldAndAllowedValues() throws Exception {
        mockMvc.perform(post("/things")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"a\",\"quantity\":1,\"color\":\"GREEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.detail.field").value("color"))
                .andExpect(jsonPath("$.detail.rejectedValue").value("GREEN"))
                .andExpect(jsonPath("$.detail.allowedValues[0]").value("RED"));
    }

    @Test
    void malformedJson_returns400WithGenericMessage() throws Exception {
        mockMvc.perform(post("/things")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo del request es inválido"));
    }

    @Test
    void invalidPathVariable_returns400WithParameter() throws Exception {
        mockMvc.perform(get("/things/-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.detail.id").exists());
    }

    @Test
    void pathVariableOfWrongType_returns400() throws Exception {
        mockMvc.perform(get("/things/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"))
                .andExpect(jsonPath("$.message").value("Valor inválido 'abc' para el parámetro 'id'"));
    }

    @Test
    void missingRequestParam_returns400() throws Exception {
        mockMvc.perform(get("/things/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el parámetro obligatorio 'city'"));
    }

    @Test
    void missingHeader_returns400() throws Exception {
        mockMvc.perform(get("/things/mine"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Falta el header obligatorio 'X-User-Id'"));
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        mockMvc.perform(post("/things/search"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error").value("method_not_allowed"));
    }

    @Test
    void dataIntegrityViolation_returns409() throws Exception {
        mockMvc.perform(get("/things/duplicated"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("data_conflict"));
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

    @Getter
    enum FakeErrorCode implements ErrorCode {
        THING_NOT_FOUND(HttpStatus.NOT_FOUND);

        private final HttpStatus status;

        FakeErrorCode(HttpStatus status) {
            this.status = status;
        }
    }

    enum Color { RED, BLUE }

    record ThingRequest(@NotBlank String name, @Positive Integer quantity, Color color) {}

    @RestController
    static class FakeController {

        @GetMapping("/things/business-error")
        void businessError() {
            throw new TicketYaException(FakeErrorCode.THING_NOT_FOUND, "No se encontró la cosa con id 7", Map.of("id", 7));
        }

        @PostMapping("/things")
        void create(@Valid @RequestBody ThingRequest request) {
        }

        @GetMapping("/things/{id}")
        void getById(@PathVariable @Positive Long id) {
        }

        @GetMapping("/things/search")
        void search(@RequestParam String city) {
        }

        @GetMapping("/things/mine")
        void mine(@RequestHeader("X-User-Id") Long userId) {
        }

        @GetMapping("/things/duplicated")
        void duplicated() {
            throw new DataIntegrityViolationException("Duplicate entry");
        }

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
