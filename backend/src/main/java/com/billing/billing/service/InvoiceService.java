package com.billing.billing.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.InvoiceItemRequest;
import com.billing.billing.dto.InvoiceRequest;
import com.billing.billing.dto.InvoiceResponse;
import com.billing.billing.dto.InvoiceSummaryResponse;
import com.billing.billing.model.Invoice;
import com.billing.billing.model.InvoiceItem;
import com.billing.billing.model.InvoiceStatus;
import com.billing.billing.model.Product;
import com.billing.billing.repository.InvoiceRepository;
import com.billing.billing.repository.InvoiceReturnRepository;
import com.billing.billing.util.Money;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceReturnRepository invoiceReturnRepository;
    private final StoreScopedLookup lookup;
    private final StockAdjuster stockAdjuster;

    public InvoiceService(InvoiceRepository invoiceRepository, InvoiceReturnRepository invoiceReturnRepository,
                           StoreScopedLookup lookup, StockAdjuster stockAdjuster) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceReturnRepository = invoiceReturnRepository;
        this.lookup = lookup;
        this.stockAdjuster = stockAdjuster;
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        List<InvoiceItem> items = new ArrayList<>();
        BigDecimal subtotal = Money.zero();
        BigDecimal taxAmount = Money.zero();

        // Duplicate productId across lines is allowed, not merged: each fetch returns the same
        // managed entity within this persistence context, so cumulative stock decrement is still correct.
        for (InvoiceItemRequest itemRequest : request.items()) {
            // Store-scoped: this is what stops a request from referencing, and thus buying and
            // decrementing stock on, another store's product (covers both the barcode-scan-by-SKU
            // and manual-picker-by-id frontend flows, which both resolve to this one call site).
            Product product = lookup.product(itemRequest.productId());

            int quantity = itemRequest.quantity();
            if (product.getStockQuantity() < quantity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient stock for SKU " + product.getSku() + ": requested " + quantity
                                + ", available " + product.getStockQuantity());
            }
            stockAdjuster.adjust(product, -quantity);

            BigDecimal lineSubtotal = Money.lineSubtotal(product.getPrice(), quantity);
            BigDecimal lineTax = Money.gst(lineSubtotal, product.getGstRate());
            BigDecimal lineTotal = lineSubtotal.add(lineTax);

            items.add(new InvoiceItem(product, product.getName(), product.getSku(), product.getHsnCode(),
                    product.getPrice(), product.getGstRate(), quantity, lineSubtotal, lineTax, lineTotal));

            subtotal = subtotal.add(lineSubtotal);
            taxAmount = taxAmount.add(lineTax);
        }

        stockAdjuster.flushOrConflict("Stock changed concurrently on one or more items, please retry");

        Invoice invoice = new Invoice(request.customerName(), request.customerPhone(),
                subtotal, taxAmount, subtotal.add(taxAmount), request.paymentMethod(), lookup.currentStoreReference());
        items.forEach(invoice::addItem);

        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    public List<InvoiceSummaryResponse> getAll() {
        return invoiceRepository.findAllByStore_Id(lookup.currentStoreId(), Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(InvoiceSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(Long id) {
        return InvoiceResponse.from(lookup.invoice(id));
    }

    @Transactional
    public InvoiceResponse voidInvoice(Long id) {
        Invoice invoice = lookup.invoice(id);

        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice already voided: " + id);
        }

        // Voiding restocks each item's FULL original quantity below — if a partial return already
        // restocked some units, voiding on top of that would double-restock them. Returns and void
        // are made mutually exclusive on a given invoice rather than reconciling the two restock
        // paths against each other.
        if (invoiceReturnRepository.existsByInvoice_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot void an invoice with existing returns: " + id);
        }

        // Restore stock for every line. Two concurrent voids of the same invoice collide on Product's
        // @Version during the flush below (whichever commits second rolls back entirely, including the
        // status flip) — so double-restoration is already prevented transitively, with no @Version
        // needed on Invoice itself.
        for (InvoiceItem item : invoice.getItems()) {
            stockAdjuster.adjust(item.getProduct(), item.getQuantity());
        }
        stockAdjuster.flushOrConflict("Stock changed concurrently while voiding, please retry");

        invoice.setStatus(InvoiceStatus.VOID);
        invoice.setVoidedAt(Instant.now());

        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }
}
