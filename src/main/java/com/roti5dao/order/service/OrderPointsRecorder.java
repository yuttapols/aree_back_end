package com.roti5dao.order.service;

import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** ให้ module point บันทึกแต้มที่ได้ลงออเดอร์ (orders.points_earned) โดยไม่ต้องเข้าถึง OrderRepository */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OrderPointsRecorder {

    private final OrderRepository orderRepository;

    public OrderPointsRecorder(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public void recordPointsEarned(Long orderId, int points) {
        orderRepository.findById(orderId).orElseThrow(() -> new NotFoundException("ออเดอร์")).setPointsEarned(points);
    }
}
