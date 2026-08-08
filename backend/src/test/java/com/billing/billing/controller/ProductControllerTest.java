package com.billing.billing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.billing.billing.dto.ProductResponse;
import com.billing.billing.model.Role;
import com.billing.billing.service.ProductService;
import com.billing.billing.support.ApiTokens;
import com.billing.billing.support.WebSecuritySlice;

@WebMvcTest(ProductController.class)
@WebSecuritySlice
class ProductControllerTest {

    private static final String VALID_BODY = """
            {"name":"Basmati Rice","sku":"RICE-1","description":"5kg","price":450.00,
             "gstRate":5.00,"hsnCode":"1006","stockQuantity":20}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private static ProductResponse response() {
        return new ProductResponse(1L, "Basmati Rice", "RICE-1", "5kg", new BigDecimal("450.00"),
                new BigDecimal("5.00"), "1006", 20, Instant.parse("2025-03-01T00:00:00Z"), null);
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
        verify(productService, never()).getAll();
    }

    @Test
    void listReturnsProductsForAnyAuthenticatedRole() throws Exception {
        when(productService.getAll()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/products").header("Authorization", ApiTokens.bearer(Role.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("RICE-1"))
                .andExpect(jsonPath("$[0].price").value(450.00));
    }

    @Test
    void getBySkuServesTheBarcodeScanFlow() throws Exception {
        when(productService.getBySku("RICE-1")).thenReturn(response());

        mockMvc.perform(get("/api/products/by-sku/RICE-1").header("Authorization", ApiTokens.bearer(Role.CASHIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getByIdPropagatesTheServicesNotFoundStatus() throws Exception {
        when(productService.getById(9L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: 9"));

        mockMvc.perform(get("/api/products/9").header("Authorization", ApiTokens.bearer(Role.CASHIER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createIsOwnerOnly() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", ApiTokens.bearer(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verify(productService, never()).create(any());
    }

    @Test
    void createReturns201ForAnOwner() throws Exception {
        when(productService.create(any())).thenReturn(response());

        mockMvc.perform(post("/api/products").header("Authorization", ApiTokens.bearer(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("RICE-1"));
    }

    @Test
    void createRejectsAnInvalidPayloadBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/api/products").header("Authorization", ApiTokens.bearer(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","sku":"RICE-1","price":-1,"gstRate":5.00,"stockQuantity":-5}
                                """))
                .andExpect(status().isBadRequest());
        verify(productService, never()).create(any());
    }

    @Test
    void updateIsOwnerOnly() throws Exception {
        mockMvc.perform(put("/api/products/1").header("Authorization", ApiTokens.bearer(Role.CASHIER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateReturnsTheUpdatedProductForAnOwner() throws Exception {
        when(productService.update(eq(1L), any())).thenReturn(response());

        mockMvc.perform(put("/api/products/1").header("Authorization", ApiTokens.bearer(Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(20));
    }

    @Test
    void deleteIsOwnerOnlyAndReturns204() throws Exception {
        mockMvc.perform(delete("/api/products/1").header("Authorization", ApiTokens.bearer(Role.CASHIER)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products/1").header("Authorization", ApiTokens.bearer(Role.OWNER)))
                .andExpect(status().isNoContent());
        verify(productService).delete(1L);
    }
}
