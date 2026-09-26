package com.roti5dao.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "product_option_group_map")
public class ProductOptionGroup {

    @EmbeddedId
    private Key id;

    @MapsId("optionGroupId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id")
    private OptionGroup optionGroup;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "product_id")
        private Long productId;

        @Column(name = "option_group_id")
        private Long optionGroupId;

        public Key(Long productId, Long optionGroupId) {
            this.productId = productId;
            this.optionGroupId = optionGroupId;
        }
    }
}
