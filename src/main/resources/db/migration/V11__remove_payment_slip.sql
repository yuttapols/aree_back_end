-- เลิกใช้สลิปโอนเงิน — ลูกค้าเลือกช่องทางชำระเงินอย่างเดียว ร้านตรวจยอดเข้าเอง
ALTER TABLE payment DROP COLUMN slip_url;
ALTER TABLE payment_method DROP COLUMN requires_slip;
