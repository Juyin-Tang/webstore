package com.example.webstore.repository;

import com.example.webstore.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    @EntityGraph(attributePaths = {"category", "supplier"})
    @Query("""
            SELECT p FROM Product p
            WHERE (:q IS NULL OR p.name LIKE CONCAT('%', :q, '%'))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
            """)
    Page<Product> search(@Param("q") String q, @Param("categoryId") Integer categoryId, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "supplier"})
    Optional<Product> findWithDetailsById(Integer id);

    boolean existsByCategoryId(Integer categoryId);
}