package com.roti5dao.point.service;

import com.roti5dao.order.pricing.PricingContext;
import com.roti5dao.order.pricing.PricingStep;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Pricing steps ของ module point (Phase 4) — เสียบเข้า pipeline โดยไม่ต้องแก้ OrderService */
public final class PointPricingSteps {

    private PointPricingSteps() {
    }

    /** แลกแต้มเป็นส่วนลด (ตรวจขั้นต่ำ / ยอดคงเหลือ / เพดาน % ของยอด) */
    @Component
    @Order(PricingStep.POINT_REDEEM)
    public static class PointRedeemStep implements PricingStep {
        private final PointService pointService;

        public PointRedeemStep(PointService pointService) {
            this.pointService = pointService;
        }

        @Override
        public void apply(PricingContext ctx) {
            if (ctx.getRequestedRedeemPoints() <= 0) {
                return;
            }
            var preview = pointService.previewRedeem(ctx.getCustomerId(), ctx.getRequestedRedeemPoints(), ctx.afterPromotion());
            ctx.setPointsRedeemed(preview.pointsUsed());
            ctx.setPointDiscount(preview.discount());
        }
    }

    /** แสดงแต้มที่จะได้ (รวมตัวคูณจากโปร POINT_MULTIPLIER) — ได้จริงเมื่อออเดอร์ COMPLETED */
    @Component
    @Order(PricingStep.POINT_EARN_PREVIEW)
    public static class PointEarnPreviewStep implements PricingStep {
        private final PointService pointService;

        public PointEarnPreviewStep(PointService pointService) {
            this.pointService = pointService;
        }

        @Override
        public void apply(PricingContext ctx) {
            ctx.setPointsToEarn(ctx.isMember() ? pointService.calculateEarn(ctx.total(), ctx.getPointMultiplier()) : 0);
        }
    }
}
