package com.roti5dao.promotion.calculator;

import com.roti5dao.common.util.MoneyUtils;
import com.roti5dao.order.pricing.PricingContext;
import com.roti5dao.order.pricing.PricingContext.PricedLine;
import com.roti5dao.promotion.entity.Promotion;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

public final class Calculators {

    private Calculators() {
    }

    /** ลด x% ของยอดที่เข้าเงื่อนไข (มีเพดานได้) */
    @Component
    public static class PercentCalculator implements DiscountCalculator {
        @Override
        public PromotionType type() {
            return PromotionType.PERCENT;
        }

        @Override
        public Result calculate(Promotion p, PricingContext ctx) {
            BigDecimal base = DiscountCalculator.eligibleAmount(p, ctx.getLines());
            BigDecimal discount = base.multiply(p.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
            if (p.getMaxDiscount() != null) {
                discount = MoneyUtils.min(discount, p.getMaxDiscount());
            }
            return new Result(MoneyUtils.floor(discount), BigDecimal.ONE, Map.of());
        }
    }

    /** ลดเป็นจำนวนเงิน (ไม่เกินยอดที่เข้าเงื่อนไข) */
    @Component
    public static class FixedAmountCalculator implements DiscountCalculator {
        @Override
        public PromotionType type() {
            return PromotionType.FIXED_AMOUNT;
        }

        @Override
        public Result calculate(Promotion p, PricingContext ctx) {
            BigDecimal base = DiscountCalculator.eligibleAmount(p, ctx.getLines());
            return new Result(MoneyUtils.floor(MoneyUtils.min(p.getDiscountValue(), base)), BigDecimal.ONE, Map.of());
        }
    }

    /** ซื้อ X แถม Y — ทุก (X+Y) ชิ้นที่เข้าเงื่อนไข ได้ Y ชิ้นที่ถูกที่สุดฟรี */
    @Component
    public static class BuyXGetYCalculator implements DiscountCalculator {
        @Override
        public PromotionType type() {
            return PromotionType.BUY_X_GET_Y;
        }

        @Override
        public Result calculate(Promotion p, PricingContext ctx) {
            List<PricedLine> lines = ctx.getLines();
            List<Integer> eligible = DiscountCalculator.eligibleLines(p, lines);
            record Unit(int lineIndex, BigDecimal price) {
            }
            List<Unit> units = new ArrayList<>();
            for (int i : eligible) {
                for (int q = 0; q < lines.get(i).getQuantity(); q++) {
                    units.add(new Unit(i, lines.get(i).unitTotal()));
                }
            }
            int group = p.getBuyQty() + p.getGetQty();
            int freeCount = (units.size() / group) * p.getGetQty();
            if (freeCount == 0) {
                return new Result(MoneyUtils.ZERO, BigDecimal.ONE, Map.of());
            }
            units.sort(Comparator.comparing(Unit::price));
            BigDecimal discount = MoneyUtils.ZERO;
            Map<Integer, Integer> free = new HashMap<>();
            for (int k = 0; k < freeCount; k++) {
                Unit u = units.get(k);
                discount = discount.add(u.price());
                free.merge(u.lineIndex(), 1, Integer::sum);
            }
            return new Result(MoneyUtils.scale(discount), BigDecimal.ONE, free);
        }
    }

    /** แต้ม x N — ไม่ลดราคา ซ้อนกับโปรส่วนลดได้ */
    @Component
    public static class PointMultiplierCalculator implements DiscountCalculator {
        @Override
        public PromotionType type() {
            return PromotionType.POINT_MULTIPLIER;
        }

        @Override
        public Result calculate(Promotion p, PricingContext ctx) {
            return new Result(MoneyUtils.ZERO, p.getDiscountValue(), Map.of());
        }
    }
}
