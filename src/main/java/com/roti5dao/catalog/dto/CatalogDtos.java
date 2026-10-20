package com.roti5dao.catalog.dto;

import com.roti5dao.catalog.entity.Category;
import com.roti5dao.catalog.entity.OptionGroup;
import com.roti5dao.catalog.entity.OptionItem;
import com.roti5dao.catalog.entity.Product;
import com.roti5dao.catalog.entity.ProductImage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    // ---------- Responses ----------

    public record CategoryResponse(Long id, String name, String nameEn, String slug, String description, String icon,
                                   int sortOrder, boolean active) {
        public static CategoryResponse of(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getNameEn(), c.getSlug(), c.getDescription(), c.getIcon(),
                    c.getSortOrder(), c.isActive());
        }
    }

    public record ProductSummary(Long id, Long categoryId, String code, String name, String nameEn, String description,
                                 String descriptionEn, BigDecimal price, BigDecimal originalPrice, String badge,
                                 String imageUrl, boolean available, boolean recommended, boolean hasOptions) {
        public static ProductSummary of(Product p, boolean hasOptions) {
            return new ProductSummary(p.getId(), p.getCategory().getId(), p.getCode(), p.getName(), p.getNameEn(),
                    p.getDescription(), p.getDescriptionEn(), p.getPrice(), p.getOriginalPrice(), p.getBadge(),
                    p.getImageUrl(), p.isAvailable(), p.isRecommended(), hasOptions);
        }
    }

    public record OptionItemResponse(Long id, String name, String nameEn, BigDecimal extraPrice, boolean available,
                                     int sortOrder) {
        public static OptionItemResponse of(OptionItem i) {
            return new OptionItemResponse(i.getId(), i.getName(), i.getNameEn(), i.getExtraPrice(), i.isAvailable(),
                    i.getSortOrder());
        }
    }

    public record OptionGroupResponse(Long id, String name, String nameEn, int minSelect, int maxSelect,
                                      boolean defaultSelected, boolean active, int sortOrder,
                                      List<OptionItemResponse> items) {
        public static OptionGroupResponse of(OptionGroup g) {
            return new OptionGroupResponse(g.getId(), g.getName(), g.getNameEn(), g.getMinSelect(), g.getMaxSelect(),
                    g.isDefaultSelected(), g.isActive(),
                    g.getSortOrder(), g.getItems().stream().map(OptionItemResponse::of).toList());
        }
    }

    public record ProductImageResponse(Long id, String url, int sortOrder) {
        public static ProductImageResponse of(ProductImage i) {
            return new ProductImageResponse(i.getId(), i.getUrl(), i.getSortOrder());
        }
    }

    public record ProductDetail(Long id, Long categoryId, String categoryName, String code, String name, String nameEn,
                                String description, String descriptionEn, BigDecimal price, BigDecimal originalPrice,
                                String badge, String imageUrl, boolean available,
                                boolean recommended, List<ProductImageResponse> images,
                                List<OptionGroupResponse> optionGroups) {
    }

    public record MenuCategory(CategoryResponse category, List<ProductSummary> products) {
    }

    public record AdminProductResponse(Long id, Long categoryId, String categoryName, String code, String name,
                                       String nameEn, String description, String descriptionEn, BigDecimal price,
                                       BigDecimal originalPrice, String badge, String imageUrl, boolean available,
                                       boolean recommended, boolean active, int sortOrder, Instant updatedAt,
                                       List<ProductImageResponse> images, List<OptionGroupResponse> optionGroups) {
    }

    // ---------- Requests ----------

    public record CategoryUpsertRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String nameEn,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "ใช้ได้เฉพาะ a-z 0-9 และ -") String slug,
            @Size(max = 500) String description,
            @Size(max = 100) @Pattern(regexp = "^[a-zA-Z0-9 _-]*$", message = "icon ไม่ถูกต้อง") String icon,
            /** ไม่ส่ง = สร้างใหม่ต่อท้าย / แก้ไขคงลำดับเดิม (จัดลำดับใช้ PATCH /sort) */
            @Min(0) @Max(100000) Integer sortOrder,
            boolean active) {
    }

    public record SortItem(@NotNull Long id, @Min(0) @Max(100000) int sortOrder) {
    }

    public record ProductUpsertRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 150) String nameEn,
            @Size(max = 1000) String description,
            @Size(max = 1000) String descriptionEn,
            @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal price,
            /** ราคาก่อนลด (ขีดฆ่า) — ถ้าส่งต้อง > price */
            @DecimalMin("0.01") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal originalPrice,
            @Pattern(regexp = "^(bestseller|new|recommended)$", message = "badge ต้องเป็น bestseller, new หรือ recommended") String badge,
            @Size(max = 500) String imageUrl,
            boolean available,
            boolean recommended,
            boolean active,
            @Min(0) @Max(100000) int sortOrder) {
    }

    public record AvailabilityRequest(boolean available) {
    }

    public record OptionItemUpsert(
            Long id,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String nameEn,
            @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal extraPrice,
            boolean available,
            @Min(0) @Max(100000) int sortOrder) {
    }

    public record OptionGroupUpsertRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String nameEn,
            @Min(0) @Max(50) int minSelect,
            @Min(1) @Max(50) int maxSelect,
            /** ติ๊กไว้ให้ก่อน (ไม่ส่ง = false) */
            Boolean defaultSelected,
            boolean active,
            @Min(0) @Max(100000) int sortOrder,
            @NotNull @Size(max = 50) List<@Valid OptionItemUpsert> items) {
    }

    public record ProductOptionGroupLink(@NotNull Long optionGroupId, @Min(0) @Max(100000) int sortOrder) {
    }
}
