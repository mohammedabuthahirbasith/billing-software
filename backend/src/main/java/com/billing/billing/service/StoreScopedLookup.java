package com.billing.billing.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.model.Invoice;
import com.billing.billing.model.Product;
import com.billing.billing.model.Store;
import com.billing.billing.repository.InvoiceRepository;
import com.billing.billing.repository.ProductRepository;
import com.billing.billing.repository.StoreRepository;
import com.billing.billing.security.CurrentUser;

// Every entity read in the app resolves the same way: take the storeId off the current request's
// JWT, look the row up scoped to it, and 404 if it isn't there. Funnelling that through one place
// means a new call site cannot accidentally omit the store predicate and read another tenant's row.
@Component
public class StoreScopedLookup {

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;

    public StoreScopedLookup(InvoiceRepository invoiceRepository, ProductRepository productRepository,
                              StoreRepository storeRepository) {
        this.invoiceRepository = invoiceRepository;
        this.productRepository = productRepository;
        this.storeRepository = storeRepository;
    }

    public Long currentStoreId() {
        return CurrentUser.get().storeId();
    }

    public Invoice invoice(Long id) {
        return invoiceRepository.findByIdAndStore_Id(id, currentStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found: " + id));
    }

    public Product product(Long id) {
        return productRepository.findByIdAndStore_Id(id, currentStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id));
    }

    // For FK writes only — a lazy proxy is enough to point a new row at the tenant and costs no query.
    public Store currentStoreReference() {
        return storeRepository.getReferenceById(currentStoreId());
    }

    // For call sites that actually read the store's fields (its display name), where the lazy-proxy
    // optimization doesn't apply and would only throw once the session has already closed. The store
    // is guaranteed to exist for any valid token, so its absence is a server-side invariant break.
    public Store currentStore() {
        return storeRepository.findById(currentStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Store not found"));
    }
}
