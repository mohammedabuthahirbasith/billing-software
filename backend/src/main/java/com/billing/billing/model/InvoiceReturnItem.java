package com.billing.billing.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// No denormalized product snapshot fields (productName/sku/etc.) — invoiceItem always references an
// already-immutable InvoiceItem, so those are read through that reference, not duplicated again.
@Entity
@Table(name = "invoice_return_items")
public class InvoiceReturnItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_return_id", nullable = false)
    private InvoiceReturn invoiceReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_item_id", nullable = false, updatable = false)
    private InvoiceItem invoiceItem;

    @Column(name = "quantity_returned", nullable = false)
    private int quantityReturned;

    @Column(name = "line_subtotal_refund", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineSubtotalRefund;

    @Column(name = "line_tax_refund", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTaxRefund;

    @Column(name = "line_total_refund", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotalRefund;

    protected InvoiceReturnItem() {} // for JPA

    public InvoiceReturnItem(InvoiceItem invoiceItem, int quantityReturned, BigDecimal lineSubtotalRefund,
                              BigDecimal lineTaxRefund, BigDecimal lineTotalRefund) {
        this.invoiceItem = invoiceItem;
        this.quantityReturned = quantityReturned;
        this.lineSubtotalRefund = lineSubtotalRefund;
        this.lineTaxRefund = lineTaxRefund;
        this.lineTotalRefund = lineTotalRefund;
    }

    void setInvoiceReturn(InvoiceReturn invoiceReturn) { this.invoiceReturn = invoiceReturn; }

    public Long getId() { return id; }
    public InvoiceReturn getInvoiceReturn() { return invoiceReturn; }
    public InvoiceItem getInvoiceItem() { return invoiceItem; }
    public int getQuantityReturned() { return quantityReturned; }
    public BigDecimal getLineSubtotalRefund() { return lineSubtotalRefund; }
    public BigDecimal getLineTaxRefund() { return lineTaxRefund; }
    public BigDecimal getLineTotalRefund() { return lineTotalRefund; }
}