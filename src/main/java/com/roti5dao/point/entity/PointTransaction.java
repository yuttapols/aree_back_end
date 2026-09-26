package com.roti5dao.point.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Ledger แต้ม (insert-only) — แถวที่ points > 0 เป็น "ก้อนแต้ม" มี remaining ไว้ตัดแบบ FIFO ตามวันหมดอายุ
 * แก้ได้เฉพาะ remaining เท่านั้น
 */
@Getter
@Setter
@Entity
@Table(name = "point_transaction")
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "order_id", updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PointType type;

    @Column(nullable = false, updatable = false)
    private int points;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private int balanceAfter;

    @Column
    private Integer remaining;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(length = 300, updatable = false)
    private String remark;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 50, updatable = false)
    private String createdBy;
}
