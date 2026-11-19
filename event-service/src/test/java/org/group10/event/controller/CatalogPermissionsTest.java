package org.group10.event.controller;

import org.group10.event.service.ParticipantService;
import org.group10.event.service.SectorService;
import org.group10.event.service.VenueService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quién puede modificar el catálogo: los lugares (con sus sectores y asientos) solo el admin, con VENUE_MANAGE; los
 * participantes, organizadores y admin, con PARTICIPANT_MANAGE. Leerlo sigue siendo público.
 */
@WebMvcTest({VenueController.class, SectorController.class, SeatController.class, ParticipantController.class})
class CatalogPermissionsTest {

    private static final String VENUE = """
            {"name": "Teatro", "address": "Av. 1", "city": "Tandil", "latitude": -37.3, "longitude": -59.1}""";
    private static final String PARTICIPANT = """
            {"name": "Los Serranos", "type": "ARTIST"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VenueService venueService;

    @MockitoBean
    private SectorService sectorService;

    @MockitoBean
    private ParticipantService participantService;

    static Stream<Arguments> venueWrites() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/venues"),
                Arguments.of(HttpMethod.PUT, "/api/venues/1"),
                Arguments.of(HttpMethod.DELETE, "/api/venues/1"),
                Arguments.of(HttpMethod.POST, "/api/venues/1/sectors"),
                Arguments.of(HttpMethod.PUT, "/api/venues/1/sectors/2"),
                Arguments.of(HttpMethod.DELETE, "/api/venues/1/sectors/2"),
                Arguments.of(HttpMethod.POST, "/api/venues/1/sectors/2/seats"),
                Arguments.of(HttpMethod.DELETE, "/api/venues/1/sectors/2/seats/3"));
    }

    static Stream<Arguments> participantWrites() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/participants"),
                Arguments.of(HttpMethod.PUT, "/api/participants/1"),
                Arguments.of(HttpMethod.DELETE, "/api/participants/1"));
    }

    @ParameterizedTest
    @MethodSource({"venueWrites", "participantWrites"})
    void write_withoutIdentity_returns401(HttpMethod method, String path) throws Exception {
        mockMvc.perform(write(method, path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
        verifyNoInteractions(venueService, sectorService, participantService);
    }

    @ParameterizedTest
    @MethodSource("venueWrites")
    void venueWrite_organizer_returns403(HttpMethod method, String path) throws Exception {
        mockMvc.perform(write(method, path).header("X-User-Id", 2)
                        .header("X-User-Permissions", "EVENT_MANAGE,PARTICIPANT_MANAGE").header("X-Organization-Id", 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El usuario 2 no tiene el permiso VENUE_MANAGE"));
        verifyNoInteractions(venueService, sectorService);
    }

    @ParameterizedTest
    @MethodSource("participantWrites")
    void participantWrite_client_returns403(HttpMethod method, String path) throws Exception {
        mockMvc.perform(write(method, path).header("X-User-Id", 1).header("X-User-Permissions", "EVENT_SEARCH"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("El usuario 1 no tiene el permiso PARTICIPANT_MANAGE"));
        verifyNoInteractions(participantService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/venues", "/api/participants"})
    void create_withThePermission_returns201(String path) throws Exception {
        String body = path.equals("/api/venues") ? VENUE : PARTICIPANT;

        mockMvc.perform(request(HttpMethod.POST, path).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-User-Id", 3).header("X-User-Permissions", "VENUE_MANAGE,PARTICIPANT_MANAGE"))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/participants", "/api/participants/1"})
    void participantWrite_organizer_isAllowed(String path) throws Exception {
        HttpMethod method = path.endsWith("/1") ? HttpMethod.DELETE : HttpMethod.POST;

        mockMvc.perform(request(method, path).contentType(MediaType.APPLICATION_JSON).content(PARTICIPANT)
                        .header("X-User-Id", 2).header("X-User-Permissions", "EVENT_MANAGE,PARTICIPANT_MANAGE"))
                .andExpect(status().is2xxSuccessful());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/venues", "/api/venues/1", "/api/venues/1/sectors", "/api/venues/1/sectors/2/seats",
            "/api/participants", "/api/participants/1"})
    void read_withoutIdentity_isPublic(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder write(HttpMethod method, String path) {
        return request(method, path).contentType(MediaType.APPLICATION_JSON).content("{}");
    }
}
