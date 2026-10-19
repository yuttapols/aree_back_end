-- ลูกค้าออนไลน์เลือกได้ 3 ช่องทาง: เงินสด (จ่ายตอนรับที่ร้าน), โอนเงิน, พร้อมเพย์ — ไม่มีการจ่ายจริงในระบบ ร้านตรวจ/เก็บเงินเอง
UPDATE payment_method SET allow_online = TRUE  WHERE code IN ('CASH', 'TRANSFER', 'PROMPTPAY');
UPDATE payment_method SET allow_online = FALSE WHERE code = 'CARD';
