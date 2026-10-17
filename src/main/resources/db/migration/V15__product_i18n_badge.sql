-- ข้อมูลสินค้าเพิ่มสำหรับหน้าร้าน: ชื่อ/คำอธิบายภาษาอังกฤษ, ราคาก่อนลด (ขีดฆ่า), ป้าย
ALTER TABLE product
    ADD COLUMN name_en        VARCHAR(150),
    ADD COLUMN description_en VARCHAR(1000),
    ADD COLUMN original_price NUMERIC(7,2),
    ADD COLUMN badge          VARCHAR(20),
    ADD CONSTRAINT ck_product_badge CHECK (badge IS NULL OR badge IN ('bestseller', 'new', 'recommended')),
    ADD CONSTRAINT ck_product_original_price CHECK (original_price IS NULL OR original_price > price);

ALTER TABLE option_group ADD COLUMN name_en VARCHAR(100);
ALTER TABLE option_item  ADD COLUMN name_en VARCHAR(100);
