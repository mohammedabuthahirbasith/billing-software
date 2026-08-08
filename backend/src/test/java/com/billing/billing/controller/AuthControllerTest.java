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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.AuthResponse;
import com.billing.billing.dto.UserResponse;
import com.billing.billing.model.Role;
import com.billing.billing.service.AuthService;
import com.billing.billing.support.WebSecuritySlice;

@WebMvcTest(AuthController.class)
@WebSecuritySlice
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerIsPublicAndReturns201() throws Exception {
        when(authService.register(any()))
                .thenReturn(new UserResponse(11L, "owner@example.com", Role.OWNER, "Kirana Mart"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@example.com","password":"password123","storeName":"Kirana Mart"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.storeName").value("Kirana Mart"));
    }

    @Test
    void registerRejectsAShortPasswordAndAMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","storeName":"Kirana Mart"}
                                """))
                .andExpect(status().isBadRequest());
        verify(authService, never()).register(any());
    }

    @Test
    void loginIsPublicAndReturnsAToken() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("jwt-token", "owner@example.com", Role.OWNER));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void loginPropagatesTheServicesUnauthorizedStatus() throws Exception {
        when(authService.login(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"owner@example.com","password":"nope"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsABlankPayload() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":""}
                                """))
                .andExpect(status().isBadRequest());
        verify(authService, never()).login(any());
    }
}
