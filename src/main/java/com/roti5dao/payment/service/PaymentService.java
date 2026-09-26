package com.roti5dao.payment.service;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.storage.FileStorageService;
import com.roti5dao.common.storage.FileStorageService.LoadedFile;
import com.roti5dao.common.storage.StorageFolder;
import com.roti5dao.common.util.MoneyUtils;
import com.roti5dao.payment.entity.Payment;
import com.roti5dao.payment.entity.PaymentMethod;
import com.roti5dao.payment.entity.PaymentStatus;
import com.roti5dao.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * บันทึก/ตรวจการชำระเงิน — ไม่รู้จักออเดอร์ (module order เป็นผู้ตรวจสถานะออเดอร์และยอดคงค้างก่อนเรียก)
 * ทุก method บังคับให้อยู่ใน transaction ของผู้เรียก
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class PaymentService {

    private final PaymentRepository repository;
    private final FileStorageService storage;
    private final Clock clock;

    public PaymentService(PaymentRepository repository, FileStorageService storage, Clock clock) {
        this.repository = repository;
        this.storage = storage;
        this.clock = clock;
    }

    /** พนักงานรับเงินที่หน้าร้าน → PAID ทันที (CASH คำนวณเงินทอน, ช่องทางที่ต้องมีเลขอ้างอิงต้องระบุ) */
    public Payment recordByStaff(Long orderId, PaymentMethod method, BigDecimal amount, BigDecimal cashReceived,
                                 String referenceNo, Long staffId) {
        BigDecimal amt = MoneyUtils.scale(amount);
        Payment p = newPayment(orderId, method, amt, referenceNo);
        if (method.isCash()) {
            BigDecimal received = cashReceived == null ? amt : MoneyUtils.scale(cashReceived);
            if (received.compareTo(amt) < 0) {
                throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "เงินที่รับมาน้อยกว่ายอดชำระ");
            }
            p.setCashReceived(received);
            p.setChangeAmount(received.subtract(amt));
        }
        if (method.isRequiresReference() && isBlank(referenceNo)) {
            throw new BusinessException(ErrorCode.REFERENCE_REQUIRED);
        }
        p.setStatus(PaymentStatus.PAID);
        p.setPaidAt(Instant.now(clock));
        p.setVerifiedBy(staffId);
        return repository.saveAndFlush(p);
    }

    /** ลูกค้าแนบสลิปเอง → PENDING รอพนักงานตรวจ (สลิปเก็บเป็นไฟล์ private) */
    public Payment recordSlip(Long orderId, PaymentMethod method, BigDecimal amount, String referenceNo, MultipartFile slip) {
        if (method.isRequiresSlip() && (slip == null || slip.isEmpty())) {
            throw new BusinessException(ErrorCode.SLIP_REQUIRED);
        }
        if (method.isRequiresReference() && isBlank(referenceNo)) {
            throw new BusinessException(ErrorCode.REFERENCE_REQUIRED);
        }
        Payment p = newPayment(orderId, method, MoneyUtils.scale(amount), referenceNo);
        if (slip != null && !slip.isEmpty()) {
            p.setSlipKey(storage.store(slip, StorageFolder.SLIPS).location());
        }
        return repository.saveAndFlush(p);
    }

    public Payment lockForUpdate(Long paymentId) {
        return repository.findForUpdate(paymentId).orElseThrow(() -> new NotFoundException("รายการชำระเงิน"));
    }

    public void approve(Payment p, Long staffId) {
        requirePending(p);
        p.setStatus(PaymentStatus.PAID);
        p.setPaidAt(Instant.now(clock));
        p.setVerifiedBy(staffId);
        repository.flush();
    }

    public void reject(Payment p, Long staffId, String reason) {
        requirePending(p);
        if (isBlank(reason)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "กรุณาระบุเหตุผลที่ปฏิเสธ");
        }
        p.setStatus(PaymentStatus.REJECTED);
        p.setVerifiedBy(staffId);
        p.setRejectReason(reason.trim());
        repository.flush();
    }

    /** ยกเลิกออเดอร์: PAID → REFUNDED, PENDING → REJECTED */
    public void voidForCancelledOrder(Long orderId, Long staffId) {
        for (Payment p : repository.findByOrderId(orderId)) {
            if (p.getStatus() == PaymentStatus.PAID) {
                p.setStatus(PaymentStatus.REFUNDED);
                p.setVerifiedBy(staffId);
            } else if (p.getStatus() == PaymentStatus.PENDING) {
                p.setStatus(PaymentStatus.REJECTED);
                p.setVerifiedBy(staffId);
                p.setRejectReason("ออเดอร์ถูกยกเลิก");
            }
        }
        repository.flush();
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public BigDecimal paidTotal(Long orderId) {
        return MoneyUtils.scale(repository.sumByOrderAndStatus(orderId, PaymentStatus.PAID));
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public long pendingCount(Long orderId) {
        return repository.countByOrderIdAndStatus(orderId, PaymentStatus.PENDING);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<Payment> forOrder(Long orderId) {
        return repository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public List<Payment> forOrders(Collection<Long> orderIds) {
        return orderIds.isEmpty() ? List.of() : repository.findByOrderIdIn(orderIds);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Page<Payment> search(PaymentStatus status, Pageable pageable) {
        return repository.search(status, pageable);
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public Payment get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("รายการชำระเงิน"));
    }

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public LoadedFile loadSlip(Payment p) {
        if (p.getSlipKey() == null) {
            throw new NotFoundException("สลิป");
        }
        return storage.loadPrivate(p.getSlipKey());
    }

    private static Payment newPayment(Long orderId, PaymentMethod method, BigDecimal amount, String referenceNo) {
        if (amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH, "ยอดชำระต้องมากกว่า 0");
        }
        Payment p = new Payment();
        p.setOrderId(orderId);
        p.setMethod(method);
        p.setAmount(amount);
        p.setReferenceNo(isBlank(referenceNo) ? null : referenceNo.trim());
        return p;
    }

    private static void requirePending(Payment p) {
        if (p.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "รายการนี้ถูกตรวจสอบไปแล้ว");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
