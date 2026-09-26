package com.roti5dao.promotion.service;

import com.roti5dao.order.event.OrderEvents.OrderCancelledEvent;
import com.roti5dao.order.event.OrderEvents.OrderPlacedEvent;
import com.roti5dao.order.pricing.PricingContext;
import com.roti5dao.order.pricing.PricingStep;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** จุดเชื่อมของ module promotion กับ order (Phase 4) */
public final class PromotionHooks {

    private PromotionHooks() {
    }

    @Component
    @Order(PricingStep.PROMOTION)
    public static class PromotionStep implements PricingStep {
        private final PromotionService promotionService;

        public PromotionStep(PromotionService promotionService) {
            this.promotionService = promotionService;
        }

        @Override
        public void apply(PricingContext ctx) {
            promotionService.evaluate(ctx);
        }
    }

    @Component
    public static class PromotionOrderListener {
        private final PromotionService promotionService;

        public PromotionOrderListener(PromotionService promotionService) {
            this.promotionService = promotionService;
        }

        @EventListener
        public void onPlaced(OrderPlacedEvent e) {
            if (!e.appliedPromotions().isEmpty()) {
                promotionService.recordUsage(e);
            }
        }

        @EventListener
        public void onCancelled(OrderCancelledEvent e) {
            promotionService.releaseUsage(e.orderId());
        }
    }
}
