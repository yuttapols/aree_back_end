-- เก็บโค้ดโปรที่ลูกค้าใส่ไว้กับออเดอร์ — ใช้คำนวณราคาใหม่เมื่อพนักงานเพิ่ม/แก้/ลบรายการ
ALTER TABLE orders ADD COLUMN promo_code VARCHAR(30);
