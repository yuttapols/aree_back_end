-- กลุ่มตัวเลือกที่ติ๊กไว้ให้ก่อน (เช่น "ใส่นมข้น" ฟรี) — ลูกค้าเอาออกได้ → FE แสดง "ไม่ใส่…"
ALTER TABLE option_group ADD COLUMN default_selected BOOLEAN NOT NULL DEFAULT FALSE;
