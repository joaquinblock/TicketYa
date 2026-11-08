package org.group10.event.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.event.dto.VenueSummaryResponse;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.service.VenueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VenueController.class)
class VenueControllerTest {

    private static final String VALID_VENUE = """
            {"name": "Estadio del Sur", "address": "Av. del Estadio 1000", "city": "Buenos Aires",
             "latitude": -34.5453, "longitude": -58.4498}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VenueService venueService;

    @Test
    void create_validVenue_returns201() throws Exception {
        when(venueService.create(any())).thenReturn(
                new VenueSummaryResponse(1L, "Estadio del Sur", "Av. del Estadio 1000", "Buenos Aires", -34.5453, -58.4498));

        mockMvc.perform(post("/api/venues").contentType(MediaType.APPLICATION_JSON).content(VALID_VENUE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_withLatitudeOutOfRange_returns400WithTheField() throws Exception {
        String body = VALID_VENUE.replace("-34.5453", "-95");

        mockMvc.perform(post("/api/venues").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.detail.latitude").exists());
    }

    @Test
    void findById_missingVenue_returns404() throws Exception {
        when(venueService.findById(7L)).thenThrow(
                new TicketYaException(EventErrorCode.VENUE_NOT_FOUND, "No se encontró el lugar con id 7"));

        mockMvc.perform(get("/api/venues/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("venue_not_found"));
    }

    @Test
    void delete_venueInUse_returns409() throws Exception {
        doThrow(new TicketYaException(EventErrorCode.VENUE_IN_USE, "El lugar 1 tiene eventos"))
                .when(venueService).delete(1L);

        mockMvc.perform(delete("/api/venues/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("venue_in_use"));
    }

    @Test
    void delete_unusedVenue_returns204() throws Exception {
        mockMvc.perform(delete("/api/venues/1")).andExpect(status().isNoContent());
    }
}
