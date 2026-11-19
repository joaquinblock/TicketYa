package org.group10.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CallerSwaggerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocs_doNotShowTheCallerAsAParameterToFillIn() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/things/suspend']").exists())
                .andExpect(jsonPath("$.paths['/api/things/{id}']").exists())
                .andExpect(content().string(not(containsString("\"caller\""))))
                .andExpect(content().string(not(containsString("\"Caller\""))));
    }
}
