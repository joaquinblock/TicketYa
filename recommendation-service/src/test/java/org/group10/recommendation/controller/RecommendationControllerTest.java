package org.group10.recommendation.controller;

import org.group10.common.exception.CommonErrorCode;
import org.group10.common.exception.TicketYaException;
import org.group10.common.security.Caller;
import org.group10.recommendation.dto.EventSummary;
import org.group10.recommendation.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecommendationController.class)
class RecommendationControllerTest {

    private static final Caller CLIENT = new Caller(7L, "CLIENT", Set.of(), null);
    private static final Caller ORGANIZER = new Caller(8L, "ORGANIZER", Set.of(), null);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService recommendationService;

    @Test
    void client_getsTheSuggestionsWithTheSameFieldsAsTheSearch() throws Exception {
        when(recommendationService.suggest(CLIENT)).thenReturn(List.of(new EventSummary(4L, "Festival de Verano",
                "FESTIVAL", "PUBLISHED", LocalDateTime.of(2027, 1, 21, 18, 0), true, 1L,
                new EventSummary.Venue(3L, "Arena Córdoba", "Av. Cárcano 1500", "Córdoba", null, null),
                new BigDecimal("38000.00"), List.of("Los Serranos"))));

        mockMvc.perform(get("/api/recommendations/me").header("X-User-Id", 7).header("X-User-Role", "CLIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4))
                .andExpect(jsonPath("$[0].type").value("FESTIVAL"))
                .andExpect(jsonPath("$[0].dateTime").value("2027-01-21T18:00:00"))
                .andExpect(jsonPath("$[0].venue.city").value("Córdoba"))
                .andExpect(jsonPath("$[0].priceFrom").value(38000.00))
                .andExpect(jsonPath("$[0].participants[0]").value("Los Serranos"));
    }

    @Test
    void withoutIdentity_returns401() throws Exception {
        mockMvc.perform(get("/api/recommendations/me"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(recommendationService);
    }

    @Test
    void nonClient_returns403() throws Exception {
        when(recommendationService.suggest(ORGANIZER))
                .thenThrow(new TicketYaException(CommonErrorCode.FORBIDDEN, "Solo clientes"));

        mockMvc.perform(get("/api/recommendations/me").header("X-User-Id", 8).header("X-User-Role", "ORGANIZER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void eventServiceDown_returns503() throws Exception {
        when(recommendationService.suggest(CLIENT)).thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(get("/api/recommendations/me").header("X-User-Id", 7).header("X-User-Role", "CLIENT"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
    }
}
