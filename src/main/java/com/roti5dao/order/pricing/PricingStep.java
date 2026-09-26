package com.roti5dao.order.pricing;

/**
 * ขั้นตอนหนึ่งใน pricing pipeline — เรียงลำดับด้วย {@link org.springframework.core.annotation.Order}
 * <ol>
 *   <li>{@code 10} SubtotalStep (order)</li>
 *   <li>{@code 20} PromotionStep (promotion)</li>
 *   <li>{@code 30} PointRedeemStep (point)</li>
 *   <li>{@code 40} PointEarnPreviewStep (point)</li>
 * </ol>
 * เพิ่ม step ใหม่ได้โดยไม่ต้องแก้ OrderService — step ต้องไม่มี side effect (ใช้ทั้ง quote และ create)
 */
public interface PricingStep {

    int SUBTOTAL = 10;
    int PROMOTION = 20;
    int POINT_REDEEM = 30;
    int POINT_EARN_PREVIEW = 40;

    void apply(PricingContext ctx);
}
