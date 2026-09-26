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
import org.springframework.web.multipart.MultipartFile;

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
        requirePendingPayment(order);
        PaymentMethod method = paymentMethodService.requireUsable(req.methodCode(), false);
        BigDecimal amount = resolveAmount(order, req.amount());
        paymentService.recordByStaff(order.getId(), method, amount, req.cashReceived(), req.referenceNo(), staffId);
        orderService.confirmIfFullyPaid(order);
        orderRepository.flush();
        return OrderMapper.full(order, paymentService.forOrder(orderId), orderService.nickname(order.getCustomerId()));
    }

    /** ลูกค้าแนบสลิปผ่านลิงก์ติดตาม → PENDING รอพนักงานตรวจ */
    @Transactional
    public TrackResponse submitSlip(UUID trackingToken, String methodCode, BigDecimal amount, String referenceNo,
                                    MultipartFile slip) {
        Order order = orderRepository.findByTrackingTokenForUpdate(trackingToken)
                .orElseThrow(() -> new NotFoundException("ออเดอร์"));
        requirePendingPayment(order);
        PaymentMethod method = paymentMethodService.requireUsable(methodCode, true);
        if (paymentService.pendingCount(order.getId()) >= limits.maxPendingSlipsPerOrder()) {
            throw new BusinessException(ErrorCode.TOO_MANY_PENDING_PAYMENTS);
        }
        BigDecimal value = resolveAmount(order, amount);
        paymentService.recordSlip(order.getId(), method, value, referenceNo, slip);
        Order full = orderRepository.findWithItems(order.getId()).orElseThrow();
        return OrderMapper.track(full, paymentService.forOrder(order.getId()));
    }

    /** พนักงานตรวจสลิป — อนุมัติแล้วยอดครบ → CONFIRMED */
    @Transactional
    public PaymentResponse verify(Long paymentId, boolean approve, String rejectReason, Long staffId) {
        // lock ออเดอร์ก่อน payment เสมอ (ลำดับเดียวกับ cancel) กัน deadlock
        Order order = orderService.lock(paymentService.get(paymentId).getOrderId());
        Payment payment = paymentService.lockForUpdate(paymentId);
        if (payment.getStatus() != com.roti5dao.payment.entity.PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "รายการนี้ถูกตรวจสอบไปแล้ว");
        }
        if (approve) {
            requirePendingPayment(order);
            BigDecimal remaining = remaining(order);
            if (payment.getAmount().compareTo(remaining) > 0) {
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                        "ยอดในสลิปเกินยอดคงค้าง (" + remaining + " บาท) กรุณาปฏิเสธและให้ลูกค้าแนบใหม่");
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

    private static void requirePendingPayment(Order order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.ORDER_INVALID_STATUS);
        }
    }
}
