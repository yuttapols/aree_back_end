package com.roti5dao.point.dto;

import com.roti5dao.point.entity.PointTransaction;
import com.roti5dao.point.entity.PointType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class PointDtos {

    private PointDtos() {
    }

    public record PointSummary(int balance, int expiringSoon, Instant nextExpiryAt, int expiringWindowDays) {
    }

    public record PointTransactionResponse(Long id, Long orderId, PointType type, int points, int balanceAfter,
                                           Integer remaining, Instant expiresAt, String remark, Instant createdAt) {
        public static PointTransactionResponse of(PointTransaction t) {
            return new PointTransactionResponse(t.getId(), t.getOrderId(), t.getType(), t.getPoints(), t.getBalanceAfter(),
                    t.getRemaining(), t.getExpiresAt(), t.getRemark(), t.getCreatedAt());
        }
    }

    public record AdjustRequest(@NotNull @Min(-1_000_000) @Max(1_000_000) Integer points,
                                @NotBlank @Size(max = 300) String remark) {
    }
}
