package com.roti5dao.auth.service;

import com.roti5dao.common.security.UserSecurityStateService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * ยกเลิกทุก session ของผู้ใช้: revoke refresh token ทั้งหมด + ตั้ง tokens_valid_after
 * ทำให้ access token ที่ออกไปก่อนหน้าใช้ไม่ได้ทันที (ไม่ต้องรอหมดอายุ 15 นาที)
 */
@Service
public class SessionRevocationService {

    private final RefreshTokenService refreshTokenService;
    private final UserSecurityStateService stateService;
    private final JdbcClient jdbc;
    private final Clock clock;

    public SessionRevocationService(RefreshTokenService refreshTokenService, UserSecurityStateService stateService,
                                    JdbcClient jdbc, Clock clock) {
        this.refreshTokenService = refreshTokenService;
        this.stateService = stateService;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** @return เวลาที่ token ใหม่ต้องออกหลังจากนี้ (iat >= ค่านี้) */
    @Transactional
    public Instant revokeAllSessions(Long userId) {
        Instant validAfter = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        refreshTokenService.revokeAllForUser(userId);
        jdbc.sql("UPDATE app_user SET tokens_valid_after = :t WHERE id = :id")
                .param("t", java.sql.Timestamp.from(validAfter))
                .param("id", userId)
                .update();
        evictAfterCommit(userId);
        return validAfter;
    }

    public void evictAfterCommit(Long userId) {
        stateService.evict(userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stateService.evict(userId);
                }
            });
        }
    }
}
