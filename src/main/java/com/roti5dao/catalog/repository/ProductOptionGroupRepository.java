package com.roti5dao.catalog.repository;

import com.roti5dao.catalog.entity.ProductOptionGroup;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductOptionGroupRepository extends JpaRepository<ProductOptionGroup, ProductOptionGroup.Key> {

    /** option group ที่ active ของสินค้าหลายตัว (โหลด items มาพร้อมกัน) */
    @Query("""
            select distinct m from ProductOptionGroup m
            join fetch m.optionGroup g left join fetch g.items
            where m.id.productId in :productIds and g.active = true
            order by m.sortOrder, g.id
            """)
    List<ProductOptionGroup> findActiveByProductIds(Collection<Long> productIds);

    @Query("select m from ProductOptionGroup m join fetch m.optionGroup where m.id.productId = :productId order by m.sortOrder")
    List<ProductOptionGroup> findByProductId(Long productId);

    @Query("select distinct m.id.productId from ProductOptionGroup m where m.id.productId in :productIds and m.optionGroup.active = true")
    List<Long> findProductIdsHavingActiveOptions(Collection<Long> productIds);

    @Modifying
    @Query("delete from ProductOptionGroup m where m.id.productId = :productId")
    void deleteByProductId(Long productId);
}
