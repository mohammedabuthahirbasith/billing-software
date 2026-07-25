package com.billing.billing.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

// Append-only, like Invoice itself: a return never mutates the original invoice/items, it's a
// separate record referencing them, preserving the full audit trail of what was sold vs. returned.
@Entity
@Table(name = "invoice_returns")
public class InvoiceReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false, updatable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(name = "refund_subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundSubtotal;

    @Column(name = "refund_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundTax;

    @Column(name = "refund_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundTotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "invoiceReturn", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<InvoiceReturnItem> items = new ArrayList<>();

    protected InvoiceReturn() {} // for JPA

    public InvoiceReturn(Store store, Invoice invoice, BigDecimal refundSubtotal, BigDecimal refundTax,
                          BigDecimal refundTotal) {
        this.store = store;
        this.invoice = invoice;
        this.refundSubtotal = refundSubtotal;
        this.refundTax = refundTax;
        this.refundTotal = refundTotal;
    }

    public void addItem(InvoiceReturnItem item) {
        item.setInvoiceReturn(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public Store getStore() { return store; }
    public Invoice getInvoice() { return invoice; }
    public BigDecimal getRefundSubtotal() { return refundSubtotal; }
    public BigDecimal getRefundTax() { return refundTax; }
    public BigDecimal getRefundTotal() { return refundTotal; }
    public Instant getCreatedAt() { return createdAt; }
    public List<InvoiceReturnItem> getItems() { return items; }
}