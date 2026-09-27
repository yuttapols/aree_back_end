# ร้านโรตี 5 ดาว — Backend

Spring Boot 4.1 · Java 25 · PostgreSQL 18 · Flyway — ตามเอกสาร `aree_document/roti-5dao` (ครบ Phase 1–4)

## เริ่มใช้งาน (dev)

```bash
cp .env.example .env               # กรอก DB_PASSWORD, JWT_SECRET, ROTI_ADMIN_*
docker compose up -d postgres
# PowerShell: ตั้ง env ตาม .env แล้ว
./mvnw spring-boot:run             # profile dev (มีข้อมูลเมนูตัวอย่างจาก db/seed)
```

- **Swagger UI**: http://localhost:8080/swagger-ui.html · **OpenAPI JSON**: http://localhost:8080/v3/api-docs
  - ทุก endpoint มี tag ตาม module + summary พร้อมสิทธิ์ (🌐 public · 👤 login · 🧑‍🍳 STAFF/ADMIN · 👑 ADMIN)
  - หน้าแรกของเอกสารอธิบายรูปแบบ response, flow การ login/refresh และตาราง ErrorCode ทั้งหมด
  - กด **Authorize** ใส่ access token จาก `/auth/login` (จำค่าไว้แม้ refresh หน้า)
  - FE generate client: `npx @openapitools/openapi-generator-cli generate -i http://localhost:8080/v3/api-docs -g typescript-angular -o src/app/core/api`
    หรือใช้ไฟล์ `target/openapi.json` ที่ได้จาก `./mvnw test` (ไม่ต้องรัน server)
  - profile `prod` ปิดไว้เป็นค่าเริ่มต้น — เปิดด้วย `API_DOCS_ENABLED=true`
- ADMIN คนแรกสร้างจาก `ROTI_ADMIN_PHONE` / `ROTI_ADMIN_PASSWORD` และ **ต้องเปลี่ยนรหัสเมื่อ login ครั้งแรก**
- dev ถ้าไม่ตั้ง `JWT_SECRET` ระบบสุ่ม key ชั่วคราวให้ (restart แล้ว token เดิมใช้ไม่ได้) — `prod` บังคับต้องตั้ง

ทดสอบ: `./mvnw test` — ใช้ PostgreSQL จริงแบบ embedded (zonky) ไม่ต้องมี Docker

## โครงสร้าง

```
com.roti5dao
├── common/     config, security, web (ApiResponse/ErrorCode), storage, setting, sequence, audit
├── auth/       register / login / refresh / logout, refresh token
├── user/       /me, profile, admin customers / staff / settings, shop-info
├── catalog/    เมนู public + CRUD หมวด/สินค้า/ตัวเลือก/รูป
├── payment/    ประเภทการจ่ายเงิน + บันทึกการชำระ
├── order/      pricing pipeline, ออเดอร์ POS/ออนไลน์, state machine, สลิป, คิวครัว
├── point/      ledger แต้ม (FIFO), earn/redeem/reverse/adjust, job หมดอายุ
├── promotion/  promotion engine (PERCENT / FIXED / BUY_X_GET_Y / POINT_MULTIPLIER)
└── dashboard/  รายงานจาก view V8
```

Module คุยกันผ่าน service เท่านั้น · Phase 4 เสียบเข้า order ผ่าน `PricingStep` + `OrderPlaced/Completed/CancelledEvent` โดยไม่ต้องแก้ `OrderService`

## Database

`src/main/resources/db/migration` — V1–V8 ตามเอกสาร (ไม่แก้) + **V9__security_hardening.sql**
(lockout, `tokens_valid_after`, refresh-token family, ตาราง `shedlock`, `security_audit_log`)

## Security

| เรื่อง | การทำงาน |
|---|---|
| รหัสผ่าน | BCrypt (12), ≥ 8 ตัว มีตัวอักษร+ตัวเลข, ≤ 72 byte |
| Access token | JWT HS256 อายุสั้นตาม role (ลูกค้า 5 นาที, พนักงาน/เจ้าของ 15 นาที) ตรวจ iss/aud/exp; ทุก request เช็กสถานะบัญชี/role ปัจจุบันจาก DB (cache 30 วิ) |
| Refresh token | สุ่ม 256-bit เก็บเฉพาะ SHA-256, cookie `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`, rotate ทุกครั้ง, ใช้ซ้ำ = revoke ทุก session; ไม่ได้ใช้งานเกิน 15 นาที (ลูกค้า) / 12 ชม. (พนักงาน/เจ้าของ) ต้อง login ใหม่ |
| Revocation | เปลี่ยนรหัส / ระงับบัญชี / เปลี่ยน role → access token เดิมใช้ไม่ได้ทันที (`tokens_valid_after`) |
| Brute force | ล็อกบัญชี 15 นาทีเมื่อผิด 5 ครั้ง, rate limit ต่อ IP ที่ login/register/refresh/สั่งออนไลน์/แนบสลิป, เวลาตอบสนองเท่ากันแม้ไม่มีบัญชี |
| สิทธิ์ | ADMIN-only ตรวจที่ URL (ก่อนอ่าน body) + `@PreAuthorize`; ข้อมูลของตัวเอง userId มาจาก token เท่านั้น; ออเดอร์คนอื่นตอบ 404 |
| บังคับเปลี่ยนรหัส | ADMIN คนแรก / พนักงานที่แอดมินสร้าง / ลูกค้าที่ได้รหัสชั่วคราว ใช้งานไม่ได้จนกว่าจะเปลี่ยน |
| ราคา | BE คำนวณเองทุกครั้ง ไม่เชื่อราคาจาก client; โควต้าโปร/แต้ม ใช้ atomic update + row lock |
| ไฟล์ | ตรวจ magic bytes (JPG/PNG/WEBP), ≤ 5MB, ตั้งชื่อใหม่ด้วย UUID, กัน path traversal, **สลิปเก็บ private** ดูได้เฉพาะพนักงาน (`no-store`) |
| HTTP | CORS เฉพาะ origin ที่กำหนด, CSP `default-src 'none'`, HSTS, nosniff, frame DENY, no-referrer; refresh/logout ตรวจ Origin |
| Error | ไม่ส่ง stack trace/ข้อความภายใน, ตอบ `ApiResponse` + `ErrorCode` เสมอ |
| Audit | `security_audit_log`: login สำเร็จ/ล้มเหลว, token reuse, เปลี่ยนรหัส, เปลี่ยน role/ระงับ, ตั้งค่า, ตรวจสลิป, ยกเลิกออเดอร์, ปรับแต้ม |

**Production checklist**: `SPRING_PROFILES_ACTIVE=prod`, ตั้ง `JWT_SECRET` (`openssl rand -base64 48`), รันหลัง HTTPS reverse proxy (`forward-headers-strategy: native` — ตั้ง proxy ให้เขียนทับ `X-Forwarded-For`), `CORS_ALLOWED_ORIGINS` = โดเมนจริง, ถ้ารันหลาย instance ให้ย้าย rate limit ไป Redis/proxy

## ส่วนที่ต่างจากเอกสาร

- ใช้ Maven (เอกสารระบุ Gradle หรือ Maven ได้) และ map DTO ด้วย static factory ใน record แทน MapStruct
- JWT ใช้ Spring Security OAuth2 Resource Server (Nimbus) แทนการเขียน `JwtAuthFilter` เอง
- เพิ่ม `POST /admin/orders/quote` (POS คำนวณราคาพร้อมเบอร์สมาชิก), `GET /admin/payments/{id}/slip`, `DELETE /admin/products/{id}/images/{imageId}`, `GET /admin/customers/{id}`
- `POST /files` จำกัดเฉพาะ STAFF/ADMIN (ลูกค้าอัปโหลดได้เฉพาะ avatar)
- ยกเลิกออเดอร์คืนโควต้าโปรโมชั่นด้วย
- Test ใช้ zonky embedded-postgres แทน Testcontainers (เครื่องนี้ไม่มี Docker)
