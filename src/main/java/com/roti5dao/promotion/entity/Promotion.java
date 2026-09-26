package com.roti5dao.promotion.entity;

import com.roti5dao.common.entity.AuditableEntity;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionChannel;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionScope;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code @DynamicUpdate}: used_count ถูกเพิ่ม/ลดด้วย UPDATE แบบ atomic (กันโควต้าเกิน)
 * การแก้โปรจากหลังบ้านจึงต้องไม่เขียนทับคอลัมน์นี้
 */
@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "promotion")
public class Promotion extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionType type;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Column(name = "max_discount", precision = 10, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "buy_qty")
    private Integer buyQty;

    @Column(name = "get_qty")
    private Integer getQty;

    @Column(name = "min_order_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionScope scope = PromotionScope.ORDER;

    @Column(name = "member_only", nullable = false)
    private boolean memberOnly;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromotionChannel channel = PromotionChannel.ALL;

    /** ISO: 1=จันทร์ … 7=อาทิตย์, null = ทุกวัน */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days_of_week", columnDefinition = "smallint[]")
    private Short[] daysOfWeek;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_per_customer")
    private Integer usagePerCustomer;

    @Column(name = "used_count", nullable = false, updatable = false)
    private int usedCount;

    @Column(name = "show_on_landing", nullable = false)
    private boolean showOnLanding = true;

    @Column(nullable = false)
    private int priority;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PromotionTarget> targets = new ArrayList<>();
}
