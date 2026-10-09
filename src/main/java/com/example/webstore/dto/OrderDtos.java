package com.example.webstore.dto;

import com.example.webstore.dto.CustomerDtos.AddressResponse;
import com.example.webstore.dto.CustomerDtos.CustomerResponse;
import com.example.webstore.entity.CustomerOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record OrderLineRequest(
            @NotNull Integer productId,
            @NotNull @Min(1) @Max(100) Integer quantity) {
    }

    public record CreateOrderRequest(
            @NotNull Integer customerId,
            Integer shippingAddressId,
            @NotEmpty List<@Valid OrderLineRequest> items) {
    }

    public record StatusUpdateRequest(@NotBlank String status) {
    }

    public record OrderListResponse(Integer orderId, Integer customerId, String customerName,
                                    LocalDateTime orderDate, String status) {

        public static OrderListResponse from(CustomerOrder o) {
            return new OrderListResponse(o.getId(), o.getCustomer().getId(),
                    o.getCustomer().getFirstName() + " " + o.getCustomer().getLastName(),
                    o.getOrderDate(), o.getStatus());
        }
    }

    public record OrderLineResponse(Integer productId, String productName, Integer quantity,
                                    BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    public record OrderDetailResponse(Integer orderId, LocalDateTime orderDate, LocalDateTime deliveryDate,
                                      String status, CustomerResponse customer, AddressResponse shippingAddress,
                                      List<OrderLineResponse> items, BigDecimal totalAmount) {
    }

    public record OrderSummaryResponse(Integer orderId, LocalDateTime orderDate, LocalDateTime deliveryDate,
                                       String status, long itemCount, BigDecimal totalAmount) {
    }
}