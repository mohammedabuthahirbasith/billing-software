package com.billing.billing.service;

import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.model.Product;
import com.billing.billing.repository.ProductRepository;

// Every stock movement in the app (sale, void, return) is the same two steps: mutate the managed
// Product then force a flush so Product's @Version conflict surfaces as a clean 409 inside the
// caller's method. Left to the implicit flush-on-commit the failure fires after the service method
// returns and escapes as an unhandled 500 instead.
@Component
public class StockAdjuster {

    private final ProductRepository productRepository;

    public StockAdjuster(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public void adjust(Product product, int delta) {
        product.setStockQuantity(product.getStockQuantity() + delta);
        productRepository.save(product);
    }

    // One flush per operation rather than per line trades specific-SKU error attribution for a
    // single DB round trip — the right call for typical cart sizes.
    public void flushOrConflict(String conflictMessage) {
        try {
            productRepository.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, conflictMessage);
        }
    }
}
