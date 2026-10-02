-- =====================================================================
-- Default เมนูร้าน "โรตี 5 ดาว 15 รส" — รันทุก environment (dev/SIT/prod)
-- ราคาอ้างอิงจากป้ายเมนูหน้าร้านจริง ณ วันที่สร้าง migration นี้
-- idempotent: ใช้ ON CONFLICT / NOT EXISTS ทั้งหมด รันซ้ำได้ไม่สร้างข้อมูลซ้ำ
-- =====================================================================

-- ---------- หมวดหมู่: โรตี ----------
INSERT INTO category (name, slug, icon, sort_order, created_by) VALUES
    ('โรตี', 'roti', 'pi pi-star', 1, 'system')
ON CONFLICT (slug) DO NOTHING;

-- ---------- สินค้า: เมนูโรตีหลัก ----------
INSERT INTO product (category_id, code, name, description, price, is_recommended, sort_order, created_by)
SELECT c.id, v.code, v.name, v.description, v.price, v.recommended, v.sort_order, 'system'
FROM (VALUES
    ('R5D-PLAIN',      'ธรรมดา',                NULL,                                     15.00, FALSE, 1),
    ('R5D-EGG',        'โรตีใส่ไข่',             NULL,                                     25.00, TRUE,  2),
    ('R5D-PLAIN-SP',   'ธรรมดาพิเศษ',           NULL,                                     40.00, FALSE, 3),
    ('R5D-EGG-SP',     'โรตีใส่ไข่ พิเศษ',       NULL,                                     35.00, FALSE, 4),
    ('R5D-EGG-BANANA', 'โรตีใส่ไข่ ใส่กล้วย',    NULL,                                     40.00, TRUE,  5),
    ('R5D-SIGNATURE',  'โรตี 5 ดาว 15 รส',       'ใส่ไข่ ใส่กล้วย ใส่แยม ใส่ช็อกโกแลต — เมนูซิกเนเจอร์ของร้าน', 45.00, TRUE, 6)
) AS v(code, name, description, price, recommended, sort_order)
JOIN category c ON c.slug = 'roti'
ON CONFLICT (code) DO NOTHING;

-- ---------- กลุ่มตัวเลือก: ท็อปปิ้ง (เลือกได้หลายอย่าง ไม่บังคับ) ----------
INSERT INTO option_group (name, min_select, max_select, sort_order, created_by)
SELECT 'ท็อปปิ้ง', 0, 10, 1, 'system'
WHERE NOT EXISTS (SELECT 1 FROM option_group WHERE name = 'ท็อปปิ้ง');

INSERT INTO option_item (option_group_id, name, extra_price, sort_order)
SELECT g.id, v.name, v.price, v.sort_order
FROM option_group g
CROSS JOIN (VALUES
    ('แยมบลูเบอร์รี่',      20.00, 1),
    ('แยมวนิลา',            20.00, 2),
    ('แยมช็อกโกแลต',        20.00, 3),
    ('แยมส้ม',               20.00, 4),
    ('สังขยาใบเตย',         20.00, 5),
    ('แยมสับปะรด',          20.00, 6),
    ('แยมสตรอว์เบอร์รี่',   20.00, 7),
    ('โอวัลตินลูกเกด',      25.00, 8),
    ('น้ำพริกเผา',          20.00, 9),
    ('เม็ดเจ็ดสี',          20.00, 10),
    ('เนยสด',               20.00, 11),
    ('ไมโล',                20.00, 12)
) AS v(name, price, sort_order)
WHERE g.name = 'ท็อปปิ้ง'
  AND NOT EXISTS (SELECT 1 FROM option_item oi WHERE oi.option_group_id = g.id AND oi.name = v.name);

-- ---------- ผูกกลุ่มท็อปปิ้งเข้ากับสินค้าทุกตัวในหมวดโรตี ----------
INSERT INTO product_option_group_map (product_id, option_group_id)
SELECT p.id, g.id
FROM product p
JOIN category c ON c.id = p.category_id AND c.slug = 'roti'
CROSS JOIN option_group g
WHERE g.name = 'ท็อปปิ้ง'
ON CONFLICT DO NOTHING;
