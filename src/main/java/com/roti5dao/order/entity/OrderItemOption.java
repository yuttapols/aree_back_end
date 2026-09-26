package com.roti5dao.order.entity;

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
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "order_item_option")
public class OrderItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "option_item_id")
    private Long optionItemId;

    @Column(name = "option_group_name", nullable = false, length = 100)
    private String optionGroupName;

    @Column(name = "option_name", nullable = false, length = 100)
    private String optionName;

    @Column(name = "extra_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal extraPrice;
}
