package com.roti5dao.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull ZoneId timezone,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Security security,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Bootstrap bootstrap,
        @Valid @NotNull OrderLimits order) {

    public record Cors(List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
    }

    public record Security(
            @Valid @NotNull Jwt jwt,
            @Valid @NotNull RefreshCookie refreshCookie,
            @Valid @NotNull Password password,
            @Valid @NotNull Login login,
            @Valid @NotNull RateLimit rateLimit) {
    }

    public record Jwt(
            String secret,
            @NotBlank String issuer,
            @NotBlank String audience,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    public record RefreshCookie(@NotBlank String name, @NotBlank String path, boolean secure, @NotBlank String sameSite) {
    }

    public record Password(@Min(10) int bcryptStrength) {
    }

    public record Login(@Min(1) int maxFailedAttempts, @NotNull Duration lockDuration) {
    }

    public record RateLimit(boolean enabled) {
    }

    public record Storage(@NotBlank String localDir, @NotBlank String publicUrlPrefix, @NotNull DataSize maxFileSize) {
    }

    public record Bootstrap(String adminPhone, String adminPassword, String adminNickname) {
    }

    public record OrderLimits(@Min(1) int maxItemsPerOrder, @Min(1) int maxQuantityPerItem, @Min(1) int maxPendingSlipsPerOrder) {
    }
}
