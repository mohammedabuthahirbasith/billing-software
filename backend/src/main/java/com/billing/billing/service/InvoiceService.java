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
import com.billing.billing.model.Store;
import com.billing.billing.repository.InvoiceRepository;
import com.billing.billing.repository.InvoiceReturnRepository;
import com.billing.billing.repository.ProductRepository;
import com.billing.billing.repository.StoreRepository;
import com.billing.billing.security.CurrentUser;

@Service
public class InvoiceService {

    private static final int MONEY_SCALE = 2;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceReturnRepository invoiceReturnRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;

    public InvoiceService(InvoiceRepository invoiceRepository, InvoiceReturnRepository invoiceReturnRepository,
                           ProductRepository productRepository, StoreRepository storeRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceReturnRepository = invoiceReturnRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
    }

    @Transactional
    public InvoiceResponse create(InvoiceRequest request) {
        Long storeId = CurrentUser.get().storeId();
        List<InvoiceItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal taxAmount = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // Duplicate productId across lines is allowed, not merged: each fetch returns the same
        // managed entity within this persistence context, so cumulative stock decrement is still correct.
        for (InvoiceItemRequest itemRequest : request.items()) {
            // Store-scoped: this is what stops a request from referencing, and thus buying and
            // decrementing stock on, another store's product (covers both the barcode-scan-by-SKU
            // and manual-picker-by-id frontend flows, which both resolve to this one call site).
            Product product = productRepository.findByIdAndStore_Id(itemRequest.productId(), storeId)
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

        Store store = storeRepository.getReferenceById(storeId);
        Invoice invoice = new Invoice(request.customerName(), request.customerPhone(),
                subtotal, taxAmount, subtotal.add(taxAmount), request.paymentMethod(), store);
        items.forEach(invoice::addItem);

        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    public List<InvoiceSummaryResponse> getAll() {
        Long storeId = CurrentUser.get().storeId();
        return invoiceRepository.findAllByStore_Id(storeId, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
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
        Long storeId = CurrentUser.get().storeId();
        return invoiceRepository.findByIdAndStore_Id(id, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + id));
    }
}
