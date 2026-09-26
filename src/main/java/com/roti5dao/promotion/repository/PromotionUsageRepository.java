package com.roti5dao.promotion.repository;

import com.roti5dao.promotion.entity.PromotionUsage;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, Long> {

    long countByPromotionIdAndCustomerId(Long promotionId, Long customerId);

    List<PromotionUsage> findByOrderId(Long orderId);

    Page<PromotionUsage> findByPromotionId(Long promotionId, Pageable pageable);
}
