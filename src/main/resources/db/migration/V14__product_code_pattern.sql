-- รหัสเมนูรูปแบบเดียว: RT- + id ประเภท 3 หลัก + id เมนู 4 หลัก (เช่น RT-0010015) — ระบบ gen ให้ แก้ไม่ได้
UPDATE product SET code = 'RT-' || lpad(category_id::text, 3, '0') || lpad(id::text, 4, '0');
