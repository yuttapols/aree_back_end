-- ลำดับหน้าร้าน: สั่ง → กำลังทำ → พร้อมรับ → ชำระเงิน → รับอาหาร — เก็บเวลาแต่ละขั้นไว้แสดง timeline
ALTER TABLE orders
    ADD COLUMN preparing_at TIMESTAMPTZ,
    ADD COLUMN ready_at     TIMESTAMPTZ;
