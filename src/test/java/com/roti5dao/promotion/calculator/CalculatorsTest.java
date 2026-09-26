package com.roti5dao.promotion.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import com.roti5dao.order.entity.OrderChannel;
import com.roti5dao.order.pricing.PricingContext;
import com.roti5dao.order.pricing.PricingContext.PricedLine;
import com.roti5dao.order.pricing.PricingContext.PricedOption;
import com.roti5dao.promotion.entity.Promotion;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionScope;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionType;
import com.roti5dao.promotion.entity.PromotionTarget;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalculatorsTest {

    private static PricingContext ctx(PricedLine... lines) {
        PricingContext c = new PricingContext(OrderChannel.ONLINE, null, null, 0, Instant.now(), List.of());
        c.setLines(List.of(lines));
        return c;
    }

    private static PricedLine line(long productId, long categoryId, String price, int qty, String... options) {
        List<PricedOption> opts = java.util.Arrays.stream(options)
                .map(o -> new PricedOption(1L, "g", "o", new BigDecimal(o))).toList();
        return new PricedLine(productId, categoryId, "p" + productId, new BigDecimal(price), opts, qty, null);
    }

    private static Promotion promo(PromotionType type, String value) {
        Promotion p = new Promotion();
        p.setType(type);
        p.setDiscountValue(new BigDecimal(value));
        p.setScope(PromotionScope.ORDER);
        return p;
    }

    @Test
    void percentWithCap() {
        Promotion p = promo(PromotionType.PERCENT, "15");
        var c = ctx(line(1, 1, "33.33", 3));
        assertThat(new Calculators.PercentCalculator().calculate(p, c).discount()).isEqualByComparingTo("14.99");
        p.setMaxDiscount(new BigDecimal("10"));
        assertThat(new Calculators.PercentCalculator().calculate(p, c).discount()).isEqualByComparingTo("10.00");
    }

    @Test
    void fixedAmountNeverExceedsEligibleAmount() {
        Promotion p = promo(PromotionType.FIXED_AMOUNT, "100");
        assertThat(new Calculators.FixedAmountCalculator().calculate(p, ctx(line(1, 1, "30", 1))).discount())
                .isEqualByComparingTo("30");
    }

    @Test
    void buyXGetYGivesCheapestUnitsFreeAcrossLines() {
        Promotion p = promo(PromotionType.BUY_X_GET_Y, "0");
        p.setBuyQty(2);
        p.setGetQty(1);
        p.setScope(PromotionScope.CATEGORY);
        PromotionTarget t = new PromotionTarget();
        t.setCategoryId(10L);
        p.getTargets().add(t);

        // 2 x 45 + 2 x (30 + 10 ไข่) + สินค้าหมวดอื่น → 4 ชิ้นเข้าเงื่อนไข → ฟรี 1 ชิ้นที่ถูกสุด (40)
        var c = ctx(line(1, 10, "45", 2), line(2, 10, "30", 2, "10"), line(3, 99, "5", 5));
        var r = new Calculators.BuyXGetYCalculator().calculate(p, c);
        assertThat(r.discount()).isEqualByComparingTo("40");
        assertThat(r.freeQuantities()).containsEntry(1, 1).doesNotContainKey(2);
    }

    @Test
    void pointMultiplierHasNoDiscount() {
        var r = new Calculators.PointMultiplierCalculator().calculate(promo(PromotionType.POINT_MULTIPLIER, "2"), ctx(line(1, 1, "10", 1)));
        assertThat(r.discount()).isZero();
        assertThat(r.pointMultiplier()).isEqualByComparingTo("2");
    }
}
