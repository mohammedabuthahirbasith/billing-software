package com.billing.billing.controller;

import static org.mockito.ArgumentMatchers.any;
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

import com.billing.billing.dto.InvoiceResponse;
import com.billing.billing.dto.InvoiceSummaryResponse;
import com.billing.billing.model.Role;
import com.billing.billing.service.InvoiceService;
import com.billing.billing.support.ApiTokens;
import com.billing.billing.support.WebSecuritySlice;

@WebMvcTest(InvoiceController.class)
@WebSecuritySlice
class InvoiceControllerTest {

    private static final String VALID_BODY = """
            {"customerName":"Ravi","customerPhone":"9876543210","paymentMethod":"CASH",
             "items":[{"productId":10,"quantity":2}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvoiceService invoiceService;

    private static InvoiceResponse invoiceResponse() {
        return new InvoiceResponse(42L, "INV-000042", "Ravi", "9876543210", new BigDecimal("100.00"),
                new BigDecimal("18.00"), new BigDecimal("118.00"), "COMPLETED", "CASH",
                Instant.parse("2025-03-01T00:00:00Z"), null, List.of());
    }

    @Test
    void createReturns201ForAnyAuthenticatedRole() throws Exception {
        when(invoiceService.create(any())).thenReturn(invoiceResponse());

        mockMvc.perform(post("/api/invoices").header("Authorization", ApiTokens.bearer(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber").value("INV-000042"))
                .andExpect(jsonPath("$.totalAmount").value(118.00));
    }

    @Test
    void createRejectsAnEmptyCart() throws Exception {
        mockMvc.perform(post("/api/invoices").header("Authorization", ApiTokens.bearer(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CASH","items":[]}
                                """))
                .andExpect(status().isBadRequest());
        verify(invoiceService, never()).create(any());
    }

    @Test
    void createRejectsANonPositiveLineQuantity() throws Exception {
        mockMvc.perform(post("/api/invoices").header("Authorization", ApiTokens.bearer(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentMethod":"CASH","items":[{"productId":10,"quantity":0}]}
                                """))
                .andExpect(status().isBadRequest());
        verify(invoiceService, never()).create(any());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/invoices").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsInvoiceSummaries() throws Exception {
        when(invoiceService.getAll()).thenReturn(List.of(new InvoiceSummaryResponse(42L, "INV-000042", "Ravi",
                "9876543210", new BigDecimal("100.00"), new BigDecimal("18.00"), new BigDecimal("118.00"),
                "COMPLETED", "CASH", Instant.parse("2025-03-01T00:00:00Z"), null)));

        mockMvc.perform(get("/api/invoices").header("Authorization", ApiTokens.bearer(Role.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void getByIdPropagatesTheServicesNotFoundStatus() throws Exception {
        when(invoiceService.getById(9L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: 9"));

        mockMvc.perform(get("/api/invoices/9").header("Authorization", ApiTokens.bearer(Role.CASHIER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void voidIsOwnerOnly() throws Exception {
        mockMvc.perform(post("/api/invoices/42/void").header("Authorization", ApiTokens.bearer(Role.CASHIER)))
                .andExpect(status().isForbidden());
        verify(invoiceService, never()).voidInvoice(any());
    }

    @Test
    void voidReturnsTheVoidedInvoiceForAnOwner() throws Exception {
        when(invoiceService.voidInvoice(42L)).thenReturn(invoiceResponse());

        mockMvc.perform(post("/api/invoices/42/void").header("Authorization", ApiTokens.bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }
}
