package com.billing.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.InvoiceItemRequest;
import com.billing.billing.dto.InvoiceRequest;
import com.billing.billing.dto.InvoiceResponse;
import com.billing.billing.model.Invoice;
import com.billing.billing.model.InvoiceStatus;
import com.billing.billing.model.PaymentMethod;
import com.billing.billing.model.Product;
import com.billing.billing.model.Role;
import com.billing.billing.model.Store;
import com.billing.billing.repository.InvoiceRepository;
import com.billing.billing.repository.InvoiceReturnRepository;
import com.billing.billing.repository.ProductRepository;
import com.billing.billing.repository.StoreRepository;
import com.billing.billing.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    private static final long STORE_ID = 3L;

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceReturnRepository invoiceReturnRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    private Store store;

    @BeforeEach
    void setUp() {
        store = TestEntities.store(STORE_ID, "Kirana Mart");
        TestEntities.authenticate(1L, "cashier@example.com", Role.CASHIER, STORE_ID);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static InvoiceRequest request(InvoiceItemRequest... items) {
        return new InvoiceRequest("Ravi", "9876543210", PaymentMethod.CASH, List.of(items));
    }

    private void stubSaveEchoingInvoice() {
        when(storeRepository.getReferenceById(STORE_ID)).thenReturn(store);
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(inv -> TestEntities.withId(inv.getArgument(0), 42L));
    }

    @Test
    void createRoundsTaxPerLineAndSumsAlreadyRoundedLineTotals() {
        // 99.99 x 3 = 299.97 subtotal; 18% of that is 53.9946, which must round to 53.99 per line
        // so the invoice total stays exactly equal to the sum of its printed line totals.
        Product product = TestEntities.product(10L, "SKU-1", "99.99", "18.00", 10, store);
        when(productRepository.findByIdAndStore_Id(10L, STORE_ID)).thenReturn(Optional.of(product));
        stubSaveEchoingInvoice();

        InvoiceResponse response = invoiceService.create(request(new InvoiceItemRequest(10L, 3)));

        assertThat(response.subtotal()).isEqualByComparingTo("299.97");
        assertThat(response.taxAmount()).isEqualByComparingTo("53.99");
        assertThat(response.totalAmount()).isEqualByComparingTo("353.96");
        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.lineTotal()).isEqualByComparingTo("353.96"));
        assertThat(response.invoiceNumber()).isEqualTo("INV-000042");
    }

    @Test
    void createSnapshotsProductPricingOntoEachLine() {
        Product product = TestEntities.product(10L, "SKU-1", "50.00", "12.00", 10, store);
        when(productRepository.findByIdAndStore_Id(10L, STORE_ID)).thenReturn(Optional.of(product));
        stubSaveEchoingInvoice();

        InvoiceResponse response = invoiceService.create(request(new InvoiceItemRequest(10L, 2)));

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("SKU-1");
            assertThat(item.unitPrice()).isEqualByComparingTo("50.00");
            assertThat(item.gstRate()).isEqualByComparingTo("12.00");
            assertThat(item.lineSubtotal()).isEqualByComparingTo("100.00");
            assertThat(item.lineTax()).isEqualByComparingTo("12.00");
        });
    }

    @Test
    void createDecrementsStockCumulativelyForDuplicateProductLines() {
        Product product = TestEntities.product(10L, "SKU-1", "10.00", "0.00", 10, store);
        when(productRepository.findByIdAndStore_Id(10L, STORE_ID)).thenReturn(Optional.of(product));
        stubSaveEchoingInvoice();

        invoiceService.create(request(new InvoiceItemRequest(10L, 3), new InvoiceItemRequest(10L, 4)));

        assertThat(product.getStockQuantity()).isEqualTo(3);
    }

    @Test
    void createRejectsUnknownProductWithoutTouchingStock() {
        when(productRepository.findByIdAndStore_Id(99L, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.create(request(new InvoiceItemRequest(99L, 1))))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void createRejectsInsufficientStock() {
        Product product = TestEntities.product(10L, "SKU-1", "10.00", "0.00", 2, store);
        when(productRepository.findByIdAndStore_Id(10L, STORE_ID)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> invoiceService.create(request(new InvoiceItemRequest(10L, 5))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient stock for SKU SKU-1")
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(product.getStockQuantity()).isEqualTo(2);
    }

    @Test
    void createMapsOptimisticLockFailureOnFlushToConflict() {
        Product product = TestEntities.product(10L, "SKU-1", "10.00", "0.00", 10, store);
        when(productRepository.findByIdAndStore_Id(10L, STORE_ID)).thenReturn(Optional.of(product));
        doThrow(new ObjectOptimisticLockingFailureException(Product.class, 10L)).when(productRepository).flush();

        assertThatThrownBy(() -> invoiceService.create(request(new InvoiceItemRequest(10L, 1))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Stock changed concurrently")
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void getAllListsCurrentStoreInvoicesNewestFirst() {
        Invoice invoice = TestEntities.invoice(1L, store, "100.00", "18.00", PaymentMethod.UPI);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        when(invoiceRepository.findAllByStore_Id(any(), any())).thenReturn(List.of(invoice));

        assertThat(invoiceService.getAll()).singleElement()
                .satisfies(summary -> assertThat(summary.invoiceNumber()).isEqualTo("INV-000001"));
        verify(invoiceRepository).findAllByStore_Id(any(), sortCaptor.capture());
        assertThat(sortCaptor.getValue()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void getByIdThrowsNotFoundForAnotherStoresInvoice() {
        when(invoiceRepository.findByIdAndStore_Id(5L, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getById(5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invoice not found: 5");
    }

    @Test
    void voidInvoiceRestocksEveryLineAndStampsVoidedAt() {
        Product product = TestEntities.product(10L, "SKU-1", "10.00", "0.00", 1, store);
        Invoice invoice = TestEntities.invoice(5L, store, "30.00", "0.00", PaymentMethod.CARD);
        invoice.addItem(TestEntities.invoiceItem(1L, product, 3, "10.00", "0.00"));
        when(invoiceRepository.findByIdAndStore_Id(5L, STORE_ID)).thenReturn(Optional.of(invoice));
        when(invoiceReturnRepository.existsByInvoice_Id(5L)).thenReturn(false);
        when(invoiceRepository.save(invoice)).thenReturn(invoice);

        InvoiceResponse response = invoiceService.voidInvoice(5L);

        assertThat(product.getStockQuantity()).isEqualTo(4);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VOID);
        assertThat(response.voidedAt()).isNotNull();
    }

    @Test
    void voidInvoiceRejectsAlreadyVoidedInvoice() {
        Invoice invoice = TestEntities.invoice(5L, store, "30.00", "0.00", PaymentMethod.CARD);
        invoice.setStatus(InvoiceStatus.VOID);
        when(invoiceRepository.findByIdAndStore_Id(5L, STORE_ID)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.voidInvoice(5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invoice already voided: 5");
        verify(invoiceReturnRepository, never()).existsByInvoice_Id(any());
    }

    @Test
    void voidInvoiceRejectsInvoiceThatAlreadyHasReturns() {
        Product product = TestEntities.product(10L, "SKU-1", "10.00", "0.00", 1, store);
        Invoice invoice = TestEntities.invoice(5L, store, "30.00", "0.00", PaymentMethod.CARD);
        invoice.addItem(TestEntities.invoiceItem(1L, product, 3, "10.00", "0.00"));
        when(invoiceRepository.findByIdAndStore_Id(5L, STORE_ID)).thenReturn(Optional.of(invoice));
        when(invoiceReturnRepository.existsByInvoice_Id(5L)).thenReturn(true);

        assertThatThrownBy(() -> invoiceService.voidInvoice(5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot void an invoice with existing returns: 5");
        assertThat(product.getStockQuantity()).isEqualTo(1);
    }

    @Test
    void voidInvoiceMapsOptimisticLockFailureToConflict() {
        Product product = TestEntities.product(10L, "SKU-1", "10.00", "0.00", 1, store);
        Invoice invoice = TestEntities.invoice(5L, store, "30.00", "0.00", PaymentMethod.CARD);
        invoice.addItem(TestEntities.invoiceItem(1L, product, 3, "10.00", "0.00"));
        when(invoiceRepository.findByIdAndStore_Id(5L, STORE_ID)).thenReturn(Optional.of(invoice));
        when(invoiceReturnRepository.existsByInvoice_Id(5L)).thenReturn(false);
        doThrow(new ObjectOptimisticLockingFailureException(Product.class, 10L)).when(productRepository).flush();

        assertThatThrownBy(() -> invoiceService.voidInvoice(5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Stock changed concurrently while voiding");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.COMPLETED);
    }
}
