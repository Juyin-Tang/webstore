package com.example.webstore.repository;

import com.example.webstore.entity.OrderItem;
import com.example.webstore.entity.OrderItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, OrderItemId> {

    boolean existsByProductId(Integer productId);
}