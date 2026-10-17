package com.roti5dao.banner.entity;

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

@Getter
@Setter
@Entity
@Table(name = "banner")
public class Banner extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BannerPlacement placement;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "image_url_mobile", length = 500)
    private String imageUrlMobile;

    @Column(length = 150)
    private String title;

    @Column(name = "title_en", length = 150)
    private String titleEn;

    @Column(length = 300)
    private String subtitle;

    @Column(name = "subtitle_en", length = 300)
    private String subtitleEn;

    @Column(name = "cta_label", length = 50)
    private String ctaLabel;

    @Column(name = "cta_label_en", length = 50)
    private String ctaLabelEn;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "start_at")
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;
}
