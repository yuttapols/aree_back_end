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
            "PENDING_PAYMENT, PREPARING, false",
            "CONFIRMED, PREPARING, true",
            "CONFIRMED, CANCELLED, true",
            "CONFIRMED, COMPLETED, false",
            "PREPARING, READY, true",
            "PREPARING, CANCELLED, false",
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
}
