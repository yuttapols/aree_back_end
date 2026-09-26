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

    public record CategoryResponse(Long id, String name, String slug, String description, String icon, int sortOrder,
                                   boolean active) {
        public static CategoryResponse of(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getIcon(),
                    c.getSortOrder(), c.isActive());
        }
    }

    public record ProductSummary(Long id, Long categoryId, String code, String name, String description,
                                 BigDecimal price, String imageUrl, boolean available, boolean recommended,
                                 boolean hasOptions) {
        public static ProductSummary of(Product p, boolean hasOptions) {
            return new ProductSummary(p.getId(), p.getCategory().getId(), p.getCode(), p.getName(), p.getDescription(),
                    p.getPrice(), p.getImageUrl(), p.isAvailable(), p.isRecommended(), hasOptions);
        }
    }

    public record OptionItemResponse(Long id, String name, BigDecimal extraPrice, boolean available, int sortOrder) {
        public static OptionItemResponse of(OptionItem i) {
            return new OptionItemResponse(i.getId(), i.getName(), i.getExtraPrice(), i.isAvailable(), i.getSortOrder());
        }
    }

    public record OptionGroupResponse(Long id, String name, int minSelect, int maxSelect, boolean active, int sortOrder,
                                      List<OptionItemResponse> items) {
        public static OptionGroupResponse of(OptionGroup g) {
            return new OptionGroupResponse(g.getId(), g.getName(), g.getMinSelect(), g.getMaxSelect(), g.isActive(),
                    g.getSortOrder(), g.getItems().stream().map(OptionItemResponse::of).toList());
        }
    }

    public record ProductImageResponse(Long id, String url, int sortOrder) {
        public static ProductImageResponse of(ProductImage i) {
            return new ProductImageResponse(i.getId(), i.getUrl(), i.getSortOrder());
        }
    }

    public record ProductDetail(Long id, Long categoryId, String categoryName, String code, String name,
                                String description, BigDecimal price, String imageUrl, boolean available,
                                boolean recommended, List<ProductImageResponse> images,
                                List<OptionGroupResponse> optionGroups) {
    }

    public record MenuCategory(CategoryResponse category, List<ProductSummary> products) {
    }

    public record AdminProductResponse(Long id, Long categoryId, String categoryName, String code, String name,
                                       String description, BigDecimal price, String imageUrl, boolean available,
                                       boolean recommended, boolean active, int sortOrder, Instant updatedAt,
                                       List<ProductImageResponse> images, List<OptionGroupResponse> optionGroups) {
    }

    // ---------- Requests ----------

    public record CategoryUpsertRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "ใช้ได้เฉพาะ a-z 0-9 และ -") String slug,
            @Size(max = 500) String description,
            @Size(max = 100) @Pattern(regexp = "^[a-zA-Z0-9 _-]*$", message = "icon ไม่ถูกต้อง") String icon,
            @Min(0) @Max(100000) int sortOrder,
            boolean active) {
    }

    public record SortItem(@NotNull Long id, @Min(0) @Max(100000) int sortOrder) {
    }

    public record ProductUpsertRequest(
            @NotNull Long categoryId,
            @NotBlank @Size(max = 30) @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]*$", message = "ใช้ได้เฉพาะ A-Z 0-9 _ -") String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal price,
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
            @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal extraPrice,
            boolean available,
            @Min(0) @Max(100000) int sortOrder) {
    }

    public record OptionGroupUpsertRequest(
            @NotBlank @Size(max = 100) String name,
            @Min(0) @Max(50) int minSelect,
            @Min(1) @Max(50) int maxSelect,
            boolean active,
            @Min(0) @Max(100000) int sortOrder,
            @NotNull @Size(max = 50) List<@Valid OptionItemUpsert> items) {
    }

    public record ProductOptionGroupLink(@NotNull Long optionGroupId, @Min(0) @Max(100000) int sortOrder) {
    }
}
