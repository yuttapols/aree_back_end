package com.roti5dao.promotion.repository;

import com.roti5dao.promotion.entity.Promotion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    @Query("""
            select distinct p from Promotion p left join fetch p.targets
            where p.active = true and p.code is null and p.startAt <= :now and p.endAt > :now
            """)
    List<Promotion> findActiveAutoApply(Instant now);

    @Query("select p from Promotion p left join fetch p.targets where upper(p.code) = upper(:code)")
    Optional<Promotion> findByCode(String code);

    @Query("select count(p) > 0 from Promotion p where upper(p.code) = upper(:code) and (:excludeId is null or p.id <> :excludeId)")
    boolean existsByCode(String code, Long excludeId);

    @Query("""
            select p from Promotion p
            where p.active = true and p.showOnLanding = true and p.startAt <= :now and p.endAt > :now
            order by p.priority desc, p.endAt asc
            """)
    List<Promotion> findLanding(Instant now);

    @Query("select p from Promotion p where p.id = :id and p.active = true and p.showOnLanding = true and p.endAt > :now")
    Optional<Promotion> findPublic(Long id, Instant now);

    @Query(value = "select p from Promotion p where (:active is null or p.active = :active)",
            countQuery = "select count(p) from Promotion p where (:active is null or p.active = :active)")
    Page<Promotion> search(Boolean active, Pageable pageable);

    /** กันโควต้าเกินเมื่อใช้พร้อมกัน — 0 แถว = เต็มแล้ว */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Promotion p set p.usedCount = p.usedCount + 1
            where p.id = :id and (p.usageLimit is null or p.usedCount < p.usageLimit)
            """)
    int incrementUsage(Long id);

    @Modifying(flushAutomatically = true)
    @Query("update Promotion p set p.usedCount = p.usedCount - 1 where p.id = :id and p.usedCount > 0")
    int decrementUsage(Long id);

    @Query("""
            select p.discountValue from Promotion p, PromotionUsage u
            where u.promotionId = p.id and u.orderId = :orderId
              and p.type = com.roti5dao.promotion.entity.PromotionEnums.PromotionType.POINT_MULTIPLIER
            """)
    List<java.math.BigDecimal> findMultipliersForOrder(Long orderId);
}
