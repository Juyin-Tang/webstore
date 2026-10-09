package com.example.webstore.repository;

import com.example.webstore.entity.CustomerOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Integer> {

    @EntityGraph(attributePaths = "customer")
    @Query("SELECT o FROM CustomerOrder o WHERE (:status IS NULL OR o.status = :status)")
    Page<CustomerOrder> search(@Param("status") String status, Pageable pageable);

    @Query("""
            SELECT o FROM CustomerOrder o
            JOIN FETCH o.customer
            LEFT JOIN FETCH o.shippingAddress
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product
            WHERE o.id = :id
            """)
    Optional<CustomerOrder> findDetailedById(@Param("id") Integer id);

    boolean existsByCustomerId(Integer customerId);
}