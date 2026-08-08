package com.billing.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
import com.billing.billing.model.Product;
import com.billing.billing.model.Store;
import com.billing.billing.repository.InvoiceItemRepository;
import com.billing.billing.repository.InvoiceRepository;
import com.billing.billing.repository.InvoiceReturnItemRepository;
import com.billing.billing.repository.InvoiceReturnRepository;
import com.billing.billing.repository.ProductRepository;
import com.billing.billing.repository.StoreRepository;
import com.billing.billing.security.CurrentUser;

@Service
public class ReturnService {

    private static final int MONEY_SCALE = 2;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceReturnRepository invoiceReturnRepository;
    private final InvoiceReturnItemRepository invoiceReturnItemRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;

    public ReturnService(InvoiceRepository invoiceRepository, InvoiceItemRepository invoiceItemRepository,
                          InvoiceReturnRepository invoiceReturnRepository,
                          InvoiceReturnItemRepository invoiceReturnItemRepository,
                          ProductRepository productRepository, StoreRepository storeRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.invoiceReturnRepository = invoiceReturnRepository;
        this.invoiceReturnItemRepository = invoiceReturnItemRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
    }

    @Transactional
    public ReturnResponse createReturn(Long invoiceId, ReturnRequest request) {
        Long storeId = CurrentUser.get().storeId();
        Invoice invoice = invoiceRepository.findByIdAndStore_Id(invoiceId, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot return items from a voided invoice");
        }

        List<InvoiceReturnItem> returnItems = new ArrayList<>();
        BigDecimal refundSubtotal = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal refundTax = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

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

            // Prorate off the ORIGINAL line's unit price/GST rate — same round-per-line discipline
            // as invoice creation, so a return's totals are internally consistent the same way.
            BigDecimal lineSubtotalRefund = invoiceItem.getUnitPrice()
                    .multiply(BigDecimal.valueOf(lineRequest.quantity()))
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTaxRefund = lineSubtotalRefund.multiply(invoiceItem.getGstRate())
                    .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTotalRefund = lineSubtotalRefund.add(lineTaxRefund);

            returnItems.add(new InvoiceReturnItem(invoiceItem, lineRequest.quantity(),
                    lineSubtotalRefund, lineTaxRefund, lineTotalRefund));

            refundSubtotal = refundSubtotal.add(lineSubtotalRefund);
            refundTax = refundTax.add(lineTaxRefund);

            // Restock — mirrors InvoiceService.voidInvoice()'s restock pattern exactly.
            Product product = invoiceItem.getProduct();
            product.setStockQuantity(product.getStockQuantity() + lineRequest.quantity());
            productRepository.save(product);
        }

        try {
            productRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock changed concurrently, please retry", e);
        }

        Store store = storeRepository.getReferenceById(storeId);
        InvoiceReturn invoiceReturn = new InvoiceReturn(store, invoice, refundSubtotal, refundTax,
                refundSubtotal.add(refundTax));
        returnItems.forEach(invoiceReturn::addItem);

        return ReturnResponse.from(invoiceReturnRepository.save(invoiceReturn));
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> getReturnsForInvoice(Long invoiceId) {
        Long storeId = CurrentUser.get().storeId();
        invoiceRepository.findByIdAndStore_Id(invoiceId, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + invoiceId));

        return invoiceReturnRepository.findAllByInvoice_IdOrderByCreatedAtAsc(invoiceId).stream()
                .map(ReturnResponse::from)
                .toList();
    }
}