package org.group10.event.controller;

import org.group10.common.exception.TicketYaException;
import org.group10.event.client.AuthClient;
import org.group10.event.dto.ParticipantResponse;
import org.group10.event.entity.ParticipantType;
import org.group10.event.exception.EventErrorCode;
import org.group10.event.service.ParticipantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParticipantController.class)
class ParticipantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParticipantService participantService;

    /** Lo necesita {@code PermissionInterceptor}, que {@code @WebMvcTest} carga para cualquier controller. */
    @MockitoBean
    private AuthClient authClient;

    @Test
    void findAll_withFilters_passesThemToTheService() throws Exception {
        when(participantService.findAll(ParticipantType.ARTIST, "serr"))
                .thenReturn(List.of(new ParticipantResponse(1L, "Los Serranos", ParticipantType.ARTIST)));

        mockMvc.perform(get("/api/participants").param("type", "ARTIST").param("name", "serr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Los Serranos"));
        verifyNoInteractions(authClient);
    }

    @Test
    void findAll_withUnknownType_returns400() throws Exception {
        mockMvc.perform(get("/api/participants").param("type", "BAND"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parameter"));
    }

    @Test
    void create_valid_returns201() throws Exception {
        when(participantService.create(any()))
                .thenReturn(new ParticipantResponse(11L, "Trío Costero", ParticipantType.ARTIST));

        mockMvc.perform(post("/api/participants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Trío Costero\", \"type\": \"ARTIST\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11));
    }

    @Test
    void create_withoutNameNorType_returns400WithBothFields() throws Exception {
        mockMvc.perform(post("/api/participants").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"))
                .andExpect(jsonPath("$.detail.name").exists())
                .andExpect(jsonPath("$.detail.type").exists());
    }

    @Test
    void create_duplicated_returns409() throws Exception {
        when(participantService.create(any())).thenThrow(new TicketYaException(EventErrorCode.PARTICIPANT_DUPLICATED,
                "Ya existe el participante 'Los Serranos' de tipo ARTIST"));

        mockMvc.perform(post("/api/participants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Los Serranos\", \"type\": \"ARTIST\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("participant_duplicated"));
    }

    @Test
    void delete_inUse_returns409() throws Exception {
        doThrow(new TicketYaException(EventErrorCode.PARTICIPANT_IN_USE, "El participante 1 está en eventos"))
                .when(participantService).delete(1L);

        mockMvc.perform(delete("/api/participants/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("participant_in_use"));
    }

    @Test
    void delete_free_returns204() throws Exception {
        mockMvc.perform(delete("/api/participants/11")).andExpect(status().isNoContent());
    }
}
