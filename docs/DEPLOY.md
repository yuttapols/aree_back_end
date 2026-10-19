# Deploy: Render (backend) + Neon (DB) + Cloudinary (รูป)

ค่าใช้จ่าย: Render Starter $7/เดือน · Neon / Cloudinary ใช้แผนฟรี

---

## 0. ก่อนเริ่ม (ทำครั้งเดียว)

- [ ] **Cloudinary: regenerate API secret** (ตัวเดิมหลุดในแชท) → Dashboard > Settings > API Keys
- [ ] เตรียม `JWT_SECRET` ใหม่: `openssl rand -base64 48` (ห้ามใช้ค่าเดียวกับ dev)
- [ ] เตรียมเบอร์เจ้าของร้าน + รหัสชั่วคราวที่เดายาก (≥ 8 ตัว มีตัวอักษร+ตัวเลข) — **ห้ามใช้ `admin@1234`**
- [ ] commit + push โค้ดขึ้น GitHub (Render ดึงจาก repo)

## 1. Neon (Database)

1. สร้าง Project ใหม่ → region **AWS Asia Pacific (Singapore)** · Postgres 17/18
2. Dashboard > **Connect** → ปิดตัวเลือก **Connection pooling** (ใช้ host แบบ direct — Flyway ใช้ advisory lock ซึ่งไม่ทำงานผ่าน pooler)
3. แปลง connection string เป็นรูปแบบ JDBC:

   ```
   ได้จาก Neon:  postgresql://USER:PASSWORD@ep-xxxx.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   DB_URL      = jdbc:postgresql://ep-xxxx.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
   DB_USERNAME = USER
   DB_PASSWORD = PASSWORD
   ```

> ⚠️ **ต้องใช้ DB ใหม่ที่ว่างเปล่า** (project หรือ branch ใหม่ใน Neon) — ตาราง + เมนูเริ่มต้นจะถูกสร้างอัตโนมัติด้วย Flyway ตอน start ครั้งแรก
> ถ้า DB เคยรันแอปแบบ dev มาก่อน (มีข้อมูลตัวอย่าง `dev seed` + ADMIN `0800000000`) แอปแบบ prod จะ **start ไม่ขึ้น**
> (`FlywayValidateException: Detected applied migration not resolved locally: dev seed`) และค่า `ROTI_ADMIN_*` ใหม่จะไม่ถูกใช้

## 2. Render (Backend)

1. Dashboard > **New > Blueprint** → เลือก repo นี้ (ใช้ `render.yaml`)
   — หรือ New > Web Service > Docker, Plan **Starter**, Region **Singapore**, Health check path `/actuator/health`
2. กรอก Environment ที่ `sync: false`:

   | Key | ค่า |
   |---|---|
   | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | จากข้อ 1 |
   | `JWT_SECRET` | จากข้อ 0 |
   | `CORS_ALLOWED_ORIGINS` | URL หน้าร้านจริง เช่น `https://roti5dao.vercel.app` (หลายค่าคั่นด้วย `,` · ห้าม `*` · ห้ามมี `/` ท้าย) |
   | `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | ตัวใหม่จากข้อ 0 |
   | `ROTI_ADMIN_PHONE`, `ROTI_ADMIN_PASSWORD` | เบอร์เจ้าของร้าน + รหัสชั่วคราว |
   | `REFRESH_COOKIE_SAME_SITE` | ดูข้อ 3 |

   ไม่ต้องตั้ง: `PORT` (Render ใส่ให้), `STORAGE_DIR` (prod ใช้ Cloudinary — ถ้าไม่ตั้ง Cloudinary แอปจะไม่ยอม start)

3. Deploy → รอ log `Started Roti5DaoApplication` (build ครั้งแรก ~5–10 นาที, start ~30–60 วิ)
4. ตรวจ: `https://<service>.onrender.com/actuator/health` → `{"status":"UP"}`

## 3. หน้าบ้าน ↔ API (สำคัญ — เรื่อง login ค้าง)

refresh token อยู่ใน cookie `HttpOnly` ถ้า FE กับ API อยู่คนละโดเมน (`*.vercel.app` vs `*.onrender.com`) browser จะไม่ส่ง cookie → refresh ไม่ได้ → หลุด login ทุก 15 นาที

**แนะนำ (ทางเลือก A): ให้ FE proxy `/api` ไป Render** → browser มองเป็นโดเมนเดียวกัน, ใช้ `REFRESH_COOKIE_SAME_SITE=Strict` ได้

```jsonc
// Vercel: vercel.json
{ "rewrites": [ { "source": "/api/:path*", "destination": "https://<service>.onrender.com/api/:path*" } ] }
```
```
# Netlify: _redirects
/api/*  https://<service>.onrender.com/api/:splat  200
```
FE เรียก API ด้วย path relative `/api/v1/...` · `CORS_ALLOWED_ORIGINS` = URL ของ FE

**ทางเลือก B: ใช้ custom domain เดียวกัน** เช่น `roti5dao.com` (FE) + `api.roti5dao.com` (Render) → `Strict` ใช้ได้

**ทางเลือก C (ไม่แนะนำ): เรียกข้ามโดเมนตรงๆ** → ตั้ง `REFRESH_COOKIE_SAME_SITE=None`
— Safari / โหมดบล็อก third-party cookie จะยังหลุด login อยู่

## 4. หลัง deploy ครั้งแรก

- [ ] เจ้าของร้าน login ด้วย `ROTI_ADMIN_PHONE` + รหัสชั่วคราว → ระบบบังคับเปลี่ยนรหัส
- [ ] ลบ `ROTI_ADMIN_PASSWORD` ออกจาก Render Environment
- [ ] ตั้งค่าร้าน (เวลาเปิด, PromptPay, บัญชีโอน, กติกาแต้ม) ที่หน้า **ตั้งค่าร้าน**
- [ ] ลองอัปรูปเมนู 1 รูป → URL ต้องขึ้นต้นด้วย `https://res.cloudinary.com/`
- [ ] ลองสั่งออนไลน์ → เลือกช่องทาง → พนักงานอนุมัติ → ออเดอร์ CONFIRMED
- [ ] สร้างบัญชีพนักงานที่หน้า **พนักงาน**

## 5. ดูแลระบบ

- **Backup**: Neon ฟรีย้อนข้อมูลได้แค่ช่วงสั้น → ทำ `pg_dump` เก็บเองสัปดาห์ละครั้ง
  ```
  pg_dump "postgresql://USER:PASSWORD@ep-xxxx.ap-southeast-1.aws.neon.tech/neondb?sslmode=require" -Fc -f roti5dao-YYYYMMDD.dump
  ```
- **ขนาด DB**: `SELECT pg_size_pretty(pg_database_size(current_database()));` — แผนฟรี 0.5GB
- **Swagger บน prod** ปิดอยู่ — เปิดชั่วคราวด้วย `API_DOCS_ENABLED=true` แล้วปิดกลับ
- Neon หลับเมื่อไม่มีการใช้งาน ~5 นาที → request แรกหลังหลับช้า 1–3 วิ (ตั้ง pool ไม่ให้ ping DB ไว้แล้ว เพื่อไม่ให้กินโควต้า compute)
- Migration รันอัตโนมัติทุกครั้งที่ deploy (Flyway) — ห้ามแก้ไฟล์ `V*.sql` ที่ขึ้นไปแล้ว ให้สร้างไฟล์ใหม่แทน

## 6. ผลทดสอบก่อนขึ้น (2026-10-19)

รัน jar ด้วย `SPRING_PROFILES_ACTIVE=prod` + JVM options เดียวกับ Dockerfile (heap ≤ 300MB) บนเครื่อง:
- start ใน ~18 วินาที · `/actuator/health` = `UP` · Flyway ถึง V21
- Swagger ปิด (`/v3/api-docs` = 404) · `/api/v1/public/menu` = 200
- `/api/v1/public/payment-methods` = `CASH`, `TRANSFER`, `PROMPTPAY`
- ยังไม่ได้ทดสอบ: build Docker image (เครื่องนี้ไม่มี Docker) และต่อ Neon จริง — ดู log ของ Render ตอน deploy แรก
