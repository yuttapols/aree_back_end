package com.roti5dao.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * สถานะผู้ใช้ที่ต้องตรวจทุก request (ACTIVE?, role ปัจจุบัน, tokens_valid_after)
 * cache สั้นๆ 30 วินาที — ระงับบัญชี/เปลี่ยน role/เปลี่ยนรหัสผ่าน มีผลภายในไม่เกิน 30 วินาที
 * (และทันทีบน instance ที่ทำรายการ เพราะเรียก {@link #evict(Long)})
 */
@Service
public class UserSecurityStateService {

    public record State(boolean active, String role, Instant tokensValidAfter, boolean passwordChangeRequired) {
    }

    private final JdbcClient jdbc;
    private final Cache<Long, Optional<State>> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(50_000)
            .build();

    public UserSecurityStateService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<State> get(Long userId) {
        return cache.get(userId, this::load);
    }

    public void evict(Long userId) {
        cache.invalidate(userId);
    }

    private Optional<State> load(Long userId) {
        return jdbc.sql("SELECT status, role, tokens_valid_after, password_change_required FROM app_user WHERE id = :id")
                .param("id", userId)
                .query((rs, n) -> {
                    Timestamp ts = rs.getTimestamp("tokens_valid_after");
                    return new State("ACTIVE".equals(rs.getString("status")), rs.getString("role"),
                            ts == null ? null : ts.toInstant(), rs.getBoolean("password_change_required"));
                })
                .optional();
    }
}
