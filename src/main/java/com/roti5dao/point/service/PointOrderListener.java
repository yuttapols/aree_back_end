package com.roti5dao.point.service;

import com.roti5dao.order.event.OrderEvents.OrderCancelledEvent;
import com.roti5dao.order.event.OrderEvents.OrderCompletedEvent;
import com.roti5dao.order.event.OrderEvents.OrderPlacedEvent;
import com.roti5dao.order.service.OrderPointsRecorder;
import com.roti5dao.promotion.service.PromotionService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** ฟัง event ของออเดอร์ (synchronous, ใน transaction เดียวกัน) */
@Component
public class PointOrderListener {

    private final PointService pointService;
    private final PromotionService promotionService;
    private final OrderPointsRecorder orderPointsRecorder;

    public PointOrderListener(PointService pointService, PromotionService promotionService,
                              OrderPointsRecorder orderPointsRecorder) {
        this.pointService = pointService;
        this.promotionService = promotionService;
        this.orderPointsRecorder = orderPointsRecorder;
    }

    @EventListener
    public void onPlaced(OrderPlacedEvent e) {
        if (e.customerId() != null && e.pointsRedeemed() > 0) {
            pointService.redeem(e.customerId(), e.orderId(), e.pointsRedeemed());
        }
    }

    @EventListener
    public void onCompleted(OrderCompletedEvent e) {
        if (e.customerId() == null) {
            return;
        }
        int points = pointService.earn(e.customerId(), e.orderId(), e.totalAmount(),
                promotionService.pointMultiplierForOrder(e.orderId()));
        orderPointsRecorder.recordPointsEarned(e.orderId(), points);
    }

    @EventListener
    public void onCancelled(OrderCancelledEvent e) {
        if (e.customerId() != null) {
            pointService.reverse(e.customerId(), e.orderId());
        }
    }
}
