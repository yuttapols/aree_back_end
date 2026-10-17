-- =====================================================================
-- แยก "ลบ" ออกจาก "ปิดใช้งาน"
--   is_active  = เปิด/ปิดชั่วคราว ยังเห็นในหลังบ้าน เปิดกลับได้
--   deleted_at = ลบแล้ว หายจากทุกหน้าจอ (เก็บแถวไว้เพราะออเดอร์/ยอดขายย้อนหลังยังอ้างถึง)
-- รหัส/slug ของแถวที่ลบแล้วนำกลับมาใช้ใหม่ได้
-- =====================================================================

ALTER TABLE category     ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE product      ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE option_group ADD COLUMN deleted_at TIMESTAMPTZ;
ALTER TABLE promotion    ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE category DROP CONSTRAINT uq_category_slug;
CREATE UNIQUE INDEX uq_category_slug ON category (slug) WHERE deleted_at IS NULL;

ALTER TABLE product DROP CONSTRAINT uq_product_code;
CREATE UNIQUE INDEX uq_product_code ON product (code) WHERE deleted_at IS NULL;

DROP INDEX uq_promotion_code;
CREATE UNIQUE INDEX uq_promotion_code ON promotion (upper(code)) WHERE code IS NOT NULL AND deleted_at IS NULL;
