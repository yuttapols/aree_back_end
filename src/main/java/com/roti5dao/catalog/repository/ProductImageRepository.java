package com.roti5dao.catalog.repository;

import com.roti5dao.catalog.entity.ProductImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

    long countByProductId(Long productId);
}
