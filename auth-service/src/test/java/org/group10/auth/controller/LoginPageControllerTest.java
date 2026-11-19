package org.group10.auth.controller;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La página mínima para entrar con Google sin front: solo con el perfil {@code dev}.
 */
class LoginPageControllerTest {

    @Nested
    @WebMvcTest(value = LoginPageController.class,
            properties = "ticketya.google.client-id=123-abc.apps.googleusercontent.com")
    @ActiveProfiles("dev")
    class WithClientId {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void loginPage_showsTheGoogleButtonWithTheClientId() throws Exception {
            mockMvc.perform(get("/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("https://accounts.google.com/gsi/client")))
                    .andExpect(content().string(containsString("data-client_id=\"123-abc.apps.googleusercontent.com\"")))
                    .andExpect(content().string(containsString("/api/auth/google")))
                    .andExpect(content().string(not(containsString("{{CLIENT_ID}}"))));
        }
    }

    @Nested
    @WebMvcTest(value = LoginPageController.class, properties = "ticketya.google.client-id=")
    @ActiveProfiles("dev")
    class WithoutClientId {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void loginPage_withoutClientId_explainsWhatIsMissingAndHasNoButton() throws Exception {
            mockMvc.perform(get("/login"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("GOOGLE_CLIENT_ID")))
                    .andExpect(content().string(not(containsString("accounts.google.com/gsi/client"))));
        }
    }

    @Nested
    @WebMvcTest(value = LoginPageController.class,
            properties = "ticketya.google.client-id=123-abc.apps.googleusercontent.com")
    class WithoutDevProfile {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void loginPage_withoutDevProfile_doesNotExist() throws Exception {
            mockMvc.perform(get("/login")).andExpect(status().isNotFound());
        }
    }
}
