package com.roti5dao.order.service;

import com.roti5dao.common.audit.SecurityAuditService;
import com.roti5dao.common.audit.SecurityAuditService.Event;
import com.roti5dao.common.config.AppProperties;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.util.MoneyUtils;
import com.roti5dao.order.dto.OrderDtos.OrderResponse;
import com.roti5dao.order.dto.OrderDtos.StaffPaymentRequest;
import com.roti5dao.order.dto.OrderDtos.TrackResponse;
import com.roti5dao.order.entity.Order;
import com.roti5dao.order.entity.OrderStatus;
import com.roti5dao.order.repository.OrderRepository;
import com.roti5dao.payment.dto.PaymentDtos.PaymentResponse;
import com.roti5dao.payment.entity.Payment;
import com.roti5dao.payment.entity.PaymentMethod;
import com.roti5dao.payment.service.PaymentMethodService;
import com.roti5dao.payment.service.PaymentService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * การชำระเงินของออเดอร์ — lock ออเดอร์ (SELECT FOR UPDATE) ทุกครั้ง
 * เพื่อให้ยอดชำระไม่เกินยอดคงค้างแม้มีหลายรายการพร้อมกัน
 */
@Service
public class OrderPaymentService {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final PaymentMethodService paymentMethodService;
    private final SecurityAuditService audit;
    private final AppProperties.OrderLimits limits;

    public OrderPaymentService(OrderRepository orderRepository, OrderService orderService, PaymentService paymentService,
                               PaymentMethodService paymentMethodService, SecurityAuditService audit, AppProperties props) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.paymentMethodService = paymentMethodService;
        this.audit = audit;
        this.limits = props.order();
    }

    /** พนักงานรับเงิน (POS) — CASH คำนวณเงินทอน, จ่ายผสมได้หลายครั้งจนครบยอด */
    @Transactional
    public OrderResponse payByStaff(Long orderId, StaffPaymentRequest req, Long staffId) {
        Order order = orderService.lock(orderId);
        requireOpen(order); // ร้านเก็บเงินตอนรับอาหาร — รับได้ทุกขั้นจนกว่าจะปิด/ยกเลิก
        PaymentMethod method = paymentMethodService.requireUsable(req.methodCode(), false);
        BigDecimal amount = resolveAmount(order, req.amount());
        paymentService.recordByStaff(order.getId(), method, amount, req.cashReceived(), req.referenceNo(), staffId);
        orderService.confirmIfFullyPaid(order);
        orderRepository.flush();
        return OrderMapper.full(order, paymentService.forOrder(orderId), orderService.nickname(order.getCustomerId()));
    }

    /** ลูกค้าเลือกช่องทางชำระเงินผ่านลิงก์ติดตาม (ไม่มีสลิป) → PENDING รอร้านตรวจยอดเข้าเอง */
    @Transactional
    public TrackResponse submitPayment(UUID trackingToken, String methodCode, BigDecimal amount, String referenceNo) {
        Order order = orderRepository.findByTrackingTokenForUpdate(trackingToken)
                .orElseThrow(() -> new NotFoundException("ออเดอร์"));
        requireOpen(order);
        PaymentMethod method = paymentMethodService.requireUsable(methodCode, true);
        if (paymentService.pendingCount(order.getId()) >= limits.maxPendingPaymentsPerOrder()) {
            throw new BusinessException(ErrorCode.TOO_MANY_PENDING_PAYMENTS);
        }
        BigDecimal value = resolveAmount(order, amount);
        paymentService.recordOnline(order.getId(), method, value, referenceNo);
        Order full = orderRepository.findWithItems(order.getId()).orElseThrow();
        return OrderMapper.track(full, paymentService.forOrder(order.getId()));
    }

    /**
     * ปิดออเดอร์หน้าเคาน์เตอร์ในครั้งเดียว: (ถ้ายังค้าง) บันทึกการจ่ายเต็มยอดคงค้าง → READY → COMPLETED → ให้แต้ม
     * ทั้งหมดใน transaction เดียว · ไม่แนบการชำระ → ใช้ช่องทางที่ลูกค้าแจ้งไว้ (ถ้าไม่มี → 422 ORDER_INVALID_STATUS)
     * payment PENDING ที่ลูกค้าแจ้งไว้ (ยังไม่ตรวจ) จะถูกปฏิเสธอัตโนมัติเมื่อรับเงินที่เคาน์เตอร์ครบแล้ว
     */
    @Transactional
    public OrderResponse complete(Long orderId, StaffPaymentRequest payment, Long staffId) {
        Order order = orderService.lock(orderId);
        requireOpen(order);
        if (!orderService.isFullyPaid(order)) {
            if (payment != null) {
                PaymentMethod method = paymentMethodService.requireUsable(payment.methodCode(), false);
                paymentService.recordByStaff(order.getId(), method, remaining(order), payment.cashReceived(),
                        payment.referenceNo(), staffId);
            } else {
                confirmCustomerChoice(order, staffId);
            }
            paymentService.rejectPending(order.getId(), staffId, "ชำระที่เคาน์เตอร์แล้ว");
            order.setPayLater(false);
        }
        orderService.advanceToCompleted(order);
        return OrderMapper.full(order, paymentService.forOrder(orderId), orderService.nickname(order.getCustomerId()));
    }

    /**
     * ไม่ได้แนบการชำระ → ใช้ช่องทางที่ลูกค้าแจ้งไว้ล่าสุด (payment PENDING) เป็นค่าเริ่มต้น
     * ยอดตรงกับยอดคงค้าง = อนุมัติรายการนั้นเลย · ไม่ตรง (เช่น แก้รายการหลังแจ้ง) = บันทึกใหม่เต็มยอดด้วยช่องทางเดิม
     */
    private void confirmCustomerChoice(Order order, Long staffId) {
        Payment chosen = paymentService.forOrder(order.getId()).stream()
                .filter(p -> p.getStatus() == com.roti5dao.payment.entity.PaymentStatus.PENDING)
                .max(java.util.Comparator.comparing(Payment::getCreatedAt).thenComparing(Payment::getId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_INVALID_STATUS,
                        "ออเดอร์นี้ยังชำระไม่ครบ — ระบุช่องทางชำระเงิน (methodCode) ก่อนปิดออเดอร์"));
        BigDecimal remaining = remaining(order);
        if (chosen.getAmount().compareTo(remaining) == 0) {
            paymentService.approve(paymentService.lockForUpdate(chosen.getId()), staffId);
        } else {
            paymentService.recordByStaff(order.getId(), chosen.getMethod(), remaining, null, chosen.getReferenceNo(), staffId);
        }
    }

    /** พนักงานตรวจยอดเข้า — อนุมัติแล้วยอดครบ → CONFIRMED */
    @Transactional
    public PaymentResponse verify(Long paymentId, boolean approve, String rejectReason, Long staffId) {
        // lock ออเดอร์ก่อน payment เสมอ (ลำดับเดียวกับ cancel) กัน deadlock
        Order order = orderService.lock(paymentService.get(paymentId).getOrderId());
        Payment payment = paymentService.lockForUpdate(paymentId);
        if (payment.getStatus() != com.roti5dao.payment.entity.PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "รายการนี้ถูกตรวจสอบไปแล้ว");
        }
        if (approve) {
            requireOpen(order);
            BigDecimal remaining = remaining(order);
            if (payment.getAmount().compareTo(remaining) > 0) {
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                        "ยอดที่แจ้งเกินยอดคงค้าง (" + remaining + " บาท) กรุณาปฏิเสธแล้วให้ลูกค้าแจ้งใหม่");
            }
            paymentService.approve(payment, staffId);
            orderService.confirmIfFullyPaid(order);
            audit.record(Event.PAYMENT_VERIFIED, order.getCustomerId(), "payment=" + paymentId + " order=" + order.getOrderNo());
        } else {
            paymentService.reject(payment, staffId, rejectReason);
            audit.record(Event.PAYMENT_REJECTED, order.getCustomerId(), "payment=" + paymentId + " order=" + order.getOrderNo());
        }
        orderRepository.flush();
        return PaymentResponse.of(payment);
    }

    private BigDecimal resolveAmount(Order order, BigDecimal requested) {
        BigDecimal remaining = remaining(order);
        if (remaining.signum() <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "ออเดอร์นี้ชำระครบแล้ว");
        }
        if (requested == null) {
            return remaining;
        }
        BigDecimal amount = MoneyUtils.scale(requested);
        if (amount.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "ยอดชำระต้องมากกว่า 0 และไม่เกินยอดคงค้าง " + remaining + " บาท");
        }
        return amount;
    }

    private BigDecimal remaining(Order order) {
        return MoneyUtils.max(MoneyUtils.ZERO, order.getTotalAmount().subtract(paymentService.paidTotal(order.getId())));
    }

    /** ชำระได้ทุกขั้นจนกว่าออเดอร์จะปิด/ยกเลิก (ยอดเกินยอดคงค้างถูกกันที่ resolveAmount) */
    private static void requireOpen(Order order) {
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS, "ออเดอร์นี้ปิดหรือยกเลิกไปแล้ว");
        }
    }
}
