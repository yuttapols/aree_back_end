package com.roti5dao.payment.entity;

import com.roti5dao.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payment")
public class Payment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_method_id", nullable = false)
    private PaymentMethod method;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "cash_received", precision = 10, scale = 2)
    private BigDecimal cashReceived;

    @Column(name = "change_amount", precision = 10, scale = 2)
    private BigDecimal changeAmount;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    /** storage key ของไฟล์ private (ไม่ใช่ URL สาธารณะ) */
    @Column(name = "slip_url", length = 500)
    private String slipKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "reject_reason", length = 300)
    private String rejectReason;
}
