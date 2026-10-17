package com.roti5dao.catalog.entity;

import com.roti5dao.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.SQLRestriction;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "product")
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "is_recommended", nullable = false)
    private boolean recommended;

    @Column(name = "name_en", length = 150)
    private String nameEn;

    @Column(name = "description_en", length = 1000)
    private String descriptionEn;

    /** ราคาก่อนลด (แสดงขีดฆ่า) — null = ไม่มี, ถ้ามีต้อง > price */
    @Column(name = "original_price", precision = 7, scale = 2)
    private BigDecimal originalPrice;

    /** bestseller | new | recommended — null = ไม่มีป้าย */
    @Column(length = 20)
    private String badge;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** ลบแล้ว (ต่างจาก active=false ที่แค่ปิดชั่วคราว) — ทุก query กรองออกด้วย @SQLRestriction */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** ขายได้จริงหรือไม่ (ยังไม่ถูกลบ, มีของ, หมวดยังเปิด) */
    public boolean isOrderable() {
        return active && available && category.isActive();
    }
}
