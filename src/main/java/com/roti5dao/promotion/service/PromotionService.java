package com.roti5dao.promotion.service;

import com.roti5dao.common.config.AppProperties;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.util.MoneyUtils;
import com.roti5dao.order.event.OrderEvents.OrderPlacedEvent;
import com.roti5dao.order.pricing.PricingContext;
import com.roti5dao.order.pricing.PricingContext.AppliedPromotion;
import com.roti5dao.promotion.calculator.DiscountCalculator;
import com.roti5dao.promotion.calculator.DiscountCalculator.Result;
import com.roti5dao.promotion.entity.Promotion;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionChannel;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionType;
import com.roti5dao.promotion.entity.PromotionUsage;
import com.roti5dao.promotion.repository.PromotionRepository;
import com.roti5dao.promotion.repository.PromotionUsageRepository;
import com.roti5dao.user.service.CustomerBalanceService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotion engine
 * — 1 ออเดอร์ใช้โปรส่วนลดได้ 1 รายการ (โค้ดที่ใส่ หรือ auto-apply ที่ลดได้มากสุด / priority สูงสุด)
 * — + โปร POINT_MULTIPLIER ซ้อนได้ 1 รายการ (ตัวคูณสูงสุด)
 */
@Service
public class PromotionService {

    private record Candidate(Promotion promotion, Result result) {
    }

    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository usageRepository;
    private final CustomerBalanceService customerLock;
    private final Map<PromotionType, DiscountCalculator> calculators = new EnumMap<>(PromotionType.class);
    private final Clock clock;
    private final ZoneId zone;

    public PromotionService(PromotionRepository promotionRepository, PromotionUsageRepository usageRepository,
                            CustomerBalanceService customerLock, List<DiscountCalculator> calculators, Clock clock,
                            AppProperties props) {
        this.promotionRepository = promotionRepository;
        this.usageRepository = usageRepository;
        this.customerLock = customerLock;
        calculators.forEach(c -> this.calculators.put(c.type(), c));
        this.clock = clock;
        this.zone = props.timezone();
    }

    /** เลือกและคำนวณโปรโมชั่นให้ตะกร้า (ไม่มี side effect) */
    @Transactional(readOnly = true)
    public void evaluate(PricingContext ctx) {
        Instant now = ctx.getNow();
        int dow = now.atZone(zone).getDayOfWeek().getValue();

        Promotion codePromo = null;
        if (ctx.getPromoCode() != null) {
            codePromo = promotionRepository.findByCode(ctx.getPromoCode()).filter(Promotion::isActive)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROMOTION_NOT_FOUND));
            if (now.isBefore(codePromo.getStartAt()) || !now.isBefore(codePromo.getEndAt())) {
                throw new BusinessException(ErrorCode.PROMOTION_EXPIRED);
            }
            if (limitReached(codePromo)) {
                throw new BusinessException(ErrorCode.PROMOTION_LIMIT_REACHED);
            }
            String reason = ineligibleReason(codePromo, ctx, dow);
            if (reason != null) {
                throw new BusinessException(ErrorCode.PROMOTION_NOT_ELIGIBLE, reason);
            }
        }
        List<Promotion> auto = promotionRepository.findActiveAutoApply(now).stream()
                .filter(p -> !limitReached(p) && ineligibleReason(p, ctx, dow) == null)
                .toList();

        // --- โปรส่วนลด (เลือก 1)
        Candidate discount = null;
        if (codePromo != null && codePromo.getType().isDiscount()) {
            Result r = calculate(codePromo, ctx);
            if (r.discount().signum() <= 0) {
                throw new BusinessException(ErrorCode.PROMOTION_NOT_ELIGIBLE, "สินค้าในตะกร้าไม่ตรงเงื่อนไขโปรโมชั่น");
            }
            discount = new Candidate(codePromo, r);
        } else {
            discount = auto.stream().filter(p -> p.getType().isDiscount())
                    .map(p -> new Candidate(p, calculate(p, ctx)))
                    .filter(c -> c.result().discount().signum() > 0)
                    .max(Comparator.<Candidate, BigDecimal>comparing(c -> c.result().discount())
                            .thenComparingInt(c -> c.promotion().getPriority())
                            .thenComparing(c -> -c.promotion().getId()))
                    .orElse(null);
        }

        // --- โปรตัวคูณแต้ม (ซ้อนได้ 1)
        Candidate multiplier = null;
        if (ctx.isMember()) {
            var multipliers = new java.util.ArrayList<>(auto.stream()
                    .filter(p -> p.getType() == PromotionType.POINT_MULTIPLIER).toList());
            if (codePromo != null && codePromo.getType() == PromotionType.POINT_MULTIPLIER) {
                multipliers.add(codePromo);
            }
            multiplier = multipliers.stream()
                    .map(p -> new Candidate(p, calculate(p, ctx)))
                    .filter(c -> c.result().pointMultiplier().compareTo(BigDecimal.ONE) > 0)
                    .max(Comparator.<Candidate, BigDecimal>comparing(c -> c.result().pointMultiplier())
                            .thenComparingInt(c -> c.promotion().getPriority()))
                    .orElse(null);
        }

        if (discount != null) {
            BigDecimal amount = MoneyUtils.min(discount.result().discount(), ctx.getSubtotal());
            ctx.setPromotionDiscount(amount);
            discount.result().freeQuantities().forEach((i, q) -> ctx.getLines().get(i).setFreeQuantity(q));
            ctx.getAppliedPromotions().add(applied(discount.promotion(), amount, BigDecimal.ONE));
        }
        if (multiplier != null) {
            ctx.setPointMultiplier(multiplier.result().pointMultiplier());
            ctx.getAppliedPromotions().add(applied(multiplier.promotion(), MoneyUtils.ZERO, multiplier.result().pointMultiplier()));
        }
    }

    /** บันทึกการใช้โปรตอนสร้างออเดอร์ — เพิ่ม used_count แบบ atomic กันโควต้าเกินเมื่อใช้พร้อมกัน */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUsage(OrderPlacedEvent e) {
        for (AppliedPromotion a : e.appliedPromotions()) {
            Promotion p = promotionRepository.findById(a.promotionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROMOTION_NOT_FOUND));
            if (promotionRepository.incrementUsage(p.getId()) == 0) {
                throw new BusinessException(ErrorCode.PROMOTION_LIMIT_REACHED);
            }
            if (p.getUsagePerCustomer() != null) {
                if (e.customerId() == null) {
                    throw new BusinessException(ErrorCode.PROMOTION_NOT_ELIGIBLE, "โปรโมชั่นนี้สำหรับสมาชิกเท่านั้น");
                }
                customerLock.lock(e.customerId()); // serialize ต่อคน กันใช้เกินสิทธิ์เมื่อสั่งพร้อมกัน
                if (usageRepository.countByPromotionIdAndCustomerId(p.getId(), e.customerId()) >= p.getUsagePerCustomer()) {
                    throw new BusinessException(ErrorCode.PROMOTION_LIMIT_REACHED, "ใช้โปรโมชั่นนี้ครบสิทธิ์แล้ว");
                }
            }
            PromotionUsage u = new PromotionUsage();
            u.setPromotionId(p.getId());
            u.setOrderId(e.orderId());
            u.setCustomerId(e.customerId());
            u.setDiscountAmount(a.discountAmount());
            u.setCreatedAt(Instant.now(clock));
            usageRepository.save(u);
        }
    }

    /** ยกเลิกออเดอร์ → คืนโควต้า */
    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseUsage(Long orderId) {
        for (PromotionUsage u : usageRepository.findByOrderId(orderId)) {
            promotionRepository.decrementUsage(u.getPromotionId());
            usageRepository.delete(u);
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal pointMultiplierForOrder(Long orderId) {
        return promotionRepository.findMultipliersForOrder(orderId).stream()
                .max(Comparator.naturalOrder()).orElse(BigDecimal.ONE);
    }

    private Result calculate(Promotion p, PricingContext ctx) {
        return calculators.get(p.getType()).calculate(p, ctx);
    }

    private static boolean limitReached(Promotion p) {
        return p.getUsageLimit() != null && p.getUsedCount() >= p.getUsageLimit();
    }

    /** null = ผ่านเงื่อนไข */
    private String ineligibleReason(Promotion p, PricingContext ctx, int dow) {
        if (p.getChannel() != PromotionChannel.ALL && !p.getChannel().name().equals(ctx.getChannel().name())) {
            return p.getChannel() == PromotionChannel.ONLINE ? "ใช้ได้เฉพาะสั่งออนไลน์" : "ใช้ได้เฉพาะที่หน้าร้าน";
        }
        if ((p.isMemberOnly() || p.getType() == PromotionType.POINT_MULTIPLIER || p.getUsagePerCustomer() != null)
                && !ctx.isMember()) {
            return "โปรโมชั่นนี้สำหรับสมาชิกเท่านั้น";
        }
        if (p.getDaysOfWeek() != null && p.getDaysOfWeek().length > 0
                && Arrays.stream(p.getDaysOfWeek()).noneMatch(d -> d != null && d == dow)) {
            return "โปรโมชั่นนี้ใช้ไม่ได้ในวันนี้";
        }
        if (ctx.getSubtotal().compareTo(p.getMinOrderAmount()) < 0) {
            return "ยอดสั่งซื้อขั้นต่ำ " + p.getMinOrderAmount().stripTrailingZeros().toPlainString() + " บาท";
        }
        if (p.getUsagePerCustomer() != null
                && usageRepository.countByPromotionIdAndCustomerId(p.getId(), ctx.getCustomerId()) >= p.getUsagePerCustomer()) {
            return "ใช้โปรโมชั่นนี้ครบสิทธิ์แล้ว";
        }
        return null;
    }

    private static AppliedPromotion applied(Promotion p, BigDecimal discount, BigDecimal multiplier) {
        return new AppliedPromotion(p.getId(), p.getCode(), p.getName(), p.getType().name(), discount, multiplier);
    }
}
