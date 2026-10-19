package com.roti5dao.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.order.entity.Order;
import com.roti5dao.order.entity.OrderStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OrderStatusMachineTest {

    private final OrderStatusMachine machine = new OrderStatusMachine();

    @ParameterizedTest
    @CsvSource({
            "PENDING_PAYMENT, CONFIRMED, true",
            "PENDING_PAYMENT, CANCELLED, true",
            "PENDING_PAYMENT, PREPARING, true",
            "CONFIRMED, PREPARING, true",
            "CONFIRMED, CANCELLED, true",
            "CONFIRMED, COMPLETED, false",
            "PREPARING, READY, true",
            "PREPARING, CANCELLED, true",
            "PREPARING, PENDING_PAYMENT, true",
            "PREPARING, CONFIRMED, true",
            "READY, CANCELLED, false",
            "READY, PREPARING, false",
            "READY, COMPLETED, true",
            "COMPLETED, CANCELLED, false",
            "CANCELLED, CONFIRMED, false"})
    void transitions(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(machine.canTransition(from, to)).isEqualTo(allowed);
    }

    @Test
    void setsTimestamps() {
        Order o = new Order();
        Instant now = Instant.parse("2026-09-23T05:00:00Z");
        machine.transition(o, OrderStatus.CONFIRMED, now);
        assertThat(o.getConfirmedAt()).isEqualTo(now);
        assertThatThrownBy(() -> machine.transition(o, OrderStatus.COMPLETED, now)).isInstanceOf(BusinessException.class);
    }

    @Test
    void kitchenTimestampsAndRequeue() {
        Order o = new Order();
        Instant t1 = Instant.parse("2026-09-23T05:00:00Z");
        Instant t2 = t1.plusSeconds(300);
        machine.transition(o, OrderStatus.PREPARING, t1);
        assertThat(o.getPreparingAt()).isEqualTo(t1);
        machine.transition(o, OrderStatus.PENDING_PAYMENT, t2); // สลับคิว
        assertThat(o.getPreparingAt()).isNull();
        machine.transition(o, OrderStatus.PREPARING, t2);
        machine.transition(o, OrderStatus.READY, t2);
        assertThat(o.getPreparingAt()).isEqualTo(t2);
        assertThat(o.getReadyAt()).isEqualTo(t2);
    }
}
