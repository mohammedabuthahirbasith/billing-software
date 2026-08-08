package com.billing.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.ProductRequest;
import com.billing.billing.dto.ProductResponse;
import com.billing.billing.model.Product;
import com.billing.billing.model.Role;
import com.billing.billing.model.Store;
import com.billing.billing.repository.InvoiceItemRepository;
import com.billing.billing.repository.ProductRepository;
import com.billing.billing.repository.StoreRepository;
import com.billing.billing.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final long STORE_ID = 7L;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private InvoiceItemRepository invoiceItemRepository;
    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private ProductService productService;

    private Store store;

    @BeforeEach
    void setUp() {
        store = TestEntities.store(STORE_ID, "Kirana Mart");
        TestEntities.authenticate(1L, "owner@example.com", Role.OWNER, STORE_ID);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static ProductRequest request(String sku) {
        return new ProductRequest("Basmati Rice", sku, "5kg bag", new BigDecimal("450.00"),
                new BigDecimal("5.00"), "1006", 20);
    }

    @Test
    void createPersistsProductScopedToCurrentStore() {
        when(productRepository.existsByStore_IdAndSku(STORE_ID, "RICE-1")).thenReturn(false);
        when(storeRepository.getReferenceById(STORE_ID)).thenReturn(store);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> TestEntities.withId(inv.getArgument(0), 55L));

        ProductResponse response = productService.create(request("RICE-1"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getStore()).isSameAs(store);
        assertThat(captor.getValue().getSku()).isEqualTo("RICE-1");
        assertThat(response.id()).isEqualTo(55L);
        assertThat(response.price()).isEqualByComparingTo("450.00");
    }

    @Test
    void createRejectsDuplicateSkuWithinStore() {
        when(productRepository.existsByStore_IdAndSku(STORE_ID, "RICE-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request("RICE-1")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(productRepository, never()).save(any());
    }

    @Test
    void getAllReturnsOnlyCurrentStoreProducts() {
        when(productRepository.findAllByStore_Id(STORE_ID)).thenReturn(List.of(
                TestEntities.product(1L, "A", "10.00", "5.00", 3, store),
                TestEntities.product(2L, "B", "20.00", "5.00", 4, store)));

        assertThat(productService.getAll()).extracting(ProductResponse::sku).containsExactly("A", "B");
    }

    @Test
    void getByIdThrowsNotFoundWhenProductBelongsToAnotherStore() {
        when(productRepository.findByIdAndStore_Id(9L, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Product not found: 9");
    }

    @Test
    void getBySkuResolvesBarcodeWithinStore() {
        when(productRepository.findByStore_IdAndSku(STORE_ID, "BAR-1"))
                .thenReturn(Optional.of(TestEntities.product(3L, "BAR-1", "99.00", "12.00", 5, store)));

        assertThat(productService.getBySku("BAR-1").id()).isEqualTo(3L);
    }

    @Test
    void getBySkuThrowsNotFoundForUnknownBarcode() {
        when(productRepository.findByStore_IdAndSku(STORE_ID, "NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getBySku("NOPE"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No product found for barcode: NOPE");
    }

    @Test
    void updateOverwritesAllMutableFieldsAndStampsUpdatedAt() {
        Product existing = TestEntities.product(4L, "OLD", "10.00", "5.00", 1, store);
        when(productRepository.findByIdAndStore_Id(4L, STORE_ID)).thenReturn(Optional.of(existing));
        when(productRepository.existsByStore_IdAndSku(STORE_ID, "RICE-1")).thenReturn(false);
        when(productRepository.save(existing)).thenReturn(existing);

        ProductResponse response = productService.update(4L, request("RICE-1"));

        assertThat(existing.getSku()).isEqualTo("RICE-1");
        assertThat(existing.getName()).isEqualTo("Basmati Rice");
        assertThat(existing.getStockQuantity()).isEqualTo(20);
        assertThat(response.updatedAt()).isNotNull();
    }

    @Test
    void updateKeepingSameSkuSkipsUniquenessCheck() {
        Product existing = TestEntities.product(4L, "RICE-1", "10.00", "5.00", 1, store);
        when(productRepository.findByIdAndStore_Id(4L, STORE_ID)).thenReturn(Optional.of(existing));
        when(productRepository.save(existing)).thenReturn(existing);

        productService.update(4L, request("RICE-1"));

        verify(productRepository, never()).existsByStore_IdAndSku(any(), any());
    }

    @Test
    void updateRejectsRenameOntoAnExistingSku() {
        Product existing = TestEntities.product(4L, "OLD", "10.00", "5.00", 1, store);
        when(productRepository.findByIdAndStore_Id(4L, STORE_ID)).thenReturn(Optional.of(existing));
        when(productRepository.existsByStore_IdAndSku(STORE_ID, "RICE-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.update(4L, request("RICE-1")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("SKU already exists: RICE-1");
        verify(productRepository, never()).save(any());
    }

    @Test
    void deleteRemovesProductWithoutInvoiceHistory() {
        Product existing = TestEntities.product(4L, "RICE-1", "10.00", "5.00", 1, store);
        when(productRepository.findByIdAndStore_Id(4L, STORE_ID)).thenReturn(Optional.of(existing));
        when(invoiceItemRepository.existsByProduct_IdAndProduct_Store_Id(4L, STORE_ID)).thenReturn(false);

        productService.delete(4L);

        verify(productRepository).delete(existing);
    }

    @Test
    void deleteRejectsProductWithInvoiceHistory() {
        Product existing = TestEntities.product(4L, "RICE-1", "10.00", "5.00", 1, store);
        when(productRepository.findByIdAndStore_Id(4L, STORE_ID)).thenReturn(Optional.of(existing));
        when(invoiceItemRepository.existsByProduct_IdAndProduct_Store_Id(4L, STORE_ID)).thenReturn(true);

        assertThatThrownBy(() -> productService.delete(4L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("existing invoice history");
        verify(productRepository, never()).delete(any(Product.class));
    }
}
