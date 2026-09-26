package com.roti5dao.order.service;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.order.entity.Order;
import com.roti5dao.order.entity.OrderStatus;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** transition ที่อนุญาตกำหนดไว้ที่เดียว — ตาม state diagram ใน 01-overview */
@Component
public class OrderStatusMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.PREPARING, EnumSet.of(OrderStatus.READY));
        ALLOWED.put(OrderStatus.READY, EnumSet.of(OrderStatus.COMPLETED));
        ALLOWED.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    public boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public void transition(Order order, OrderStatus target, Instant now) {
        if (!canTransition(order.getStatus(), target)) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS,
                    "เปลี่ยนสถานะจาก " + order.getStatus() + " เป็น " + target + " ไม่ได้");
        }
        order.setStatus(target);
        switch (target) {
            case CONFIRMED -> order.setConfirmedAt(now);
            case COMPLETED -> order.setCompletedAt(now);
            case CANCELLED -> order.setCancelledAt(now);
            default -> {
            }
        }
    }
}
