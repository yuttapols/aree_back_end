-- POS "ยังไม่ชำระ": ออเดอร์หน้าร้านเข้าคิวครัวได้ก่อน แล้วค่อยเก็บเงิน — TRUE ระหว่างที่ยังค้างชำระ (ชำระครบแล้วกลับเป็น FALSE)
ALTER TABLE orders ADD COLUMN pay_later BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX ix_orders_pay_later ON orders (created_at) WHERE pay_later;
