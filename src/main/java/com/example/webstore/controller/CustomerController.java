package com.example.webstore.controller;

import com.example.webstore.dto.CustomerDtos.*;
import com.example.webstore.dto.OrderDtos.OrderSummaryResponse;
import com.example.webstore.dto.PageResponse;
import com.example.webstore.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public PageResponse<CustomerResponse> getAll(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return customerService.findAll(page, size);
    }

    @GetMapping("/{id}")
    public CustomerDetailResponse getById(@PathVariable Integer id) {
        return customerService.findById(id);
    }

    @GetMapping("/{id}/orders")
    public List<OrderSummaryResponse> getOrders(@PathVariable Integer id) {
        return customerService.findOrders(id);
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse created = customerService.create(request);
        return ResponseEntity.created(URI.create("/api/customers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Integer id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}