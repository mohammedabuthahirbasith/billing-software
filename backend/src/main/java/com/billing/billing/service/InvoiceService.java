package com.billing.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import com.billing.billing.repository.ProductRepository;

@Service
public class InvoiceService {

    private static final int MONEY_SCALE = 2;

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;

    public InvoiceService(InvoiceRepository invoiceRepository, ProductRepository productRepository) {
        this.invoiceRepository = invoiceRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        List<InvoiceItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal taxAmount = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // Duplicate productId across lines is allowed, not merged: each fetch returns the same
        // managed entity within this persistence context, so cumulative stock decrement is still correct.
        for (InvoiceItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Product not found: " + itemRequest.productId()));

            int quantity = itemRequest.quantity();
            if (product.getStockQuantity() < quantity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Insufficient stock for SKU " + product.getSku() + ": requested " + quantity
                                + ", available " + product.getStockQuantity());
            }
            product.setStockQuantity(product.getStockQuantity() - quantity);
            productRepository.save(product);

            // Round per line, then sum already-rounded values into the invoice totals — keeps the
            // invoice total exactly equal to the sum of its printed line totals. price/gstRate are
            // both scale-2 numeric columns, but their raw product/quotient in Java lands at scale 4,
            // so this rounding must happen here, not deferred to persistence.
            BigDecimal lineSubtotal = product.getPrice()
                    .multiply(BigDecimal.valueOf(quantity))
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineSubtotal.multiply(product.getGstRate())
                    .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineSubtotal.add(lineTax);

            items.add(new InvoiceItem(product, product.getName(), product.getSku(), product.getHsnCode(),
                    product.getPrice(), product.getGstRate(), quantity, lineSubtotal, lineTax, lineTotal));

            subtotal = subtotal.add(lineSubtotal);
            taxAmount = taxAmount.add(lineTax);
        }

        // Explicit flush inside this method (not left to implicit flush-on-commit) so an optimistic-lock
        // failure is catchable here and mapped to a clean 409 — left implicit, it fires after this method
        // returns and surfaces as an unhandled 500 instead. One flush after the loop (not per item) trades
        // specific-SKU error attribution for a single DB round trip — the right call for typical cart sizes.
        try {
            productRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock changed concurrently on one or more items, please retry");
        }

        Invoice invoice = new Invoice(request.customerName(), request.customerPhone(),
                subtotal, taxAmount, subtotal.add(taxAmount));
        items.forEach(invoice::addItem);

        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    public List<InvoiceSummaryResponse> getAll() {
        return invoiceRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(InvoiceSummaryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(Long id) {
        return InvoiceResponse.from(findInvoiceOrThrow(id));
    }

    @Transactional
    public InvoiceResponse voidInvoice(Long id) {
        Invoice invoice = findInvoiceOrThrow(id);

        if (invoice.getStatus() == InvoiceStatus.VOID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice already voided: " + id);
        }

        // Restore stock for every line. Two concurrent voids of the same invoice collide on Product's
        // @Version during the flush below (whichever commits second rolls back entirely, including the
        // status flip) — so double-restoration is already prevented transitively, with no @Version
        // needed on Invoice itself.
        for (InvoiceItem item : invoice.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }

        try {
            productRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stock changed concurrently while voiding, please retry");
        }

        invoice.setStatus(InvoiceStatus.VOID);
        invoice.setVoidedAt(Instant.now());

        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    private Invoice findInvoiceOrThrow(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + id));
    }
}