package com.roti5dao.payment.entity;

import com.roti5dao.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payment_method")
public class PaymentMethod extends AuditableEntity {

    public static final String CASH = "CASH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "requires_slip", nullable = false)
    private boolean requiresSlip;

    @Column(name = "requires_reference", nullable = false)
    private boolean requiresReference;

    @Column(name = "allow_online", nullable = false)
    private boolean allowOnline;

    @Column(length = 1000)
    private String instruction;

    @Column(length = 100)
    private String icon;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public boolean isCash() {
        return CASH.equals(code);
    }
}
