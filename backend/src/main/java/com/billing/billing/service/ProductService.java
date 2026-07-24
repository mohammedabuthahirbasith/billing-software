package com.billing.billing.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.ProductRequest;
import com.billing.billing.dto.ProductResponse;
import com.billing.billing.model.Product;
import com.billing.billing.model.Store;
import com.billing.billing.repository.InvoiceItemRepository;
import com.billing.billing.repository.ProductRepository;
import com.billing.billing.repository.StoreRepository;
import com.billing.billing.security.CurrentUser;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final StoreRepository storeRepository;

    public ProductService(ProductRepository productRepository, InvoiceItemRepository invoiceItemRepository,
                           StoreRepository storeRepository) {
        this.productRepository = productRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.storeRepository = storeRepository;
    }

    public ProductResponse create(ProductRequest request) {
        Long storeId = CurrentUser.get().storeId();
        if (productRepository.existsByStore_IdAndSku(storeId, request.sku())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists: " + request.sku());
        }

        Store store = storeRepository.getReferenceById(storeId);
        Product product = new Product(
                request.name(),
                request.sku(),
                request.description(),
                request.price(),
                request.gstRate(),
                request.hsnCode(),
                request.stockQuantity(),
                store
        );

        return ProductResponse.from(productRepository.save(product));
    }

    public List<ProductResponse> getAll() {
        Long storeId = CurrentUser.get().storeId();
        return productRepository.findAllByStore_Id(storeId).stream()
                .map(ProductResponse::from)
                .toList();
    }

    public ProductResponse getById(Long id) {
        return ProductResponse.from(findProductOrThrow(id));
    }

    public ProductResponse getBySku(String sku) {
        Long storeId = CurrentUser.get().storeId();
        Product product = productRepository.findByStore_IdAndSku(storeId, sku)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No product found for barcode: " + sku));
        return ProductResponse.from(product);
    }

    public ProductResponse update(Long id, ProductRequest request) {
        Long storeId = CurrentUser.get().storeId();
        Product product = findProductOrThrow(id);

        boolean skuChanged = !product.getSku().equals(request.sku());
        if (skuChanged && productRepository.existsByStore_IdAndSku(storeId, request.sku())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already exists: " + request.sku());
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
        Long storeId = CurrentUser.get().storeId();
        Product product = findProductOrThrow(id);
        if (invoiceItemRepository.existsByProduct_IdAndProduct_Store_Id(id, storeId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete product with existing invoice history: " + id);
        }
        productRepository.delete(product);
    }

    private Product findProductOrThrow(Long id) {
        Long storeId = CurrentUser.get().storeId();
        return productRepository.findByIdAndStore_Id(id, storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id));
    }
}
