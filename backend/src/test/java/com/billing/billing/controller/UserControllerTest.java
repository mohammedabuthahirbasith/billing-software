package com.billing.billing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.billing.billing.dto.UserResponse;
import com.billing.billing.model.Role;
import com.billing.billing.service.AuthService;
import com.billing.billing.support.ApiTokens;
import com.billing.billing.support.WebSecuritySlice;

@WebMvcTest(UserController.class)
@WebSecuritySlice
class UserControllerTest {

    private static final String VALID_BODY = """
            {"email":"cashier@example.com","password":"password123","role":"CASHIER"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void creatingStaffIsOwnerOnly() throws Exception {
        mockMvc.perform(post("/api/users").header("Authorization", ApiTokens.bearer(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verify(authService, never()).createStaffUser(any());
    }

    @Test
    void ownerCreatesStaffAndGets201() throws Exception {
        when(authService.createStaffUser(any()))
                .thenReturn(new UserResponse(12L, "cashier@example.com", Role.CASHIER, "Kirana Mart"));

        mockMvc.perform(post("/api/users").header("Authorization", ApiTokens.bearer(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("cashier@example.com"))
                .andExpect(jsonPath("$.role").value("CASHIER"));
    }

    @Test
    void creatingStaffRejectsAMissingRole() throws Exception {
        mockMvc.perform(post("/api/users").header("Authorization", ApiTokens.bearer(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cashier@example.com","password":"password123"}
                                """))
                .andExpect(status().isBadRequest());
        verify(authService, never()).createStaffUser(any());
    }

    @Test
    void creatingStaffRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }
}
