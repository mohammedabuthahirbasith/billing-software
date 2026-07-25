package com.billing.billing.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.billing.billing.dto.ReturnRequest;
import com.billing.billing.dto.ReturnResponse;
import com.billing.billing.service.ReturnService;

@RestController
@RequestMapping("/api/invoices/{invoiceId}/returns")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @PreAuthorize("hasRole('OWNER')")
    @PostMapping
    public ResponseEntity<ReturnResponse> create(@PathVariable Long invoiceId, @Valid @RequestBody ReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(returnService.createReturn(invoiceId, request));
    }

    // No @PreAuthorize — matches InvoiceController.getById()'s "any authenticated user can view an
    // invoice" pattern; only creating a return is OWNER-only.
    @GetMapping
    public List<ReturnResponse> list(@PathVariable Long invoiceId) {
        return returnService.getReturnsForInvoice(invoiceId);
    }
}