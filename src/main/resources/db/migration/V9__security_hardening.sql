-- =====================================================================
-- Security hardening (ต่อยอดจาก V1)
--  - Account lockout เมื่อ login ผิดติดกัน
--  - tokens_valid_after : access token ที่ออกก่อนเวลานี้ถูกปฏิเสธ (เปลี่ยนรหัส / ระงับ / เปลี่ยน role)
--  - refresh token family : ตรวจจับการนำ refresh token ที่ถูก rotate ไปแล้วกลับมาใช้ซ้ำ
--  - ShedLock สำหรับ scheduled job เมื่อรันหลาย instance
--  - security_audit_log : บันทึกเหตุการณ์ด้านความปลอดภัย
-- =====================================================================

ALTER TABLE app_user
    ADD COLUMN failed_login_count       INT         NOT NULL DEFAULT 0,
    ADD COLUMN locked_until             TIMESTAMPTZ,
    ADD COLUMN tokens_valid_after       TIMESTAMPTZ,
    ADD COLUMN password_change_required BOOLEAN     NOT NULL DEFAULT FALSE;

ALTER TABLE refresh_token
    ADD COLUMN family_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE INDEX ix_refresh_token_family  ON refresh_token (family_id);
CREATE INDEX ix_refresh_token_expires ON refresh_token (expires_at);

CREATE TABLE shedlock (
    name       VARCHAR(64)  PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

CREATE TABLE security_audit_log (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type VARCHAR(50)  NOT NULL,
    user_id    BIGINT       REFERENCES app_user (id) ON DELETE SET NULL,
    actor      VARCHAR(50),
    ip_address VARCHAR(64),
    detail     VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_security_audit_user    ON security_audit_log (user_id, created_at DESC);
CREATE INDEX ix_security_audit_created ON security_audit_log (created_at);
