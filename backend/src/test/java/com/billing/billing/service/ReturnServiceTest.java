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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.ReturnRequest;
import com.billing.billing.dto.ReturnResponse;
import com.billing.billing.model.Invoice;
import com.billing.billing.model.InvoiceItem;
import com.billing.billing.model.InvoiceReturn;
import com.billing.billing.model.InvoiceStatus;
import com.billing.billing.model.PaymentMethod;
import com.billing.billing.model.Product;
import com.billing.billing.model.Role;
import com.billing.billing.model.Store;
import com.billing.billing.repository.InvoiceItemRepository;
import com.billing.billing.repository.InvoiceRepository;
import com.billing.billing.repository.InvoiceReturnItemRepository;
import com.billing.billing.repository.InvoiceReturnRepository;
import com.billing.billing.repository.ProductRepository;
import com.billing.billing.repository.StoreRepository;
import com.billing.billing.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class ReturnServiceTest {

    private static final long STORE_ID = 3L;
    private static final long INVOICE_ID = 5L;

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private InvoiceItemRepository invoiceItemRepository;
    @Mock
    private InvoiceReturnRepository invoiceReturnRepository;
    @Mock
    private InvoiceReturnItemRepository invoiceReturnItemRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StoreRepository storeRepository;

    @InjectMocks
    private ReturnService returnService;

    private Store store;
    private Product product;
    private Invoice invoice;
    private InvoiceItem invoiceItem;

    @BeforeEach
    void setUp() {
        store = TestEntities.store(STORE_ID, "Kirana Mart");
        product = TestEntities.product(10L, "SKU-1", "99.99", "18.00", 4, store);
        invoice = TestEntities.invoice(INVOICE_ID, store, "299.97", "53.99", PaymentMethod.CASH);
        invoiceItem = TestEntities.invoiceItem(77L, product, 3, "99.99", "18.00");
        invoice.addItem(invoiceItem);
        TestEntities.authenticate(1L, "cashier@example.com", Role.CASHIER, STORE_ID);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static ReturnRequest request(long invoiceItemId, int quantity) {
        return new ReturnRequest(List.of(new ReturnRequest.ReturnItemRequest(invoiceItemId, quantity)));
    }

    private void stubSaveEchoingReturn() {
        when(storeRepository.getReferenceById(STORE_ID)).thenReturn(store);
        when(invoiceReturnRepository.save(any(InvoiceReturn.class)))
                .thenAnswer(inv -> TestEntities.withId(inv.getArgument(0), 90L));
    }

    @Test
    void createReturnProratesRefundOffOriginalLinePricingAndRestocks() {
        when(invoiceRepository.findByIdAndStore_Id(INVOICE_ID, STORE_ID)).thenReturn(Optional.of(invoice));
        when(invoiceItemRepository.findByIdAndInvoice_Id(77L, INVOICE_ID)).thenReturn(Optional.of(invoiceItem));
        when(invoiceReturnItemRepository.sumReturnedQuantityByInvoiceItemId(77L)).thenReturn(0);
        stubSaveEchoingReturn();

        ReturnResponse response = returnService.createReturn(INVOICE_ID, request(77L, 2));

        // 99.99 x 2 = 199.98, 18% of which is 35.9964 -> 36.00 rounded per line.
        assertThat(response.refundSubtotal()).isEqualByComparingTo("199.98");
        assertThat(response.refundTax()).isEqualByComparingTo("36.00");
        assertThat(response.refundTotal()).isEqualByComparingTo("235.98");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.invoiceItemId()).isEqualTo(77L);
            assertThat(item.sku()).isEqualTo("SKU-1");
            assertThat(item.quantityReturned()).isEqualTo(2);
        });
        assertThat(product.getStockQuantity()).isEqualTo(6);
    }

    @Test
    void createReturnAllowsOnlyTheStillReturnableRemainder() {
        when(invoiceRepository.findByIdAndStore_Id(INVOICE_ID, STORE_ID)).thenReturn(Optional.of(invoice));
        when(invoiceItemRepository.findByIdAndInvoice_Id(77L, INVOICE_ID)).thenReturn(Optional.of(invoiceItem));
        when(invoiceReturnItemRepository.sumReturnedQuantityByInvoiceItemId(77L)).thenReturn(2);
        stubSaveEchoingReturn();

        ReturnResponse response = returnService.createReturn(INVOICE_ID, request(77L, 1));

        assertThat(response.items()).singleElement()
                .satisfies(item -> assertThat(item.quantityReturned()).isEqualTo(1));
    }

    @Test
    void createReturnRejectsQuantityBeyondTheReturnableRemainder() {
        when(invoiceRepository.findByIdAndStore_Id(INVOICE_ID, STORE_ID)).thenReturn(Optional.of(invoice));
        when(invoiceItemRepository.findByIdAndInvoice_Id(77L, INVOICE_ID)).thenReturn(Optional.of(invoiceItem));
        when(invoiceReturnItemRepository.sumReturnedQuantityByInvoiceItemId(77L)).thenReturn(2);

        assertThatThrownBy(() -> returnService.createReturn(INVOICE_ID, request(77L, 2)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("only 1 remaining returnable")
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(product.getStockQuantity()).isEqualTo(4);
        verify(invoiceReturnRepository, never()).save(any());
    }

    @Test
    void createReturnRejectsVoidedInvoice() {
        invoice.setStatus(InvoiceStatus.VOID);
        when(invoiceRepository.findByIdAndStore_Id(INVOICE_ID, STORE_ID)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> returnService.createReturn(INVOICE_ID, request(77L, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot return items from a voided invoice");
    }

    @Test
    void createReturnRejectsInvoiceFromAnotherStore() {
        when(invoiceRepository.findByIdAndStore_Id(INVOICE_ID, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> returnService.createReturn(INVOICE_ID, request(77L, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void createReturnRejectsInvoiceItemFromAnotherInvoice() {
        when(invoiceRepository.findByIdAndStore_Id(INVOICE_ID, STORE_ID)).thenReturn(Optional.of(invoice));
        when(invoiceItemRepository.findByIdAndInvoice_Id(88L, INVOICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> returnService.createReturn(INVOICE_ID, request(88L, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("does not belong to invoice " + INVOICE_ID)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createReturnMapsOptimisticLockFailureToConflict() {
        when(invoiceRepository.findByIdAndStore_Id(INVOICE_ID, STORE_ID)).thenReturn(Optional.of(invoice));
        when(invoiceItemRepository.findByIdAndInvoice_Id(77L, INVOICE_ID)).thenReturn(Optional.of(invoiceItem));
        when(invoiceReturnItemRepository.sumReturnedQuantityByInvoiceItemId(77L)).thenReturn(0);
        doThrow(new ObjectOptimisticLockingFailureException(Product.class, 10L)).when(productRepository).flush();

        assertThatThrownBy(() -> returnService.createReturn(INVOICE_ID, request(77L, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Stock changed concurrently");
        verify(invoiceReturnRepository, never()).save(any());
    }

    @Test
    void getReturnsForInvoiceListsReturnsOldestFirst() {
        when(invoiceRepository.findByIdAndStore_Id(INVOICE_ID, STORE_ID)).thenReturn(Optional.of(invoice));
        InvoiceReturn first = TestEntities.withId(new InvoiceReturn(store, invoice,
                new java.math.BigDecimal("99.99"), new java.math.BigDecimal("18.00"),
                new java.math.BigDecimal("117.99")), 1L);
        when(invoiceReturnRepository.findAllByInvoice_IdOrderByCreatedAtAsc(INVOICE_ID)).thenReturn(List.of(first));

        assertThat(returnService.getReturnsForInvoice(INVOICE_ID)).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(1L);
            assertThat(r.invoiceId()).isEqualTo(INVOICE_ID);
            assertThat(r.items()).isEmpty();
        });
    }

    @Test
    void getReturnsForInvoiceThrowsNotFoundForAnotherStoresInvoice() {
        when(invoiceRepository.findByIdAndStore_Id(INVOICE_ID, STORE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> returnService.getReturnsForInvoice(INVOICE_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invoice not found: " + INVOICE_ID);
    }
}
