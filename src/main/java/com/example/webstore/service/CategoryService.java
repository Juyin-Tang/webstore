package com.example.webstore.service;

import com.example.webstore.dto.CatalogDtos.CategoryRequest;
import com.example.webstore.dto.CatalogDtos.CategoryResponse;
import com.example.webstore.entity.ProductCategory;
import com.example.webstore.exception.ApiException;
import com.example.webstore.repository.ProductCategoryRepository;
import com.example.webstore.repository.ProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(ProductCategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll(Sort.by("id")).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse findById(Integer id) {
        return CategoryResponse.from(getCategory(id));
    }

    public CategoryResponse create(CategoryRequest request) {
        ProductCategory category = new ProductCategory();
        category.setName(request.name());
        category.setDescription(request.description());
        return CategoryResponse.from(categoryRepository.save(category));
    }

    public CategoryResponse update(Integer id, CategoryRequest request) {
        ProductCategory category = getCategory(id);
        category.setName(request.name());
        category.setDescription(request.description());
        return CategoryResponse.from(category);
    }

    public void delete(Integer id) {
        ProductCategory category = getCategory(id);
        if (productRepository.existsByCategoryId(id)) {
            throw ApiException.conflict("Category " + id + " still has products and cannot be deleted");
        }
        categoryRepository.delete(category);
    }

    private ProductCategory getCategory(Integer id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category", id));
    }
}