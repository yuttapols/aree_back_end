package com.roti5dao.user.entity;

import com.roti5dao.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;

@Getter
@Setter
@Entity
@Table(name = "customer_profile")
public class CustomerProfile extends AuditableEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    /** สร้างโดย DB default จาก member_code_seq */
    @Generated
    @Column(name = "member_code", insertable = false, updatable = false, length = 20)
    private String memberCode;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 50)
    private String nickname;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "points_balance", nullable = false)
    private int pointsBalance;

    @Column(name = "lifetime_points", nullable = false)
    private int lifetimePoints;
}
