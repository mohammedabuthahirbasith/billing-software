package com.billing.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.SalesReportResponse;
import com.billing.billing.model.InvoiceStatus;
import com.billing.billing.model.PaymentMethod;
import com.billing.billing.model.Role;
import com.billing.billing.repository.InvoiceItemRepository;
import com.billing.billing.repository.InvoiceRepository;
import com.billing.billing.repository.PaymentMethodBreakdownRow;
import com.billing.billing.repository.SalesSummaryRow;
import com.billing.billing.repository.TopProductRow;
import com.billing.billing.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final long STORE_ID = 3L;
    private static final LocalDate FROM = LocalDate.of(2025, 3, 1);
    private static final LocalDate TO = LocalDate.of(2025, 3, 31);

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceItemRepository invoiceItemRepository;

    @InjectMocks
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        TestEntities.authenticate(1L, "owner@example.com", Role.OWNER, STORE_ID);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void stubSummary() {
        when(invoiceRepository.getSalesSummary(eq(STORE_ID), any(), any())).thenReturn(
                new SalesSummaryRow(new BigDecimal("1000.00"), new BigDecimal("180.00"),
                        new BigDecimal("1180.00"), 4L));
        when(invoiceRepository.countByStore_IdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(STORE_ID), eq(InvoiceStatus.VOID), any(), any())).thenReturn(2L);
    }

    @Test
    void salesReportBoundsRangeOnIndianCalendarDays() {
        stubSummary();
        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);

        reportService.getSalesReport(FROM, TO, 5);

        verify(invoiceRepository).getSalesSummary(eq(STORE_ID), fromCaptor.capture(), toCaptor.capture());
        // Asia/Kolkata is UTC+5:30, so a local day starts at 18:30 UTC the previous day and the
        // exclusive upper bound is the start of the day after 'to'.
        assertThat(fromCaptor.getValue()).isEqualTo(Instant.parse("2025-02-28T18:30:00Z"));
        assertThat(toCaptor.getValue()).isEqualTo(Instant.parse("2025-03-31T18:30:00Z"));
    }

    @Test
    void salesReportAlwaysReportsEveryPaymentMethodEvenWithNoSales() {
        stubSummary();
        when(invoiceRepository.getBreakdownByPaymentMethod(eq(STORE_ID), any(), any())).thenReturn(List.of(
                new PaymentMethodBreakdownRow(PaymentMethod.UPI, new BigDecimal("1180.00"), 4L)));

        SalesReportResponse response = reportService.getSalesReport(FROM, TO, 5);

        assertThat(response.byPaymentMethod())
                .extracting(SalesReportResponse.PaymentMethodBreakdown::paymentMethod)
                .containsExactly("CASH", "CARD", "UPI");
        assertThat(response.byPaymentMethod()).filteredOn(b -> b.paymentMethod().equals("CASH"))
                .singleElement().satisfies(b -> {
                    assertThat(b.revenue()).isEqualByComparingTo("0.00");
                    assertThat(b.invoiceCount()).isZero();
                });
        assertThat(response.totalRevenue()).isEqualByComparingTo("1180.00");
        assertThat(response.invoiceCount()).isEqualTo(4L);
        assertThat(response.voidedCount()).isEqualTo(2L);
    }

    @Test
    void salesReportMapsTopProductRows() {
        stubSummary();
        when(invoiceItemRepository.findTopProducts(eq(STORE_ID), any(), any(), any())).thenReturn(List.of(
                new TopProductRow(10L, "Basmati Rice", "RICE-1", 12L, new BigDecimal("540.00"))));

        SalesReportResponse response = reportService.getSalesReport(FROM, TO, 5);

        assertThat(response.topProducts()).singleElement().satisfies(top -> {
            assertThat(top.productId()).isEqualTo(10L);
            assertThat(top.sku()).isEqualTo("RICE-1");
            assertThat(top.quantitySold()).isEqualTo(12L);
            assertThat(top.revenue()).isEqualByComparingTo("540.00");
        });
    }

    @Test
    void salesReportClampsTopNIntoOneToHundred() {
        stubSummary();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        reportService.getSalesReport(FROM, TO, 0);
        reportService.getSalesReport(FROM, TO, 500);

        verify(invoiceItemRepository, times(2))
                .findTopProducts(eq(STORE_ID), any(), any(), pageableCaptor.capture());
        assertThat(pageableCaptor.getAllValues()).extracting(Pageable::getPageSize).containsExactly(1, 100);
    }

    @Test
    void salesReportRejectsInvertedRange() {
        assertThatThrownBy(() -> reportService.getSalesReport(TO, FROM, 5))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("'from' must not be after 'to'")
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void salesReportRejectsRangeLongerThanAYear() {
        assertThatThrownBy(() -> reportService.getSalesReport(FROM, FROM.plusDays(367), 5))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Date range cannot exceed 366 days");
    }

    @Test
    void salesReportAcceptsRangeExactlyAtTheLimit() {
        stubSummary();

        assertThat(reportService.getSalesReport(FROM, FROM.plusDays(366), 5)).isNotNull();
    }
}
