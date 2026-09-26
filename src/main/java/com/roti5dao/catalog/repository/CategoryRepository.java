package com.roti5dao.catalog.repository;

import com.roti5dao.catalog.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<Category> findAllByOrderBySortOrderAscIdAsc();

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
