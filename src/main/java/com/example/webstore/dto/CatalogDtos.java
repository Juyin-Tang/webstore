package com.example.webstore.dto;

import com.example.webstore.entity.Product;
import com.example.webstore.entity.ProductCategory;
import com.example.webstore.entity.Supplier;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 100) String name,
            String description) {
    }

    public record CategoryResponse(Integer id, String name, String description) {

        public static CategoryResponse from(ProductCategory c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription());
        }
    }

    public record ProductRequest(
            @NotBlank @Size(max = 255) String name,
            String description,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
            @NotNull @Min(0) Integer stockQuantity,
            Integer categoryId,
            Integer supplierId) {
    }

    public record ProductResponse(Integer id, String name, String description, BigDecimal price,
                                  Integer stockQuantity, Integer categoryId, String categoryName,
                                  Integer supplierId, String supplierName) {

        public static ProductResponse from(Product p) {
            ProductCategory c = p.getCategory();
            Supplier s = p.getSupplier();
            return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(),
                    p.getStockQuantity(),
                    c == null ? null : c.getId(), c == null ? null : c.getName(),
                    s == null ? null : s.getId(), s == null ? null : s.getName());
        }
    }

    public record ProductVersionResponse(String name, BigDecimal price,
                                         LocalDateTime validFrom, LocalDateTime validTo) {
    }

    public record ProductSalesResponse(Integer productId, String productName, String categoryName,
                                       long unitsSold, BigDecimal revenue) {
    }
}