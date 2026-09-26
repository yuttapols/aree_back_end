package com.roti5dao.order.pricing;

import com.roti5dao.common.util.MoneyUtils;
import com.roti5dao.order.entity.OrderChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * สถานะระหว่างคำนวณราคา — แต่ละ {@link PricingStep} อ่าน/เขียนค่าต่อกันตามลำดับ
 * subtotal → ส่วนลดโปรโมชั่น → ส่วนลดจากแต้ม → total → แต้มที่จะได้
 */
@Getter
@Setter
public class PricingContext {

    public record CartLine(Long productId, int quantity, List<Long> optionItemIds, String note) {
    }

    public record PricedOption(Long optionItemId, String groupName, String name, BigDecimal extraPrice) {
    }

    @Getter
    @Setter
    public static class PricedLine {
        private final Long productId;
        private final Long categoryId;
        private final String productName;
        private final BigDecimal unitPrice;
        private final List<PricedOption> options;
        private final int quantity;
        private final String note;
        private int freeQuantity;

        public PricedLine(Long productId, Long categoryId, String productName, BigDecimal unitPrice,
                          List<PricedOption> options, int quantity, String note) {
            this.productId = productId;
            this.categoryId = categoryId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.options = options;
            this.quantity = quantity;
            this.note = note;
        }

        public BigDecimal optionsPrice() {
            return options.stream().map(PricedOption::extraPrice).reduce(MoneyUtils.ZERO, BigDecimal::add);
        }

        /** ราคาต่อชิ้นรวมตัวเลือก */
        public BigDecimal unitTotal() {
            return unitPrice.add(optionsPrice());
        }

        public BigDecimal lineTotal() {
            return MoneyUtils.scale(unitTotal().multiply(BigDecimal.valueOf(quantity)));
        }
    }

    public record AppliedPromotion(Long promotionId, String code, String name, String type, BigDecimal discountAmount,
                                   BigDecimal pointMultiplier) {
    }

    private final OrderChannel channel;
    private final Long customerId;
    private final String promoCode;
    private final int requestedRedeemPoints;
    private final Instant now;
    private final List<CartLine> cart;

    private List<PricedLine> lines = new ArrayList<>();
    private BigDecimal subtotal = MoneyUtils.ZERO;
    private BigDecimal promotionDiscount = MoneyUtils.ZERO;
    private List<AppliedPromotion> appliedPromotions = new ArrayList<>();
    private BigDecimal pointMultiplier = BigDecimal.ONE;
    private int pointsRedeemed;
    private BigDecimal pointDiscount = MoneyUtils.ZERO;
    private int pointsToEarn;

    public PricingContext(OrderChannel channel, Long customerId, String promoCode, int requestedRedeemPoints,
                          Instant now, List<CartLine> cart) {
        this.channel = channel;
        this.customerId = customerId;
        this.promoCode = promoCode == null || promoCode.isBlank() ? null : promoCode.trim();
        this.requestedRedeemPoints = Math.max(0, requestedRedeemPoints);
        this.now = now;
        this.cart = List.copyOf(cart);
    }

    public boolean isMember() {
        return customerId != null;
    }

    /** ยอดหลังหักส่วนลดโปรโมชั่น (ก่อนหักแต้ม) */
    public BigDecimal afterPromotion() {
        return MoneyUtils.max(MoneyUtils.ZERO, subtotal.subtract(promotionDiscount));
    }

    public BigDecimal total() {
        return MoneyUtils.max(MoneyUtils.ZERO, subtotal.subtract(promotionDiscount).subtract(pointDiscount));
    }
}
