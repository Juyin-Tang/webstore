package com.example.webstore.service;

import com.example.webstore.dto.CustomerDtos.AddressResponse;
import com.example.webstore.dto.CustomerDtos.CustomerResponse;
import com.example.webstore.dto.OrderDtos.*;
import com.example.webstore.dto.PageResponse;
import com.example.webstore.entity.*;
import com.example.webstore.exception.ApiException;
import com.example.webstore.repository.CustomerAddressRepository;
import com.example.webstore.repository.CustomerOrderRepository;
import com.example.webstore.repository.CustomerRepository;
import com.example.webstore.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class OrderService {

    private static final Set<String> STATUSES = Set.of("NEW", "SHIPPED", "CANCELLED");

    private final CustomerOrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    public OrderService(CustomerOrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        CustomerAddressRepository addressRepository,
                        ProductRepository productRepository,
                        EntityManager entityManager) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderListResponse> list(String status, int page, int size) {
        String st = (status == null || status.isBlank()) ? null : checkStatus(status);
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "orderDate", "id");
        return PageResponse.from(orderRepository.search(st, PageResponse.request(page, size, newestFirst)),
                OrderListResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse findById(Integer id) {
        CustomerOrder order = orderRepository.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("Order", id));
        return toDetail(order);
    }

    public OrderDetailResponse create(CreateOrderRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> ApiException.notFound("Customer", request.customerId()));

        CustomerAddress address = null;
        if (request.shippingAddressId() != null) {
            address = addressRepository.findById(request.shippingAddressId())
                    .orElseThrow(() -> ApiException.notFound("Address", request.shippingAddressId()));
            if (!address.getCustomer().getId().equals(customer.getId())) {
                throw ApiException.badRequest("Address " + address.getId()
                        + " does not belong to customer " + customer.getId());
            }
        }

        CustomerOrder order = new CustomerOrder();
        order.setCustomer(customer);
        order.setShippingAddress(address);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus("NEW");

        Set<Integer> productIds = new HashSet<>();
        for (OrderLineRequest line : request.items()) {
            if (!productIds.add(line.productId())) {
                throw ApiException.badRequest("Product " + line.productId() + " appears more than once");
            }
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> ApiException.notFound("Product", line.productId()));
            order.addItem(new OrderItem(product, line.quantity(), product.getPrice()));
        }

        CustomerOrder saved = orderRepository.saveAndFlush(order);
        return toDetail(saved);
    }

    public OrderDetailResponse updateStatus(Integer id, String newStatus) {
        String status = checkStatus(newStatus);
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Order", id));
        order.setStatus(status);
        orderRepository.saveAndFlush(order);
        entityManager.clear();
        return findById(id);
    }

    public void delete(Integer id) {
        CustomerOrder order = orderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Order", id));
        if ("SHIPPED".equals(order.getStatus())) {
            throw ApiException.conflict("Order " + id + " has been shipped and cannot be deleted");
        }
        orderRepository.delete(order);
        orderRepository.flush();
    }

    private static String checkStatus(String status) {
        String s = status.trim().toUpperCase();
        if (!STATUSES.contains(s)) {
            throw ApiException.badRequest("Status must be NEW, SHIPPED or CANCELLED");
        }
        return s;
    }

    private static OrderDetailResponse toDetail(CustomerOrder order) {
        List<OrderLineResponse> lines = order.getItems().stream()
                .map(item -> new OrderLineResponse(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getLineTotal()))
                .toList();
        BigDecimal total = lines.stream()
                .map(OrderLineResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        CustomerAddress address = order.getShippingAddress();
        return new OrderDetailResponse(order.getId(), order.getOrderDate(), order.getDeliveryDate(),
                order.getStatus(), CustomerResponse.from(order.getCustomer()),
                address == null ? null : AddressResponse.from(address),
                lines, total);
    }
}