package com.billing.billing.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.billing.billing.model.Role;
import com.billing.billing.repository.StoreRepository;
import com.billing.billing.support.ApiTokens;
import com.billing.billing.support.TestEntities;
import com.billing.billing.support.WebSecuritySlice;

@WebMvcTest(MeController.class)
@WebSecuritySlice
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StoreRepository storeRepository;

    @Test
    void meDescribesTheTokenHolderAndResolvesTheStoreNameFromTheDatabase() throws Exception {
        when(storeRepository.findById(ApiTokens.STORE_ID))
                .thenReturn(Optional.of(TestEntities.store(ApiTokens.STORE_ID, "Kirana Mart")));

        mockMvc.perform(get("/api/me").header("Authorization", ApiTokens.bearer(Role.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.email").value("cashier@example.com"))
                .andExpect(jsonPath("$.role").value("CASHIER"))
                .andExpect(jsonPath("$.storeName").value("Kirana Mart"));
    }

    @Test
    void meFailsWhenTheTokensStoreNoLongerExists() throws Exception {
        when(storeRepository.findById(ApiTokens.STORE_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/me").header("Authorization", ApiTokens.bearer(Role.OWNER)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
