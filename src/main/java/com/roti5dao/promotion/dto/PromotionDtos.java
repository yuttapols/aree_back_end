package com.roti5dao.promotion.dto;

import com.roti5dao.promotion.entity.Promotion;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionChannel;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionScope;
import com.roti5dao.promotion.entity.PromotionEnums.PromotionType;
import com.roti5dao.promotion.entity.PromotionTarget;
import com.roti5dao.promotion.entity.PromotionUsage;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class PromotionDtos {

    private PromotionDtos() {
    }

    public record PublicPromotion(Long id, String code, String name, String description, String bannerUrl,
                                  PromotionType type, BigDecimal discountValue, BigDecimal maxDiscount, Integer buyQty,
                                  Integer getQty, BigDecimal minOrderAmount, PromotionScope scope, boolean memberOnly,
                                  PromotionChannel channel, List<Integer> daysOfWeek, Instant startAt, Instant endAt) {
        public static PublicPromotion of(Promotion p) {
            return new PublicPromotion(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getBannerUrl(),
                    p.getType(), p.getDiscountValue(), p.getMaxDiscount(), p.getBuyQty(), p.getGetQty(),
                    p.getMinOrderAmount(), p.getScope(), p.isMemberOnly(), p.getChannel(), days(p), p.getStartAt(),
                    p.getEndAt());
        }
    }

    public record AdminPromotion(Long id, String code, String name, String description, String bannerUrl,
                                 PromotionType type, BigDecimal discountValue, BigDecimal maxDiscount, Integer buyQty,
                                 Integer getQty, BigDecimal minOrderAmount, PromotionScope scope, boolean memberOnly,
                                 PromotionChannel channel, List<Integer> daysOfWeek, Instant startAt, Instant endAt,
                                 Integer usageLimit, Integer usagePerCustomer, int usedCount, boolean showOnLanding,
                                 int priority, boolean active, List<Long> productIds, List<Long> categoryIds,
                                 Instant updatedAt) {
        public static AdminPromotion of(Promotion p) {
            return new AdminPromotion(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getBannerUrl(),
                    p.getType(), p.getDiscountValue(), p.getMaxDiscount(), p.getBuyQty(), p.getGetQty(),
                    p.getMinOrderAmount(), p.getScope(), p.isMemberOnly(), p.getChannel(), days(p), p.getStartAt(),
                    p.getEndAt(), p.getUsageLimit(), p.getUsagePerCustomer(), p.getUsedCount(), p.isShowOnLanding(),
                    p.getPriority(), p.isActive(),
                    p.getTargets().stream().map(PromotionTarget::getProductId).filter(Objects::nonNull).toList(),
                    p.getTargets().stream().map(PromotionTarget::getCategoryId).filter(Objects::nonNull).toList(),
                    p.getUpdatedAt());
        }
    }

    public record UsageResponse(Long id, Long orderId, Long customerId, BigDecimal discountAmount, Instant createdAt) {
        public static UsageResponse of(PromotionUsage u) {
            return new UsageResponse(u.getId(), u.getOrderId(), u.getCustomerId(), u.getDiscountAmount(), u.getCreatedAt());
        }
    }

    public record PromotionUpsertRequest(
            @Size(max = 30) @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$", message = "โค้ดต้องเป็น A-Z 0-9 _ - ยาว 3–30 ตัว") String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @Size(max = 500) String bannerUrl,
            @NotNull PromotionType type,
            @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal discountValue,
            @DecimalMin("0.01") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal maxDiscount,
            @Min(1) @Max(100) Integer buyQty,
            @Min(1) @Max(100) Integer getQty,
            @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal minOrderAmount,
            @NotNull PromotionScope scope,
            boolean memberOnly,
            @NotNull PromotionChannel channel,
            @Size(max = 7) List<@NotNull @Min(1) @Max(7) Integer> daysOfWeek,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            @Min(1) @Max(10_000_000) Integer usageLimit,
            @Min(1) @Max(1000) Integer usagePerCustomer,
            boolean showOnLanding,
            @Min(-1000) @Max(1000) int priority,
            boolean active,
            @Size(max = 200) List<@NotNull Long> productIds,
            @Size(max = 200) List<@NotNull Long> categoryIds) {
    }

    private static List<Integer> days(Promotion p) {
        return p.getDaysOfWeek() == null ? null : Arrays.stream(p.getDaysOfWeek()).map(Short::intValue).toList();
    }
}
