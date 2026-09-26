package com.roti5dao.catalog.service;

import com.roti5dao.catalog.dto.CatalogDtos.CategoryResponse;
import com.roti5dao.catalog.dto.CatalogDtos.CategoryUpsertRequest;
import com.roti5dao.catalog.dto.CatalogDtos.SortItem;
import com.roti5dao.catalog.entity.Category;
import com.roti5dao.catalog.repository.CategoryRepository;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryAdminService {

    private final CategoryRepository repository;

    public CategoryAdminService(CategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return repository.findAllByOrderBySortOrderAscIdAsc().stream().map(CategoryResponse::of).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryUpsertRequest req) {
        if (repository.existsBySlug(req.slug())) {
            throw new BusinessException(ErrorCode.DUPLICATE_VALUE, "slug นี้ถูกใช้แล้ว");
        }
        Category c = new Category();
        apply(c, req);
        return CategoryResponse.of(repository.saveAndFlush(c));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryUpsertRequest req) {
        Category c = get(id);
        if (repository.existsBySlugAndIdNot(req.slug(), id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_VALUE, "slug นี้ถูกใช้แล้ว");
        }
        apply(c, req);
        return CategoryResponse.of(repository.saveAndFlush(c));
    }

    /** soft delete — ออเดอร์เก่ายังอ้างอิงสินค้าในหมวดนี้ได้ */
    @Transactional
    public void delete(Long id) {
        get(id).setActive(false);
    }

    @Transactional
    public List<CategoryResponse> sort(List<SortItem> items) {
        Map<Long, Category> byId = repository.findAllById(items.stream().map(SortItem::id).toList()).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        for (SortItem item : items) {
            Category c = byId.get(item.id());
            if (c == null) {
                throw new NotFoundException("หมวดหมู่ id=" + item.id());
            }
            c.setSortOrder(item.sortOrder());
        }
        repository.flush();
        return list();
    }

    private Category get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("หมวดหมู่"));
    }

    private static void apply(Category c, CategoryUpsertRequest req) {
        c.setName(req.name().trim());
        c.setSlug(req.slug());
        c.setDescription(req.description());
        c.setIcon(req.icon());
        c.setSortOrder(req.sortOrder());
        c.setActive(req.active());
    }
}
