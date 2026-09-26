package com.roti5dao.promotion.service;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.storage.ImageUrlValidator;
import com.roti5dao.common.util.MoneyUtils;
import com.roti5dao.common.web.PageResponse;
import com.roti5dao.promotion.dto.PromotionDtos.AdminPromotion;
import com.roti5dao.promotion.dto.PromotionDtos.PromotionUpsertRequest;
import com.roti5dao.promotion.dto.PromotionDtos.PublicPromotion;
import com.roti5dao.promotion.dto.PromotionDtos.UsageResponse;
import com.roti5dao.promotion.entity.Promotion;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionScope;
import com.roti5dao.promotion.entity.PromotionTarget;
import com.roti5dao.promotion.repository.PromotionRepository;
import com.roti5dao.promotion.repository.PromotionUsageRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionAdminService {

    private final PromotionRepository repository;
    private final PromotionUsageRepository usageRepository;
    private final Clock clock;

    public PromotionAdminService(PromotionRepository repository, PromotionUsageRepository usageRepository, Clock clock) {
        this.repository = repository;
        this.usageRepository = usageRepository;
        this.clock = clock;
    }

    // ---------- public

    @Transactional(readOnly = true)
    public List<PublicPromotion> landing() {
        return repository.findLanding(Instant.now(clock)).stream().map(PublicPromotion::of).toList();
    }

    @Transactional(readOnly = true)
    public PublicPromotion publicDetail(Long id) {
        return repository.findPublic(id, Instant.now(clock)).map(PublicPromotion::of)
                .orElseThrow(() -> new NotFoundException("โปรโมชั่น"));
    }

    // ---------- admin

    @Transactional(readOnly = true)
    public PageResponse<AdminPromotion> search(Boolean active, Pageable pageable) {
        return PageResponse.of(repository.search(active, pageable), AdminPromotion::of);
    }

    @Transactional(readOnly = true)
    public AdminPromotion get(Long id) {
        return AdminPromotion.of(promotion(id));
    }

    @Transactional
    public AdminPromotion create(PromotionUpsertRequest req) {
        Promotion p = new Promotion();
        apply(p, req);
        return AdminPromotion.of(repository.saveAndFlush(p));
    }

    @Transactional
    public AdminPromotion update(Long id, PromotionUpsertRequest req) {
        Promotion p = promotion(id);
        if (req.usageLimit() != null && req.usageLimit() < p.getUsedCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "จำนวนสิทธิ์ต้องไม่น้อยกว่าที่ใช้ไปแล้ว (" + p.getUsedCount() + ")");
        }
        apply(p, req);
        return AdminPromotion.of(repository.saveAndFlush(p));
    }

    /** soft delete */
    @Transactional
    public void delete(Long id) {
        promotion(id).setActive(false);
    }

    @Transactional(readOnly = true)
    public PageResponse<UsageResponse> usages(Long id, Pageable pageable) {
        promotion(id);
        return PageResponse.of(usageRepository.findByPromotionId(id, pageable), UsageResponse::of);
    }

    private Promotion promotion(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("โปรโมชั่น"));
    }

    private void apply(Promotion p, PromotionUpsertRequest req) {
        String code = req.code() == null || req.code().isBlank() ? null : req.code().trim().toUpperCase(Locale.ROOT);
        if (code != null && repository.existsByCode(code, p.getId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_VALUE, "โค้ดโปรโมชั่นซ้ำ");
        }
        if (!req.endAt().isAfter(req.startAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "วันสิ้นสุดต้องหลังวันเริ่ม");
        }
        BigDecimal value = MoneyUtils.scale(req.discountValue());
        switch (req.type()) {
            case PERCENT -> require(value.signum() > 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0, "ส่วนลด % ต้องอยู่ระหว่าง 0–100");
            case FIXED_AMOUNT -> require(value.signum() > 0, "ส่วนลดต้องมากกว่า 0");
            case BUY_X_GET_Y -> require(req.buyQty() != null && req.getQty() != null, "ต้องระบุจำนวนซื้อและแถม");
            case POINT_MULTIPLIER -> require(value.compareTo(BigDecimal.ONE) > 0 && value.compareTo(BigDecimal.TEN) <= 0,
                    "ตัวคูณแต้มต้องมากกว่า 1 และไม่เกิน 10");
        }
        List<Long> productIds = req.productIds() == null ? List.of() : req.productIds().stream().distinct().toList();
        List<Long> categoryIds = req.categoryIds() == null ? List.of() : req.categoryIds().stream().distinct().toList();
        switch (req.scope()) {
            case ORDER -> require(productIds.isEmpty() && categoryIds.isEmpty(), "โปรทั้งออเดอร์ไม่ต้องระบุสินค้า/หมวด");
            case PRODUCT -> require(!productIds.isEmpty() && categoryIds.isEmpty(), "ต้องระบุสินค้าที่ร่วมรายการ");
            case CATEGORY -> require(!categoryIds.isEmpty() && productIds.isEmpty(), "ต้องระบุหมวดที่ร่วมรายการ");
        }
        List<Integer> days = req.daysOfWeek() == null || req.daysOfWeek().isEmpty() ? null
                : req.daysOfWeek().stream().distinct().sorted().toList();

        p.setCode(code);
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setBannerUrl(ImageUrlValidator.requireStoredOrNull(req.bannerUrl()));
        p.setType(req.type());
        p.setDiscountValue(value);
        p.setMaxDiscount(req.type() == com.roti5dao.promotion.entity.PromotionEnums.PromotionType.PERCENT ? req.maxDiscount() : null);
        boolean bxgy = req.type() == com.roti5dao.promotion.entity.PromotionEnums.PromotionType.BUY_X_GET_Y;
        p.setBuyQty(bxgy ? req.buyQty() : null);
        p.setGetQty(bxgy ? req.getQty() : null);
        p.setMinOrderAmount(MoneyUtils.scale(req.minOrderAmount()));
        p.setScope(req.scope());
        p.setMemberOnly(req.memberOnly());
        p.setChannel(req.channel());
        p.setDaysOfWeek(days == null ? null : days.stream().map(Integer::shortValue).toArray(Short[]::new));
        p.setStartAt(req.startAt());
        p.setEndAt(req.endAt());
        p.setUsageLimit(req.usageLimit());
        p.setUsagePerCustomer(req.usagePerCustomer());
        p.setShowOnLanding(req.showOnLanding());
        p.setPriority(req.priority());
        p.setActive(req.active());

        p.getTargets().clear();
        for (Long productId : req.scope() == PromotionScope.PRODUCT ? productIds : List.<Long>of()) {
            p.getTargets().add(target(p, productId, null));
        }
        for (Long categoryId : req.scope() == PromotionScope.CATEGORY ? categoryIds : List.<Long>of()) {
            p.getTargets().add(target(p, null, categoryId));
        }
    }

    private static PromotionTarget target(Promotion p, Long productId, Long categoryId) {
        PromotionTarget t = new PromotionTarget();
        t.setPromotion(p);
        t.setProductId(productId);
        t.setCategoryId(categoryId);
        return t;
    }

    private static void require(boolean ok, String message) {
        if (!ok) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }
}
