package com.roti5dao.order.event;

import com.roti5dao.order.pricing.PricingContext.AppliedPromotion;
import java.math.BigDecimal;
import java.util.List;

/**
 * Event ของออเดอร์ — publish แบบ synchronous ภายใน transaction เดียวกับการเปลี่ยนสถานะ
 * listener (point/promotion) ที่ throw exception จะทำให้ทั้งรายการ rollback
 */
public final class OrderEvents {

    private OrderEvents() {
    }

    /** สร้างออเดอร์แล้ว — ใช้บันทึกการใช้โปร / ตัดแต้มที่แลก */
    public record OrderPlacedEvent(Long orderId, Long customerId, List<AppliedPromotion> appliedPromotions,
                                   int pointsRedeemed) {
    }

    public record OrderCompletedEvent(Long orderId, Long customerId, BigDecimal totalAmount) {
    }

    public record OrderCancelledEvent(Long orderId, Long customerId) {
    }
}
