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

@Entity
@Table(name = "invoice_items")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    // Kept for traceability and the product-delete guard query; all pricing below is a snapshot,
    // not a live read through this reference.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(name = "hsn_code", length = 20)
    private String hsnCode;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineSubtotal;

    @Column(name = "line_tax", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTax;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    protected InvoiceItem() {} // for JPA

    public InvoiceItem(Product product, String productName, String sku, String hsnCode,
                        BigDecimal unitPrice, BigDecimal gstRate, int quantity,
                        BigDecimal lineSubtotal, BigDecimal lineTax, BigDecimal lineTotal) {
        this.product = product;
        this.productName = productName;
        this.sku = sku;
        this.hsnCode = hsnCode;
        this.unitPrice = unitPrice;
        this.gstRate = gstRate;
        this.quantity = quantity;
        this.lineSubtotal = lineSubtotal;
        this.lineTax = lineTax;
        this.lineTotal = lineTotal;
    }

    void setInvoice(Invoice invoice) { this.invoice = invoice; }

    public Long getId() { return id; }
    public Invoice getInvoice() { return invoice; }
    public Product getProduct() { return product; }
    public String getProductName() { return productName; }
    public String getSku() { return sku; }
    public String getHsnCode() { return hsnCode; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getGstRate() { return gstRate; }
    public int getQuantity() { return quantity; }
    public BigDecimal getLineSubtotal() { return lineSubtotal; }
    public BigDecimal getLineTax() { return lineTax; }
    public BigDecimal getLineTotal() { return lineTotal; }
}