package org.group10.event.controller;

import org.group10.event.client.AuthClient;
import org.group10.event.client.dto.UserPermissionsResponse;
import org.group10.event.service.EventSearchService;
import org.group10.event.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

import java.util.Set;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerTest {

    private static final String VALID_EVENT = """
            {"name": "Los Serranos en vivo", "description": "Presentación del nuevo disco", "type": "CONCERT",
             "venueId": 1, "dateTime": "2099-12-12T21:00:00", "highDemand": true,
             "saleStart": "2099-11-20T10:00:00", "maxTicketsPerCustomer": 4}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private AuthClient authClient;

    @MockitoBean
    private EventSearchService eventSearchService;

    @Test
    void create_userWithoutEventManage_returns403AndDoesNotCreate() throws Exception {
        when(authClient.getPermissions(2L)).thenReturn(
                new UserPermissionsResponse(2L, "CLIENT", Set.of("EVENT_SEARCH", "TICKET_PURCHASE")));

        mockMvc.perform(createRequest(VALID_EVENT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.message").value("El usuario 2 no tiene el permiso EVENT_MANAGE"));
        verifyNoInteractions(eventService);
    }

    @Test
    void create_withoutPermissionAndInvalidBody_returns403BeforeValidating() throws Exception {
        when(authClient.getPermissions(2L)).thenReturn(
                new UserPermissionsResponse(2L, "CLIENT", Set.of("EVENT_SEARCH")));
        String body = VALID_EVENT.replace("\"Los Serranos en vivo\"", "\"  \"");

        mockMvc.perform(createRequest(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void create_authServiceDownWhileCheckingPermissions_returns503() throws Exception {
        when(authClient.getPermissions(2L)).thenThrow(new ResourceAccessException("Connection refused"));

        mockMvc.perform(createRequest(VALID_EVENT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("service_unavailable"));
        verifyNoInteractions(eventService);
    }

    private static MockHttpServletRequestBuilder createRequest(String body) {
        return post("/api/events")
                .header("X-Organization-Id", 1)
                .header("X-User-Id", 2)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
