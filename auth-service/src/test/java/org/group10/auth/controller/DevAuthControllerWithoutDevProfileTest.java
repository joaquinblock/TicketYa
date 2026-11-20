package org.group10.auth.controller;

import org.group10.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DevAuthController.class)
class DevAuthControllerWithoutDevProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void devLogin_withoutDevProfile_doesNotExist() throws Exception {
        mockMvc.perform(post("/api/auth/dev-login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"userId": 1}"""))
                .andExpect(status().isNotFound());
        verifyNoInteractions(authService);
    }
}
