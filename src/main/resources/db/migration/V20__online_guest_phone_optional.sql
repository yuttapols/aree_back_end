-- ลูกค้าไม่สมัคร (เช่น walk-in สแกนสั่งเอง) ไม่ต้องกรอกเบอร์ — บังคับแค่ชื่อ
ALTER TABLE orders DROP CONSTRAINT ck_orders_online_guest;
ALTER TABLE orders ADD CONSTRAINT ck_orders_online_guest
    CHECK (channel <> 'ONLINE' OR customer_id IS NOT NULL OR guest_name IS NOT NULL);
