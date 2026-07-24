package com.billing.billing.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.SalesReportResponse;
import com.billing.billing.model.InvoiceStatus;
import com.billing.billing.model.PaymentMethod;
import com.billing.billing.repository.InvoiceItemRepository;
import com.billing.billing.repository.InvoiceRepository;
import com.billing.billing.repository.PaymentMethodBreakdownRow;
import com.billing.billing.security.CurrentUser;

@Service
public class ReportService {

    // India-specific GST billing app — "today"/"this month" should mean the actual Indian retail
    // day, not a UTC-shifted one. Fixed UTC+5:30 offset, no DST to worry about.
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final int MAX_RANGE_DAYS = 366;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;

    public ReportService(InvoiceRepository invoiceRepository, InvoiceItemRepository invoiceItemRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
    }

    @Transactional(readOnly = true)
    public SalesReportResponse getSalesReport(LocalDate from, LocalDate to, int topN) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range cannot exceed " + MAX_RANGE_DAYS + " days");
        }
        int clampedTopN = Math.min(Math.max(topN, 1), 100);

        Long storeId = CurrentUser.get().storeId();
        Instant fromInstant = from.atStartOfDay(ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZONE).toInstant();

        var summary = invoiceRepository.getSalesSummary(storeId, fromInstant, toExclusive);
        long voidedCount = invoiceRepository.countByStore_IdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                storeId, InvoiceStatus.VOID, fromInstant, toExclusive);

        // GROUP BY omits a payment method entirely if it had zero invoices in range — fill the gaps
        // so CASH/CARD/UPI always appear, rather than silently disappearing from the response.
        Map<PaymentMethod, PaymentMethodBreakdownRow> byMethod = invoiceRepository
                .getBreakdownByPaymentMethod(storeId, fromInstant, toExclusive).stream()
                .collect(Collectors.toMap(PaymentMethodBreakdownRow::paymentMethod, row -> row));
        List<SalesReportResponse.PaymentMethodBreakdown> breakdown = Arrays.stream(PaymentMethod.values())
                .map(pm -> {
                    PaymentMethodBreakdownRow row = byMethod.get(pm);
                    return new SalesReportResponse.PaymentMethodBreakdown(
                            pm.name(),
                            row != null ? row.revenue() : BigDecimal.ZERO.setScale(2),
                            row != null ? row.invoiceCount() : 0L);
                })
                .toList();

        List<SalesReportResponse.TopProduct> topProducts = invoiceItemRepository
                .findTopProducts(storeId, fromInstant, toExclusive, PageRequest.of(0, clampedTopN)).stream()
                .map(row -> new SalesReportResponse.TopProduct(
                        row.productId(), row.productName(), row.sku(), row.quantitySold(), row.revenue()))
                .toList();

        return new SalesReportResponse(from, to, summary.subtotal(), summary.taxAmount(), summary.totalAmount(),
                summary.invoiceCount(), voidedCount, breakdown, topProducts);
    }
}
