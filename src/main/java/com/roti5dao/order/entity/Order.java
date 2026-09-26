package com.roti5dao.order.entity;

import com.roti5dao.common.entity.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 20)
    private String orderNo;

    /** UUID v4 (SecureRandom) — ใช้เป็นลิงก์ติดตามสำหรับ guest จึงต้องเดาไม่ได้ */
    @Column(name = "tracking_token", nullable = false)
    private UUID trackingToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "guest_name", length = 100)
    private String guestName;

    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    @Column(name = "queue_no", nullable = false)
    private int queueNo;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "promotion_discount", nullable = false, precision = 10, scale = 2)
    private BigDecimal promotionDiscount;

    @Column(name = "point_discount", nullable = false, precision = 10, scale = 2)
    private BigDecimal pointDiscount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "points_redeemed", nullable = false)
    private int pointsRedeemed;

    @Column(name = "points_earned", nullable = false)
    private int pointsEarned;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "cashier_id")
    private Long cashierId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
