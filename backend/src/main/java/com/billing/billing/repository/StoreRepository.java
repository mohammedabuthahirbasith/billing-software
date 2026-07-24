package com.billing.billing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.billing.billing.model.Store;

public interface StoreRepository extends JpaRepository<Store, Long> {
}