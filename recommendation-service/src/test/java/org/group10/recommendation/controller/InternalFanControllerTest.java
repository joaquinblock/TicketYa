package org.group10.recommendation.controller;

import org.group10.recommendation.service.FanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los fans que pide el notification-service al publicarse un evento: solo con el secreto entre servicios.
 */
@WebMvcTest(InternalFanController.class)
class InternalFanControllerTest {

    private static final String SECRET = "dev-internal-secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FanService fanService;

    @Test
    void findFans_withTheSecret_returns200WithTheUserIds() throws Exception {
        when(fanService.findFans(List.of(10L, 11L), "Tandil")).thenReturn(List.of(7L, 9L));

        mockMvc.perform(get("/internal/fans").header("X-Internal-Secret", SECRET)
                        .param("participantIds", "10", "11").param("city", "Tandil"))
                .andExpect(status().isOk())
                .andExpect(content().json("[7, 9]"));
    }

    @Test
    void findFans_commaSeparatedParticipants_areReadAsAList() throws Exception {
        when(fanService.findFans(List.of(10L, 11L), "Tandil")).thenReturn(List.of(7L));

        mockMvc.perform(get("/internal/fans").header("X-Internal-Secret", SECRET)
                        .param("participantIds", "10,11").param("city", "Tandil"))
                .andExpect(status().isOk())
                .andExpect(content().json("[7]"));
    }

    @Test
    void findFans_withoutTheSecret_returns401WithoutLookingUpFans() throws Exception {
        mockMvc.perform(get("/internal/fans").param("participantIds", "10").param("city", "Tandil"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(fanService);
    }

    @Test
    void findFans_withoutParticipants_returns400() throws Exception {
        mockMvc.perform(get("/internal/fans").header("X-Internal-Secret", SECRET).param("city", "Tandil"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));
        verifyNoInteractions(fanService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void findFans_nonPositiveParticipantId_returns400(String participantId) throws Exception {
        mockMvc.perform(get("/internal/fans").header("X-Internal-Secret", SECRET)
                        .param("participantIds", participantId).param("city", "Tandil"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(fanService);
    }

    @Test
    void findFans_participantIdThatIsNotANumber_returns400() throws Exception {
        mockMvc.perform(get("/internal/fans").header("X-Internal-Secret", SECRET)
                        .param("participantIds", "abc").param("city", "Tandil"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));
        verifyNoInteractions(fanService);
    }

    @Test
    void findFans_withoutCity_returns400() throws Exception {
        mockMvc.perform(get("/internal/fans").header("X-Internal-Secret", SECRET).param("participantIds", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));
        verifyNoInteractions(fanService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void findFans_blankCity_returns400(String city) throws Exception {
        mockMvc.perform(get("/internal/fans").header("X-Internal-Secret", SECRET)
                        .param("participantIds", "10").param("city", city))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(fanService);
    }
}
