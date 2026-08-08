package com.billing.billing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.billing.billing.dto.SalesReportResponse;
import com.billing.billing.model.Role;
import com.billing.billing.service.ReportService;
import com.billing.billing.support.ApiTokens;
import com.billing.billing.support.WebSecuritySlice;

@WebMvcTest(ReportController.class)
@WebSecuritySlice
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportService reportService;

    private static SalesReportResponse report() {
        return new SalesReportResponse(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31),
                new BigDecimal("1000.00"), new BigDecimal("180.00"), new BigDecimal("1180.00"), 4L, 2L,
                List.of(new SalesReportResponse.PaymentMethodBreakdown("CASH", new BigDecimal("1180.00"), 4L)),
                List.of(new SalesReportResponse.TopProduct(10L, "Basmati Rice", "RICE-1", 12L,
                        new BigDecimal("540.00"))));
    }

    @Test
    void salesReportIsOwnerOnly() throws Exception {
        mockMvc.perform(get("/api/reports/sales").param("from", "2025-03-01").param("to", "2025-03-31")
                        .header("Authorization", ApiTokens.bearer(Role.CASHIER)))
                .andExpect(status().isForbidden());
        verify(reportService, never()).getSalesReport(any(), any(), anyInt());
    }

    @Test
    void salesReportDefaultsTopNToTen() throws Exception {
        when(reportService.getSalesReport(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31), 10))
                .thenReturn(report());

        mockMvc.perform(get("/api/reports/sales").param("from", "2025-03-01").param("to", "2025-03-31")
                        .header("Authorization", ApiTokens.bearer(Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(1180.00))
                .andExpect(jsonPath("$.topProducts[0].sku").value("RICE-1"));
    }

    @Test
    void salesReportPassesAnExplicitTopNThrough() throws Exception {
        when(reportService.getSalesReport(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31), 3))
                .thenReturn(report());

        mockMvc.perform(get("/api/reports/sales").param("from", "2025-03-01").param("to", "2025-03-31")
                        .param("topN", "3").header("Authorization", ApiTokens.bearer(Role.OWNER)))
                .andExpect(status().isOk());
        verify(reportService).getSalesReport(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31), 3);
    }

    @Test
    void salesReportRejectsAMissingDateRange() throws Exception {
        mockMvc.perform(get("/api/reports/sales").header("Authorization", ApiTokens.bearer(Role.OWNER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salesReportRejectsAnUnparseableDate() throws Exception {
        mockMvc.perform(get("/api/reports/sales").param("from", "01-03-2025").param("to", "2025-03-31")
                        .header("Authorization", ApiTokens.bearer(Role.OWNER)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void salesReportRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/reports/sales").param("from", "2025-03-01").param("to", "2025-03-31"))
                .andExpect(status().isUnauthorized());
    }
}
