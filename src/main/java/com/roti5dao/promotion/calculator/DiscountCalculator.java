package com.roti5dao.promotion.calculator;

import com.roti5dao.order.pricing.PricingContext;
import com.roti5dao.order.pricing.PricingContext.PricedLine;
import com.roti5dao.promotion.entity.Promotion;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionScope;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionType;
import com.roti5dao.promotion.entity.PromotionTarget;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Strategy ต่อประเภทโปรโมชั่น */
public interface DiscountCalculator {

    PromotionType type();

    /** @param freeQuantities index ของบรรทัด → จำนวนชิ้นที่ได้ฟรี (เฉพาะ BUY_X_GET_Y) */
    record Result(BigDecimal discount, BigDecimal pointMultiplier, Map<Integer, Integer> freeQuantities) {
    }

    Result calculate(Promotion promotion, PricingContext ctx);

    /** บรรทัดที่อยู่ในขอบเขตโปร (ORDER = ทุกบรรทัด) — คืน index */
    static List<Integer> eligibleLines(Promotion p, List<PricedLine> lines) {
        if (p.getScope() == PromotionScope.ORDER) {
            return java.util.stream.IntStream.range(0, lines.size()).boxed().toList();
        }
        Set<Long> ids = p.getTargets().stream()
                .map(p.getScope() == PromotionScope.PRODUCT ? PromotionTarget::getProductId : PromotionTarget::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return java.util.stream.IntStream.range(0, lines.size())
                .filter(i -> ids.contains(p.getScope() == PromotionScope.PRODUCT
                        ? lines.get(i).getProductId() : lines.get(i).getCategoryId()))
                .boxed().toList();
    }

    static BigDecimal eligibleAmount(Promotion p, List<PricedLine> lines) {
        return eligibleLines(p, lines).stream().map(i -> lines.get(i).lineTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
