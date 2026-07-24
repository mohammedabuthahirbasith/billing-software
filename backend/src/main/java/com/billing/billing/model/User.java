package com.billing.billing.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // No setter, updatable = false — a user's store is fixed at creation. This is what guarantees
    // a JWT's storeId claim can never go stale relative to the DB for the life of a token.
    @ManyToOne
    @JoinColumn(name = "store_id", nullable = false, updatable = false)
    private Store store;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected User() {} // for Hibernate

    public User(String email, String passwordHash, Role role, Store store) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.store = store;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Store getStore() { return store; }
    public Instant getCreatedAt() { return createdAt; }
}