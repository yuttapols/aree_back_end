package com.roti5dao.auth.service;

import com.roti5dao.common.config.AppProperties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * นับการ login ผิด — ครบ N ครั้งล็อกบัญชีชั่วคราว
 * ใช้ UPDATE แบบ atomic + REQUIRES_NEW เพื่อให้บันทึกได้แม้ login ล้มเหลว และไม่ชน optimistic lock
 */
@Service
public class LoginAttemptService {

    private final JdbcClient jdbc;
    private final AppProperties.Login props;
    private final Clock clock;

    public LoginAttemptService(JdbcClient jdbc, AppProperties appProperties, Clock clock) {
        this.jdbc = jdbc;
        this.props = appProperties.security().login();
        this.clock = clock;
    }

    /** @return true ถ้าครั้งนี้ทำให้บัญชีถูกล็อก */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(Long userId) {
        Instant lockUntil = Instant.now(clock).plus(props.lockDuration());
        return jdbc.sql("""
                        UPDATE app_user
                        SET failed_login_count = CASE WHEN failed_login_count + 1 >= :max THEN 0 ELSE failed_login_count + 1 END,
                            locked_until       = CASE WHEN failed_login_count + 1 >= :max THEN :lockUntil ELSE locked_until END
                        WHERE id = :id
                        RETURNING locked_until = :lockUntil
                        """)
                .param("max", props.maxFailedAttempts())
                .param("lockUntil", Timestamp.from(lockUntil))
                .param("id", userId)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId) {
        jdbc.sql("UPDATE app_user SET failed_login_count = 0, locked_until = NULL, last_login_at = :now WHERE id = :id")
                .param("now", Timestamp.from(Instant.now(clock)))
                .param("id", userId)
                .update();
    }
}
