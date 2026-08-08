package com.billing.billing.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.ProductRequest;
import com.billing.billing.dto.ProductResponse;
import com.billing.billing.model.Product;
import com.billing.billing.repository.InvoiceItemRepository;
import com.billing.billing.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final StoreScopedLookup lookup;

    public ProductService(ProductRepository productRepository, InvoiceItemRepository invoiceItemRepository,
                           StoreScopedLookup lookup) {
        this.productRepository = productRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.lookup = lookup;
    }

    public ProductResponse create(ProductRequest request) {
        requireSkuAvailable(request.sku());

        Product product = new Product(
                request.name(),
                request.sku(),
                request.description(),
                request.price(),
                request.gstRate(),
                request.hsnCode(),
                request.stockQuantity(),
                lookup.currentStoreReference()
        );

        return ProductResponse.from(productRepository.save(product));
    }

    public List<ProductResponse> getAll() {
        return productRepository.findAllByStore_Id(lookup.currentStoreId()).stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse getById(Long id) {
        return ProductResponse.from(lookup.product(id));
    }

    public ProductResponse getBySku(String sku) {
        Product product = productRepository.findByStore_IdAndSku(lookup.currentStoreId(), sku)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No product found for barcode: " + sku));
        return ProductResponse.from(product);
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Product product = lookup.product(id);

        if (!product.getSku().equals(request.sku())) {
            requireSkuAvailable(request.sku());
        }

        product.setName(request.name());
        product.setSku(request.sku());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setGstRate(request.gstRate());
        product.setHsnCode(request.hsnCode());
        product.setStockQuantity(request.stockQuantity());
        product.setUpdatedAt(Instant.now());

        return ProductResponse.from(productRepository.save(product));
    }

    public void delete(Long id) {
        Product product = lookup.product(id);
        if (invoiceItemRepository.existsByProduct_IdAndProduct_Store_Id(id, lookup.currentStoreId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete product with existing invoice history: " + id);
        }
        productRepository.delete(product);
    }

    // SKU is unique per store, not globally — the check has to be store-scoped on both write paths.
    private void requireSkuAvailable(String sku) {
        if (productRepository.existsByStore_IdAndSku(lookup.currentStoreId(), sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists: " + sku);
        }
    }
}
