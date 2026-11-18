package org.group10.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FakeController.class)
class PermissionInterceptorTest {

    private static final String VALID_BODY = """
            {"name": "Recital"}""";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endpointWithoutAnnotation_withoutIdentity_isPublic() throws Exception {
        mockMvc.perform(get("/api/things"))
                .andExpect(status().isOk())
                .andExpect(content().string("public"));
    }

    @Test
    void protectedEndpoint_withoutUserHeader_returns401() throws Exception {
        mockMvc.perform(create(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "abc", "1.5"})
    void protectedEndpoint_userHeaderIsNotAnId_returns401(String userId) throws Exception {
        mockMvc.perform(create(VALID_BODY).header(GatewayHeaders.USER_ID, userId)
                        .header(GatewayHeaders.PERMISSIONS, "EVENT_MANAGE"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void protectedEndpoint_userWithoutThePermission_returns403NamingIt() throws Exception {
        mockMvc.perform(create(VALID_BODY).header(GatewayHeaders.USER_ID, "1")
                        .header(GatewayHeaders.PERMISSIONS, "EVENT_SEARCH,TICKET_PURCHASE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"))
                .andExpect(jsonPath("$.message").value("El usuario 1 no tiene el permiso EVENT_MANAGE"));
    }

    @Test
    void protectedEndpoint_userWithoutPermissionsHeader_returns403() throws Exception {
        mockMvc.perform(create(VALID_BODY).header(GatewayHeaders.USER_ID, "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\": \"\"}", "{\"name\": ", "no es json"})
    void protectedEndpoint_withoutPermissionAndInvalidBody_returns403BeforeReadingTheBody(String body)
            throws Exception {
        mockMvc.perform(create(body).header(GatewayHeaders.USER_ID, "1")
                        .header(GatewayHeaders.PERMISSIONS, "EVENT_SEARCH"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void protectedEndpoint_withoutIdentityAndInvalidBody_returns401BeforeReadingTheBody() throws Exception {
        mockMvc.perform(create("no es json"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void protectedEndpoint_withThePermissionAndInvalidBody_returns400() throws Exception {
        mockMvc.perform(create("{\"name\": \"\"}").header(GatewayHeaders.USER_ID, "2")
                        .header(GatewayHeaders.PERMISSIONS, "EVENT_MANAGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation_error"));
    }

    @Test
    void protectedEndpoint_withThePermission_reachesTheController() throws Exception {
        mockMvc.perform(create(VALID_BODY).header(GatewayHeaders.USER_ID, "2")
                        .header(GatewayHeaders.PERMISSIONS, "EVENT_SEARCH,EVENT_MANAGE"))
                .andExpect(status().isOk())
                .andExpect(content().string("created"));
    }

    @Test
    void protectedEndpoint_withAnyOfThePermissions_receivesTheCallerFromTheHeaders() throws Exception {
        mockMvc.perform(post("/api/things/suspend")
                        .header(GatewayHeaders.USER_ID, "2")
                        .header(GatewayHeaders.ROLE, "ORGANIZER")
                        .header(GatewayHeaders.PERMISSIONS, "EVENT_MANAGE, REPORT_VIEW")
                        .header(GatewayHeaders.ORGANIZATION_ID, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.role").value("ORGANIZER"))
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("EVENT_MANAGE", "REPORT_VIEW")))
                .andExpect(jsonPath("$.organizationId").value(1));
    }

    @Test
    void protectedEndpoint_withoutOrganizationHeader_callerHasNoOrganization() throws Exception {
        mockMvc.perform(post("/api/things/suspend").header(GatewayHeaders.USER_ID, "3")
                        .header(GatewayHeaders.ROLE, "ADMIN")
                        .header(GatewayHeaders.PERMISSIONS, "EVENT_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value("none"));
    }

    @Test
    void protectedEndpoint_organizationHeaderIsNotAnId_returns401() throws Exception {
        mockMvc.perform(post("/api/things/suspend").header(GatewayHeaders.USER_ID, "2")
                        .header(GatewayHeaders.PERMISSIONS, "EVENT_MANAGE")
                        .header(GatewayHeaders.ORGANIZATION_ID, "abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void internalRoute_isNotCheckedByTheInterceptor() throws Exception {
        mockMvc.perform(post("/internal/things"))
                .andExpect(status().isOk())
                .andExpect(content().string("internal"));
    }

    private static MockHttpServletRequestBuilder create(String body) {
        return post("/api/things").contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
