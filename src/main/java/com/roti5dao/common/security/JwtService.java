package com.roti5dao.common.security;

import com.roti5dao.common.config.AppProperties;
import com.roti5dao.user.entity.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * ออก/ตรวจ access token (JWT HS256)
 * claims: sub=userId, role, iss, aud, iat, exp, jti — อายุสั้น ตาม role (app.security.jwt.<role>.access-token-ttl)
 */
@Component
public class JwtService {

    public static final String EXPIRED_MARKER = "TOKEN_EXPIRED";
    static final String CLAIM_ROLE = "role";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AppProperties.Jwt props;
    private final Clock clock;

    public JwtService(AppProperties appProperties, Clock clock, Environment env) {
        this.props = appProperties.security().jwt();
        this.clock = clock;
        SecretKey key = new SecretKeySpec(resolveSecret(props.secret(), env), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new TimestampValidator(clock),
                new JwtIssuerValidator(props.issuer()),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(props.audience())),
                new JwtClaimValidator<String>("sub", sub -> sub != null && sub.matches("\\d{1,18}")),
                new JwtClaimValidator<String>(CLAIM_ROLE, r -> r != null && Role.isValid(r)))));
        this.decoder = nimbus;
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    public IssuedToken issueAccessToken(Long userId, Role role) {
        Instant now = Instant.now(clock);
        Instant exp = now.plus(props.forRole(role).accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .audience(List.of(props.audience()))
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(exp)
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_ROLE, role.name())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedToken(token, exp);
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    private static byte[] resolveSecret(String secret, Environment env) {
        if (secret == null || secret.isBlank()) {
            if (env.acceptsProfiles(Profiles.of("dev", "test"))) {
                log.warn("JWT_SECRET is not set — using an ephemeral random key (dev/test only). Tokens will be invalid after restart.");
                byte[] random = new byte[48];
                new java.security.SecureRandom().nextBytes(random);
                return random;
            }
            throw new IllegalStateException("app.security.jwt.secret (JWT_SECRET) must be set — generate with: openssl rand -base64 48");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET must be base64 encoded", e);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 256 bits (32 bytes)");
        }
        return bytes;
    }

    /** ตรวจ exp/nbf/iat เอง เพื่อแยก error TOKEN_EXPIRED ออกจาก token ที่ไม่ถูกต้อง */
    static final class TimestampValidator implements OAuth2TokenValidator<Jwt> {
        private final Clock clock;

        TimestampValidator(Clock clock) {
            this.clock = clock;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt jwt) {
            Instant now = Instant.now(clock);
            if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "missing exp/iat", null));
            }
            if (now.minus(CLOCK_SKEW).isAfter(jwt.getExpiresAt())) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", EXPIRED_MARKER, null));
            }
            if (jwt.getNotBefore() != null && now.plus(CLOCK_SKEW).isBefore(jwt.getNotBefore())) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "token not yet valid", null));
            }
            if (jwt.getIssuedAt().isAfter(now.plus(CLOCK_SKEW))) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "iat in the future", null));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }
}
