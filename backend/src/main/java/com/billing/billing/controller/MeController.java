package com.billing.billing.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.billing.billing.dto.UserResponse;
import com.billing.billing.security.CurrentUser;
import com.billing.billing.service.StoreScopedLookup;

@RestController
public class MeController {

    private final StoreScopedLookup lookup;

    public MeController(StoreScopedLookup lookup) {
        this.lookup = lookup;
    }

    @GetMapping("/api/me")
    public UserResponse me() {
        var current = CurrentUser.get();
        // storeId lives on the JWT (never stale — see User.store's no-setter/updatable=false design),
        // but the store's display NAME isn't baked into the token (a future store rename shouldn't
        // require re-issuing every live token), so this one field costs a lookup.
        return new UserResponse(current.userId(), current.email(), current.role(), lookup.currentStore().getName());
    }
}
