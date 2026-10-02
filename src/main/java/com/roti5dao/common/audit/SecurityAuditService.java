package com.roti5dao.common.audit;

import com.roti5dao.common.security.CurrentUser;
import com.roti5dao.common.util.ClientIp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * บันทึกเหตุการณ์ด้านความปลอดภัยลง security_audit_log (ห้ามใส่รหัสผ่าน / token ลงใน detail)
 * <ul>
 *   <li>ถ้ามี transaction อยู่ → เขียนหลัง commit (รายการที่ rollback จะไม่ถูกบันทึก และ FK ไปยัง user ที่เพิ่งสร้างใช้ได้)</li>
 *   <li>ถ้าไม่มี transaction → เขียนทันที</li>
 * </ul>
 * เหตุการณ์ที่ต้องบันทึกแม้ธุรกรรมล้มเหลว (login ผิด, token reuse) ใช้ noRollbackFor ที่ฝั่ง service จึง commit เสมอ
 */
@Service
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    public enum Event {
        LOGIN_SUCCESS, LOGIN_FAILED, LOGIN_LOCKED, LOGOUT, REGISTER,
        REFRESH_TOKEN_REUSE, PASSWORD_CHANGED,
        USER_CREATED, USER_UPDATED, USER_ROLE_CHANGED, USER_SUSPENDED,
        SETTINGS_UPDATED, PAYMENT_VERIFIED, PAYMENT_REJECTED, ORDER_CANCELLED, POINT_ADJUSTED
    }

    private record Entry(Event event, Long userId, String actor, String ip, String detail) {
    }

    private final JdbcClient jdbc;
    private final TransactionTemplate newTx;

    public SecurityAuditService(JdbcClient jdbc, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(Event event, Long userId, String detail) {
        Entry entry = new Entry(event, userId, currentActor(), truncate(ClientIp.current(), 64), truncate(detail, 500));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write(entry);
                }
            });
        } else {
            write(entry);
        }
    }

    private void write(Entry e) {
        try {
            newTx.executeWithoutResult(s -> jdbc.sql("""
                            INSERT INTO security_audit_log (event_type, user_id, actor, ip_address, detail)
                            VALUES (:event, :userId, :actor, :ip, :detail)
                            """)
                    .param("event", e.event().name())
                    .param("userId", e.userId())
                    .param("actor", e.actor())
                    .param("ip", e.ip())
                    .param("detail", e.detail())
                    .update());
        } catch (RuntimeException ex) {
            // audit ล้มเหลวต้องไม่ทำให้ธุรกรรมหลักล้ม แต่ต้อง log ไว้
            log.error("Failed to write security audit log event={} userId={}", e.event(), e.userId(), ex);
        }
    }

    private static String currentActor() {
        return CurrentUser.actorOr("anonymous");
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
