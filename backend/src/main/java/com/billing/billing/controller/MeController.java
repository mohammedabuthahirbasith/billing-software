package com.billing.billing.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.billing.billing.dto.UserResponse;
import com.billing.billing.model.Store;
import com.billing.billing.repository.StoreRepository;
import com.billing.billing.security.CurrentUser;

@RestController
public class MeController {

    private final StoreRepository storeRepository;

    public MeController(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    @GetMapping("/api/me")
    public UserResponse me() {
        var current = CurrentUser.get();
        // storeId lives on the JWT (never stale — see User.store's no-setter/updatable=false design),
        // but the store's display NAME isn't baked into the token (a future store rename shouldn't
        // require re-issuing every live token), so this one field costs a lookup. findById (not
        // getReferenceById) here — we need the actual name, not just the id for an FK write, so the
        // lazy-proxy optimization doesn't apply and would only throw once the session's already closed.
        Store store = storeRepository.findById(current.storeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Store not found"));
        return new UserResponse(current.userId(), current.email(), current.role(), store.getName());
    }
}