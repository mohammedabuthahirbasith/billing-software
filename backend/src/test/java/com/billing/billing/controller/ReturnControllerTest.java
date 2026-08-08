package com.billing.billing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.ReturnResponse;
import com.billing.billing.model.Role;
import com.billing.billing.service.ReturnService;
import com.billing.billing.support.ApiTokens;
import com.billing.billing.support.WebSecuritySlice;

@WebMvcTest(ReturnController.class)
@WebSecuritySlice
class ReturnControllerTest {

    private static final String VALID_BODY = """
            {"items":[{"invoiceItemId":77,"quantity":2}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReturnService returnService;

    private static ReturnResponse returnResponse() {
        return new ReturnResponse(90L, 42L, new BigDecimal("199.98"), new BigDecimal("36.00"),
                new BigDecimal("235.98"), Instant.parse("2025-03-01T00:00:00Z"),
                List.of(new ReturnResponse.ReturnItemResponse(77L, "Basmati Rice", "RICE-1", 2,
                        new BigDecimal("199.98"), new BigDecimal("36.00"), new BigDecimal("235.98"))));
    }

    @Test
    void createIsOwnerOnly() throws Exception {
        mockMvc.perform(post("/api/invoices/42/returns").header("Authorization", ApiTokens.bearer(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verify(returnService, never()).createReturn(any(), any());
    }

    @Test
    void createReturns201ForAnOwner() throws Exception {
        when(returnService.createReturn(eq(42L), any())).thenReturn(returnResponse());

        mockMvc.perform(post("/api/invoices/42/returns").header("Authorization", ApiTokens.bearer(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundTotal").value(235.98))
                .andExpect(jsonPath("$.items[0].quantityReturned").value(2));
    }

    @Test
    void createRejectsAnEmptyItemList() throws Exception {
        mockMvc.perform(post("/api/invoices/42/returns").header("Authorization", ApiTokens.bearer(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
        verify(returnService, never()).createReturn(any(), any());
    }

    @Test
    void createPropagatesTheServicesConflictStatus() throws Exception {
        when(returnService.createReturn(eq(42L), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "only 1 remaining returnable"));

        mockMvc.perform(post("/api/invoices/42/returns").header("Authorization", ApiTokens.bearer(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void listIsAvailableToAnyAuthenticatedRole() throws Exception {
        when(returnService.getReturnsForInvoice(42L)).thenReturn(List.of(returnResponse()));

        mockMvc.perform(get("/api/invoices/42/returns").header("Authorization", ApiTokens.bearer(Role.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceId").value(42));
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/invoices/42/returns")).andExpect(status().isUnauthorized());
    }
}
