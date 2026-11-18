package com.nextcart.backend;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nextcart.backend.admin.AdminController;
import com.nextcart.backend.auth.AuthController;
import com.nextcart.backend.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminApiSmokeTests {

    @Test
    void dashboardRejectsAnonymousRequest() throws Exception {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(jdbcTemplate)).build();

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardRejectsCustomerRequest() throws Exception {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), ArgumentMatchers.<ResultSetExtractor<String>>any(), anyLong()))
                .thenReturn("CUSTOMER");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 42L);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(jdbcTemplate)).build();

        mockMvc.perform(get("/api/admin/dashboard").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        AuthService authService = org.mockito.Mockito.mock(AuthService.class);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 42L);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(session.isInvalid());
    }
}
