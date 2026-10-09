package com.example.webstore.service;

import com.example.webstore.dto.CatalogDtos.ProductRequest;
import com.example.webstore.dto.CatalogDtos.ProductResponse;
import com.example.webstore.dto.CatalogDtos.ProductSalesResponse;
import com.example.webstore.dto.CatalogDtos.ProductVersionResponse;
import com.example.webstore.dto.PageResponse;
import com.example.webstore.entity.Product;
import com.example.webstore.exception.ApiException;
import com.example.webstore.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReportQueries reportQueries;

    public ProductService(ProductRepository productRepository,
                          ProductCategoryRepository categoryRepository,
                          SupplierRepository supplierRepository,
                          OrderItemRepository orderItemRepository,
                          ReportQueries reportQueries) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.orderItemRepository = orderItemRepository;
        this.reportQueries = reportQueries;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String q, Integer categoryId, int page, int size) {
        String text = (q == null || q.isBlank()) ? null : q.trim();
        return PageResponse.from(
                productRepository.search(text, categoryId, PageResponse.request(page, size, Sort.by("id"))),
                ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Integer id) {
        return ProductResponse.from(getProduct(id));
    }

    public ProductResponse create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        return ProductResponse.from(productRepository.save(product));
    }

    public ProductResponse update(Integer id, ProductRequest request) {
        Product product = getProduct(id);
        apply(product, request);
        return ProductResponse.from(product);
    }

    public void delete(Integer id) {
        Product product = getProduct(id);
        if (orderItemRepository.existsByProductId(id)) {
            throw ApiException.conflict("Product " + id + " appears in orders and cannot be deleted");
        }
        productRepository.delete(product);
    }

    @Transactional(readOnly = true)
    public List<ProductVersionResponse> history(Integer id) {
        List<ProductVersionResponse> versions = reportQueries.productHistory(id);
        if (versions.isEmpty()) {
            throw ApiException.notFound("Product", id);
        }
        return versions;
    }

    @Transactional(readOnly = true)
    public List<ProductSalesResponse> topSellers(int limit) {
        if (limit < 1 || limit > 100) {
            throw ApiException.badRequest("limit must be between 1 and 100");
        }
        return reportQueries.topProducts(limit);
    }

    private Product getProduct(Integer id) {
        return productRepository.findWithDetailsById(id)
                .orElseThrow(() -> ApiException.notFound("Product", id));
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());

        if (request.categoryId() == null) {
            product.setCategory(null);
        } else {
            product.setCategory(categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> ApiException.badRequest("Category " + request.categoryId() + " does not exist")));
        }

        if (request.supplierId() == null) {
            product.setSupplier(null);
        } else {
            product.setSupplier(supplierRepository.findById(request.supplierId())
                    .orElseThrow(() -> ApiException.badRequest("Supplier " + request.supplierId() + " does not exist")));
        }
    }
}