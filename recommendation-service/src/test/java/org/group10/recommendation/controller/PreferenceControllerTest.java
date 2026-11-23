package org.group10.recommendation.controller;

import org.group10.common.security.Caller;
import org.group10.recommendation.dto.PreferencesRequest;
import org.group10.recommendation.dto.PreferencesResponse;
import org.group10.recommendation.service.PreferenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreferenceController.class)
class PreferenceControllerTest {

    private static final Caller CLIENT = new Caller(7L, "CLIENT", Set.of(), null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PreferenceService preferenceService;

    @Test
    void get_client_returnsItsPreferences() throws Exception {
        when(preferenceService.get(CLIENT))
                .thenReturn(new PreferencesResponse(List.of("CONCERT"), List.of(12L), "Tandil"));

        mockMvc.perform(get("/api/preferences/me").header("X-User-Id", 7).header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventTypes[0]").value("CONCERT"))
                .andExpect(jsonPath("$.participantIds[0]").value(12))
                .andExpect(jsonPath("$.city").value("Tandil"));
    }

    @Test
    void get_withoutIdentity_returns401() throws Exception {
        mockMvc.perform(get("/api/preferences/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(preferenceService);
    }

    @Test
    void replace_client_passesTheBodyAsSentAndReturnsTheSavedPreferences() throws Exception {
        PreferencesRequest request = new PreferencesRequest(List.of("CONCERT"), List.of(12L, 40L), "Tandil");
        when(preferenceService.replace(CLIENT, request))
                .thenReturn(new PreferencesResponse(List.of("CONCERT"), List.of(12L, 40L), "Tandil"));

        mockMvc.perform(put("/api/preferences/me").header("X-User-Id", 7).header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventTypes": ["CONCERT"], "participantIds": [12, 40], "city": "Tandil"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantIds.length()").value(2));
        verify(preferenceService).replace(CLIENT, request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "[]", "{\"participantIds\": [\"abc\"]}", "{\"participantIds\": 12}",
            "{\"participantIds\": [null]}", "{\"eventTypes\": [null]}"})
    void replace_malformedBody_returns400(String body) throws Exception {
        mockMvc.perform(put("/api/preferences/me").header("X-User-Id", 7).header("X-User-Role", "CLIENT")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
        verifyNoInteractions(preferenceService);
    }

    @Test
    void replace_withoutIdentity_returns401() throws Exception {
        mockMvc.perform(put("/api/preferences/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(preferenceService);
    }
}
