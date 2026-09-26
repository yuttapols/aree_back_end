package com.roti5dao.catalog.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * ข้อมูลสินค้าที่ module order ใช้ตรวจ/คำนวณราคา (read-only snapshot)
 * order ห้ามเข้าถึง ProductRepository โดยตรง — เรียกผ่าน ProductQueryService.getForOrder()
 */
public record OrderableProduct(Long id, Long categoryId, String name, BigDecimal price, boolean orderable,
                               List<Group> optionGroups) {

    public record Group(Long id, String name, int minSelect, int maxSelect, Map<Long, Option> options) {
    }

    public record Option(Long id, String name, BigDecimal extraPrice, boolean available) {
    }
}
