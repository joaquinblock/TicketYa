package org.group10.ticket.controller;

import org.group10.ticket.dto.EventReportResponse;
import org.group10.ticket.service.EventReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventReportController.class)
class EventReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventReportService eventReportService;

    @Test
    void findReport_withoutIdentity_returns401() throws Exception {
        mockMvc.perform(get("/api/events/1/report"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));

        verifyNoInteractions(eventReportService);
    }

    @Test
    void findReport_withoutReportPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/events/1/report")
                        .header("X-User-Id", 7)
                        .header("X-User-Permissions", "EVENT_MANAGE")
                        .header("X-Organization-Id", 3))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));

        verifyNoInteractions(eventReportService);
    }

    @Test
    void findReport_withReportPermission_returnsReport() throws Exception {
        when(eventReportService.findReport(any(), eq(1L))).thenReturn(new EventReportResponse(1L,
                List.of(new EventReportResponse.SectorReport(2L, "Campo", 4, 3, 1,
                        new BigDecimal("75.00"), new BigDecimal("300.00"))),
                new EventReportResponse.EventTotals(4, 3, 1, new BigDecimal("75.00"), new BigDecimal("300.00"))));

        mockMvc.perform(get("/api/events/1/report")
                        .header("X-User-Id", 7)
                        .header("X-User-Permissions", "REPORT_VIEW")
                        .header("X-Organization-Id", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.sectors[0].soldTickets").value(3))
                .andExpect(jsonPath("$.sectors[0].returnedTickets").value(1))
                .andExpect(jsonPath("$.sectors[0].occupancyPercentage").value(75.00))
                .andExpect(jsonPath("$.total.revenue").value(300.00));
    }
}
