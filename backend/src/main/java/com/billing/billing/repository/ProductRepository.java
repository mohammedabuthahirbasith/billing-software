package com.billing.billing.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.billing.billing.model.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByStore_IdAndSku(Long storeId, String sku);
    Optional<Product> findByStore_IdAndSku(Long storeId, String sku);
    Optional<Product> findByIdAndStore_Id(Long id, Long storeId);
    List<Product> findAllByStore_Id(Long storeId);
}
