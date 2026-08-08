package com.billing.billing.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.ReturnRequest;
import com.billing.billing.dto.ReturnResponse;
import com.billing.billing.model.Invoice;
import com.billing.billing.model.InvoiceItem;
import com.billing.billing.model.InvoiceReturn;
import com.billing.billing.model.InvoiceReturnItem;
import com.billing.billing.model.InvoiceStatus;
import com.billing.billing.repository.InvoiceItemRepository;
import com.billing.billing.repository.InvoiceReturnItemRepository;
import com.billing.billing.repository.InvoiceReturnRepository;
import com.billing.billing.util.Money;

@Service
public class ReturnService {

    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceReturnRepository invoiceReturnRepository;
    private final InvoiceReturnItemRepository invoiceReturnItemRepository;
    private final StoreScopedLookup lookup;
    private final StockAdjuster stockAdjuster;

    public ReturnService(InvoiceItemRepository invoiceItemRepository,
                          InvoiceReturnRepository invoiceReturnRepository,
                          InvoiceReturnItemRepository invoiceReturnItemRepository,
                          StoreScopedLookup lookup, StockAdjuster stockAdjuster) {
        this.invoiceItemRepository = invoiceItemRepository;
        this.invoiceReturnRepository = invoiceReturnRepository;
        this.invoiceReturnItemRepository = invoiceReturnItemRepository;
        this.lookup = lookup;
        this.stockAdjuster = stockAdjuster;
    }

    @Transactional
    public ReturnResponse createReturn(Long invoiceId, ReturnRequest request) {
        Invoice invoice = lookup.invoice(invoiceId);

        if (invoice.getStatus() != InvoiceStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot return items from a voided invoice");
        }

        List<InvoiceReturnItem> returnItems = new ArrayList<>();
        BigDecimal refundSubtotal = Money.zero();
        BigDecimal refundTax = Money.zero();

        for (ReturnRequest.ReturnItemRequest lineRequest : request.items()) {
            InvoiceItem invoiceItem = invoiceItemRepository.findByIdAndInvoice_Id(lineRequest.invoiceItemId(), invoiceId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Invoice item " + lineRequest.invoiceItemId() + " does not belong to invoice " + invoiceId));

            int alreadyReturned = invoiceReturnItemRepository.sumReturnedQuantityByInvoiceItemId(invoiceItem.getId());
            int remaining = invoiceItem.getQuantity() - alreadyReturned;
            if (lineRequest.quantity() > remaining) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cannot return " + lineRequest.quantity() + " of " + invoiceItem.getSku()
                                + ": only " + remaining + " remaining returnable");
            }

            // Prorate off the ORIGINAL line's unit price/GST rate, through the same Money helpers as
            // invoice creation, so a return's totals are internally consistent the same way.
            BigDecimal lineSubtotalRefund = Money.lineSubtotal(invoiceItem.getUnitPrice(), lineRequest.quantity());
            BigDecimal lineTaxRefund = Money.gst(lineSubtotalRefund, invoiceItem.getGstRate());
            BigDecimal lineTotalRefund = lineSubtotalRefund.add(lineTaxRefund);

            returnItems.add(new InvoiceReturnItem(invoiceItem, lineRequest.quantity(),
                    lineSubtotalRefund, lineTaxRefund, lineTotalRefund));

            refundSubtotal = refundSubtotal.add(lineSubtotalRefund);
            refundTax = refundTax.add(lineTaxRefund);

            stockAdjuster.adjust(invoiceItem.getProduct(), lineRequest.quantity());
        }

        stockAdjuster.flushOrConflict("Stock changed concurrently, please retry");

        InvoiceReturn invoiceReturn = new InvoiceReturn(lookup.currentStoreReference(), invoice, refundSubtotal,
                refundTax, refundSubtotal.add(refundTax));
        returnItems.forEach(invoiceReturn::addItem);

        return ReturnResponse.from(invoiceReturnRepository.save(invoiceReturn));
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> getReturnsForInvoice(Long invoiceId) {
        lookup.invoice(invoiceId);   // 404s before exposing another store's return history

        return invoiceReturnRepository.findAllByInvoice_IdOrderByCreatedAtAsc(invoiceId).stream()
                .map(ReturnResponse::from)
                .toList();
    }
}
