package com.roti5dao.user.entity;

import com.roti5dao.common.entity.AuditableEntity;
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
import org.hibernate.annotations.DynamicUpdate;

/**
 * {@code @DynamicUpdate}: UPDATE เฉพาะคอลัมน์ที่เปลี่ยน — คอลัมน์ security (failed_login_count, locked_until,
 * tokens_valid_after, last_login_at) ถูกอัปเดตผ่าน JDBC โดยตรง จึงห้ามให้ entity flush ทับค่าเก่า
 */
@Getter
@Setter
@Entity
@DynamicUpdate
@Table(name = "app_user")
public class AppUser extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "tokens_valid_after")
    private Instant tokensValidAfter;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
