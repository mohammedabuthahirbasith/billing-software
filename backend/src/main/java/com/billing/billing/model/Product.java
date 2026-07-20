package com.billing.billing.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String sku;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate;   // e.g. 18.00 (percent)

    @Column(length = 20)
    private String hsnCode;

    @Column(nullable = false)
    private int stockQuantity;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    // Optimistic locking: makes concurrent stock decrements (from Invoice creation) fail fast
    // with ObjectOptimisticLockingFailureException instead of silently losing an update.
    @Version
    @Column(nullable = false)
    private Long version;

    protected Product() {} // for JPA

    public Product(String name, String sku, String description, BigDecimal price,
                   BigDecimal gstRate, String hsnCode, int stockQuantity) {
        this.name = name;
        this.sku = sku;
        this.description = description;
        this.price = price;
        this.gstRate = gstRate;
        this.hsnCode = hsnCode;
        this.stockQuantity = stockQuantity;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getGstRate() { return gstRate; }
    public String getHsnCode() { return hsnCode; }
    public int getStockQuantity() { return stockQuantity; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    public void setName(String name) { this.name = name; }
    public void setSku(String sku) { this.sku = sku; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setGstRate(BigDecimal gstRate) { this.gstRate = gstRate; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}