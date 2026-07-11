package com.billing.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.billing.billing.model.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsBySku(String sku);
}