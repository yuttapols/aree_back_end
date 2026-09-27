package com.roti5dao.auth.service;

import com.roti5dao.auth.entity.RefreshToken;
import com.roti5dao.auth.repository.RefreshTokenRepository;
import com.roti5dao.common.config.AppProperties;
import com.roti5dao.common.security.TokenHashing;
import com.roti5dao.user.entity.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final AppProperties.Jwt jwt;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties props, Clock clock) {
        this.repository = repository;
        this.jwt = props.security().jwt();
        this.clock = clock;
    }

    public record Issued(String rawToken, Instant expiresAt) {
    }

    /**
     * ออก refresh token ใหม่ — familyId = null คือเริ่ม session ใหม่ (login/register)
     * อายุ = idle-timeout ของ role นับจากตอนนี้ จึงต่ออายุไปเรื่อยๆ ทุกครั้งที่ refresh
     */
    @Transactional
    public Issued issue(Long userId, Role role, UUID familyId, String userAgent) {
        String raw = TokenHashing.randomToken();
        Instant now = Instant.now(clock);
        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setTokenHash(TokenHashing.sha256Hex(raw));
        t.setFamilyId(familyId != null ? familyId : UUID.randomUUID());
        t.setCreatedAt(now);
        t.setExpiresAt(now.plus(jwt.forRole(role).idleTimeout()));
        t.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length())));
        repository.save(t);
        return new Issued(raw, t.getExpiresAt());
    }

    @Transactional
    public Optional<RefreshToken> findForUpdate(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
            return Optional.empty();
        }
        return repository.findByHashForUpdate(TokenHashing.sha256Hex(rawToken));
    }

    @Transactional
    public void revokeFamily(UUID familyId) {
        repository.revokeFamily(familyId, Instant.now(clock));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId, Instant.now(clock));
    }

    /** ลบ token ที่หมดอายุเกิน 7 วัน ทุกวันตี 3 */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Bangkok")
    @SchedulerLock(name = "refreshTokenCleanup")
    @Transactional
    public void cleanupExpired() {
        repository.deleteExpiredBefore(Instant.now(clock).minus(Duration.ofDays(7)));
    }
}
