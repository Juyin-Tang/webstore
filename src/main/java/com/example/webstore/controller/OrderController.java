package com.example.webstore.controller;

import com.example.webstore.dto.OrderDtos.*;
import com.example.webstore.dto.PageResponse;
import com.example.webstore.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public PageResponse<OrderListResponse> list(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return orderService.list(status, page, size);
    }

    @GetMapping("/{id}")
    public OrderDetailResponse getById(@PathVariable Integer id) {
        return orderService.findById(id);
    }

    @PostMapping
    public ResponseEntity<OrderDetailResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderDetailResponse created = orderService.create(request);
        return ResponseEntity.created(URI.create("/api/orders/" + created.orderId())).body(created);
    }

    @PatchMapping("/{id}/status")
    public OrderDetailResponse updateStatus(@PathVariable Integer id,
                                            @Valid @RequestBody StatusUpdateRequest request) {
        return orderService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}