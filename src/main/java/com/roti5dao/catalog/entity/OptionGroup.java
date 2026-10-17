package com.roti5dao.catalog.entity;

import com.roti5dao.common.entity.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.SQLRestriction;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "option_group")
public class OptionGroup extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "min_select", nullable = false)
    private int minSelect;

    @Column(name = "max_select", nullable = false)
    private int maxSelect = 1;

    @Column(name = "name_en", length = 100)
    private String nameEn;

    /** ติ๊กไว้ให้ก่อน — ลูกค้าเอาออกได้ (FE แสดง "ไม่ใส่…") */
    @Column(name = "default_selected", nullable = false)
    private boolean defaultSelected;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** ลบแล้ว (ต่างจาก active=false ที่แค่ปิดชั่วคราว) — ทุก query กรองออกด้วย @SQLRestriction */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<OptionItem> items = new ArrayList<>();
}
