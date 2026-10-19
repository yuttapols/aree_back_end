# Prompt สำหรับทีมหน้าบ้าน — Roti5Dao API (v1)

คุณคือ Frontend Developer (Angular, dev origin `http://localhost:4200`) ที่จะต่อ API ของร้านโรตี "Roti5Dao"
ให้ทำตามเอกสารนี้ทั้งหมด: เข้าใจสัญญา (contract) ของ API, สร้าง service/interceptor/guard และหน้าจอตาม role
Swagger UI ดูได้ที่ `/swagger-ui.html` (เปิดเฉพาะ dev)

---

## 1. กติกากลาง (อ่านก่อนเขียนโค้ด)

**Base URL:** `{API_HOST}/api/v1` · ไฟล์รูปสาธารณะ: `{API_HOST}/files/...`

**Envelope ทุก response (JSON)**
```json
// สำเร็จ
{ "success": true, "data": { ... }, "error": null, "timestamp": "2026-09-28T03:00:00Z" }
// ผิดพลาด
{ "success": false, "data": null,
  "error": { "code": "VALIDATION_ERROR", "message": "ข้อมูลไม่ถูกต้อง",
             "fields": [ { "field": "phone", "message": "..." } ] },
  "timestamp": "..." }
```
- ให้ unwrap `data` ใน interceptor/service กลาง
- `error.code` คือสิ่งที่ FE ใช้ map เป็นข้อความ/พฤติกรรม, `error.fields[]` ใช้ผูกกับ form field

**Paging** — query `?page=0&size=20` (page เริ่ม 0, size สูงสุด 100, ค่าเริ่มต้น 20) → `data` เป็น
```json
{ "items": [ ... ], "page": 0, "size": 20, "totalItems": 135, "totalPages": 7 }
```

**ชนิดข้อมูล:** เงินเป็น `BigDecimal` (ส่ง/รับเป็น number ทศนิยม 2 ตำแหน่ง ห้ามคำนวณยอดเองด้วย float — ใช้ค่าจาก `quote`),
เวลาเป็น ISO-8601 UTC (`Instant`) แสดงผลเป็น Asia/Bangkok, วันที่ล้วนเป็น `YYYY-MM-DD`

**Enums**
| ชื่อ | ค่า |
|---|---|
| Role | `CUSTOMER`, `STAFF`, `ADMIN` |
| OrderStatus | `PENDING_PAYMENT`, `CONFIRMED`, `PREPARING`, `READY`, `COMPLETED`, `CANCELLED` |
| OrderChannel | `WALK_IN`, `ONLINE` |
| PaymentStatus | `PENDING`, `PAID`, `REJECTED`, `REFUNDED` |
| Gender | `MALE`, `FEMALE`, `OTHER` |
| UserStatus | (ใช้ใน customer/staff — มี ACTIVE/SUSPENDED ตามที่ backend ส่ง, ให้ดูค่าจริงจาก Swagger) |
| PromotionType | `PERCENT`, `FIXED_AMOUNT`, `BUY_X_GET_Y`, `POINT_MULTIPLIER` |
| PromotionScope | `ORDER`, `PRODUCT`, `CATEGORY` |
| PromotionChannel | `ALL`, `WALK_IN`, `ONLINE` |
| GroupBy (dashboard) | `DAY`, `WEEK`, `MONTH` |
| StorageFolder | `PRODUCTS`, `AVATARS`, `BANNERS` |

**สัญลักษณ์สิทธิ์:** 🌐 ไม่ต้อง login · 👤 login ทุก role · 🧑‍🍳 STAFF+ADMIN · 👑 ADMIN เท่านั้น

---

## 2. การยืนยันตัวตน (สำคัญที่สุด)

- Login/Register/Refresh ได้ **access token (JWT)** ใน body และ **refresh token เป็น HttpOnly cookie `r5d_rt`**
  (Secure, SameSite=Strict, path `/api/v1/auth`) — **JS อ่าน cookie นี้ไม่ได้ และห้ามพยายามเก็บ refresh token เอง**
- เก็บ access token **ใน memory เท่านั้น** (ห้าม localStorage/sessionStorage) แล้วแนบทุก request: `Authorization: Bearer <accessToken>`
- ทุก call ไปที่ `/api/v1/auth/*` ต้องตั้ง `withCredentials: true` (HttpClient `withCredentials`) เพื่อให้ cookie ถูกส่ง
  และ backend ตั้ง CORS `allowCredentials=true` แล้ว (origin ต้องตรงกับที่ backend อนุญาต ห้ามใช้ `*`)
- อายุ: CUSTOMER access 5 นาที / idle 15 นาที · STAFF และ ADMIN access 15 นาที / idle 12 ชม.
  → ต้องทำ **HTTP Interceptor**: เมื่อได้ 401 `TOKEN_EXPIRED` ให้เรียก `POST /auth/refresh` **ครั้งเดียว** (queue request อื่นรอ)
  แล้ว retry; ถ้า refresh ล้มเหลว (401) → ล้าง state แล้วเด้งไปหน้า login
- refresh เป็นแบบ rotate ทุกครั้ง — ห้ามยิง refresh ซ้อนหลายตัวพร้อมกัน
- ตอนเปิดแอป/รีเฟรชหน้า: ยิง `POST /auth/refresh` เพื่อกู้ session (ถ้าได้ 401 = ยังไม่ได้ login)
- ถ้า `MeResponse.passwordChangeRequired == true` (พนักงานที่แอดมินสร้าง / ลูกค้าที่ POS สมัครให้ด้วยรหัสชั่วคราว) →
  บังคับไปหน้าเปลี่ยนรหัสผ่านทันที; API อื่นจะตอบ `403 PASSWORD_CHANGE_REQUIRED` จนกว่าจะเปลี่ยน (ยกเว้นเส้นเปลี่ยนรหัสและ auth)
- Guard ตาม role: `/admin/**` ใช้ role STAFF/ADMIN, เมนูที่มี 👑 ซ่อนจาก STAFF (แต่ backend บังคับสิทธิ์จริงอยู่แล้ว ตอบ 403 `FORBIDDEN`)

### Auth endpoints
| Method | Path | ทำอะไร | Request | Response `data` |
|---|---|---|---|---|
| POST | `/auth/register` 🌐 | สมัครสมาชิก (201) | `{ phone (≤20), password (≤100), nickname (≤50), email? }` | `TokenResponse` |
| POST | `/auth/login` 🌐 | เข้าสู่ระบบด้วยเบอร์หรืออีเมล | `{ username, password }` | `TokenResponse` |
| POST | `/auth/refresh` 🌐 | ขอ access token ใหม่จาก cookie | ไม่มี body (ต้องมี cookie) | `TokenResponse` |
| POST | `/auth/logout` 🌐 | ยกเลิก refresh + ลบ cookie | ไม่มี body | `null` |

```json
// TokenResponse
{ "accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 300, "expiresAt": "2026-09-28T03:05:00Z",
  "user": { /* MeResponse */ } }
```
- รหัสผ่านต้อง ≥ 8 ตัว, มีทั้งตัวอักษรและตัวเลข, ไม่เกิน 72 byte → validate ฝั่ง FE ล่วงหน้า (backend ตอบ `VALIDATION_ERROR` พร้อม `fields`)
- Login ผิด 5 ครั้งล็อก 15 นาที → `423 ACCOUNT_LOCKED`; ผิดปกติ → `401 INVALID_CREDENTIALS`
- แสดง error ถี่เกิน: `429 RATE_LIMITED` (มี header `Retry-After`)

---

## 3. ฝั่งสาธารณะ (Landing / สั่งออนไลน์) — ไม่ต้อง login

| Method | Path | ทำอะไร | Query / Request | Response `data` |
|---|---|---|---|---|
| GET | `/public/shop-info` | ชื่อร้าน เวลาเปิดปิด สถานะเปิดอยู่ไหม | – | `{ name, phone, openTime, closeTime, acceptOnlineOrder, openNow }` |
| GET | `/public/categories` | หมวดที่เปิดใช้ (เรียง sortOrder) | – | `CategoryResponse[]` |
| GET | `/public/products` | รายการสินค้า | `categoryId?, keyword?, recommended?` | `ProductSummary[]` |
| GET | `/public/products/{id}` | รายละเอียด + รูป + ตัวเลือก | – | `ProductDetail` |
| GET | `/public/menu` | หมวด + สินค้าในหมวด (เรียกครั้งเดียวสำหรับ landing) | – | `MenuCategory[]` |
| GET | `/public/promotions` | โปรที่แสดงบน landing | – | `PublicPromotion[]` |
| GET | `/public/promotions/{id}` | รายละเอียดโปร | – | `PublicPromotion` |
| POST | `/public/promotions/validate` | ตรวจโค้ดโปรกับตะกร้า (ผลเหมือน quote) | `QuoteRequest` | `QuoteResponse` |
| GET | `/public/payment-methods` | ช่องทางชำระเงิน | `channel=ONLINE` (default) หรือ `ALL` | `PublicPaymentMethod[]` |
| POST | `/public/orders/quote` | คำนวณราคาตะกร้า (ไม่บันทึก) | `QuoteRequest` | `QuoteResponse` |
| POST | `/public/orders` | สร้างออเดอร์ ONLINE (201) | `OnlineOrderRequest` | `OrderResponse` |
| GET | `/public/orders/track/{trackingToken}` | ติดตามออเดอร์ | UUID | `TrackResponse` |
| POST | `/public/orders/track/{trackingToken}/payments` | แจ้งช่องทางชำระเงิน (201) JSON — ไม่มีสลิป | `OnlinePaymentRequest` | `TrackResponse` |
| GET | `/files/**` | ดูรูปสาธารณะ (products/avatars/banners) | – | ไฟล์รูป |

**หมายเหตุ /public/orders/*:** ถ้าแนบ `Authorization: Bearer` จะผูกออเดอร์กับสมาชิกอัตโนมัติ (ได้แต้ม/ใช้แต้มได้)
ถ้าไม่แนบ = guest → **ต้องส่ง `guestName`** · `guestPhone` ไม่บังคับ (ไม่กรอก = null, ถ้ากรอกต้องเป็นเบอร์ไทยที่ถูกต้อง ไม่งั้น `400 VALIDATION_ERROR`) — track แสดง `maskedPhone: null`

**Models หลัก**
```jsonc
// CategoryResponse
{ "id":1, "name":"โรตี", "slug":"roti", "description":"", "icon":"cookie", "sortOrder":0, "active":true }
// ProductSummary
{ "id":10, "categoryId":1, "code":"ROTI-01", "name":"โรตีไข่", "description":"", "price":35.00,
  "imageUrl":"/files/products/2026/09/<uuid>.jpg", "available":true, "recommended":true, "hasOptions":true }
// ProductDetail = ProductSummary + categoryName + images[{id,url,sortOrder}] + optionGroups[]
// OptionGroupResponse
{ "id":3, "name":"ท็อปปิ้ง", "minSelect":0, "maxSelect":2, "defaultSelected":false, "nameEn":null, "active":true, "sortOrder":0,
  "items":[ { "id":31, "name":"นมข้น", "extraPrice":5.00, "available":true, "sortOrder":0 } ] }
// MenuCategory
{ "category": CategoryResponse, "products": ProductSummary[] }
```

**Cart / Quote**
```jsonc
// CartItemRequest (ใช้ซ้ำในทุก request ที่มี items) — items 1..50 รายการ, quantity 1..99
{ "productId":10, "quantity":2, "optionItemIds":[31,32], "note":"ไม่หวาน" }

// QuoteRequest
{ "items":[CartItemRequest], "promoCode":"WELCOME10", "redeemPoints":0 }   // promoCode?, redeemPoints? (0..10,000,000)

// QuoteResponse — ใช้แสดงยอดในตะกร้า/ชำระเงิน (FE ห้ามคำนวณเอง)
{ "items":[ { "productId","productName","unitPrice","optionsPrice","quantity","freeQuantity","lineTotal","note",
              "options":[{"groupName","name","extraPrice"}] } ],
  "subtotal", "promotionDiscount", "pointDiscount", "total",
  "pointsRedeemed", "pointsToEarn", "appliedPromotions":[ ... ], "redeemPointsPerBaht" /* กี่แต้ม = 1 บาท */ }

// OnlineOrderRequest
{ "items":[CartItemRequest], "promoCode":null, "redeemPoints":null,
  "guestName":"สมชาย", "guestPhone":"0812345678", "note":"รับ 17:00" }
```

**OrderResponse** (สร้างออเดอร์/ดูของฉัน/POS)
```jsonc
{ "id","orderNo","trackingToken":"<uuid>","channel","status","queueNo",
  "customer":{"id","nickname"}, "guestName","guestPhone",
  "items":[Line], "subtotal","promotionDiscount","pointDiscount","totalAmount","paidAmount","remainingAmount",
  "pointsRedeemed","pointsEarned","note","cashierId","payments":[PaymentResponse],
  "createdAt","confirmedAt","completedAt","cancelledAt","cancelReason" }
```
**TrackResponse** = มุมมองสาธารณะ (เบอร์ถูกมาสก์ `maskedPhone`, payments เป็น `PublicPaymentResponse` ไม่มีข้อมูลพนักงาน)
- OrderResponse / TrackResponse มี `preparingAt`, `readyAt` · `PublicPaymentResponse` = `{ id, methodCode, methodName, amount, status, rejectReason, paidAt, createdAt }`

**แจ้งช่องทางชำระเงิน (JSON, ไม่มีสลิป):** `OnlinePaymentRequest` = `{ methodCode (จำเป็น, จาก /public/payment-methods), amount?, referenceNo? }` — ร้านตรวจยอดเข้าเองแล้วกดอนุมัติ
- ช่องทางที่ `requiresReference=true` แต่ไม่ส่ง `referenceNo` → `422 REFERENCE_REQUIRED`
- มีรายการรอตรวจครบ 3 รายการแล้ว → `422 TOO_MANY_PENDING_PAYMENTS`

**Flow สั่งออนไลน์ที่ FE ต้องทำ**
1. โหลด `/public/shop-info` → ถ้า `acceptOnlineOrder=false` หรือ `openNow=false` แสดงว่าปิดรับ (ถ้ายิงสร้างจะได้ `422 ONLINE_ORDER_CLOSED`)
2. โหลด `/public/menu` → ผู้ใช้เลือกสินค้า/ตัวเลือก (บังคับ minSelect/maxSelect ที่ UI)
3. ทุกครั้งที่ตะกร้า/โค้ด/แต้มเปลี่ยน → `POST /public/orders/quote` แล้วแสดงยอดจากผลลัพธ์
4. `POST /public/orders` → เก็บ `trackingToken` (guest ให้เก็บใน URL/หน้า track เพราะไม่มี login)
5. เลือกช่องทางชำระ (`/public/payment-methods`, แสดง `promptPayId`/`bankAccount`/`instruction`) → แจ้งช่องทางที่เลือกที่ `/track/{token}/payments` (ไม่ต้องแนบสลิป)
6. หน้า Track: poll `/track/{token}` (แนะนำทุก 10–15 วินาที) แสดง `status`, `queueNo`, สถานะแต่ละ payment (`REJECTED` แสดง `rejectReason` ให้แนบใหม่)

**สถานะออเดอร์ (ร้านทำก่อน เก็บเงินตอนรับ):** `PENDING_PAYMENT` (สั่งแล้ว ยังไม่จ่าย) → `PREPARING` (กำลังทำ — ยังไม่จ่ายก็เริ่มได้) → `READY` (พร้อมรับ) → ชำระเงิน → `COMPLETED` (รับอาหารแล้ว — ต้องชำระครบ) · `CONFIRMED` = จ่ายแล้ว/ยังไม่ชำระ (payLater) แต่ยังไม่เริ่มทำ · `PREPARING` สลับคิวกลับไปรอได้ · ยกเลิกได้จนถึง `PREPARING` → `CANCELLED`
**Timeline หน้าติดตาม:** สั่งอาหาร=`createdAt` · กำลังทำ=`preparingAt` · พร้อมรับ=`readyAt` · ชำระเงิน=`remainingAmount==0` (เวลา = `payments[].paidAt` ล่าสุดที่ status PAID) · รับอาหารแล้ว=`completedAt`

---

## 4. สมาชิก (ต้อง login ทุก role) — `/me/**`
userId มาจาก token เสมอ ห้ามส่งจาก client

| Method | Path | ทำอะไร | Request | Response `data` |
|---|---|---|---|---|
| GET | `/me` 👤 | ข้อมูลผู้ใช้ปัจจุบัน | – | `MeResponse` |
| GET | `/me/profile` 👤 | ดูโปรไฟล์ | – | `ProfileResponse` |
| PUT | `/me/profile` 👤 | แก้โปรไฟล์ | `ProfileUpdateRequest` | `ProfileResponse` |
| POST | `/me/profile/avatar` 👤 | อัปโหลดรูปโปรไฟล์ (multipart `file`, JPG/PNG/WEBP ≤ 5MB) | form-data | `ProfileResponse` |
| PUT | `/me/password` 👤 | เปลี่ยนรหัสผ่าน | `{ currentPassword, newPassword }` | `TokenResponse` (+cookie ใหม่) |
| GET | `/me/orders` 👤 | ประวัติออเดอร์ของฉัน (paging) | `page,size` | `PageResponse<OrderListItem>` |
| GET | `/me/orders/{orderNo}` 👤 | รายละเอียด (ของคนอื่น = 404) | – | `OrderResponse` |
| GET | `/me/points` 👤 | แต้มคงเหลือ + ใกล้หมดอายุ | – | `PointSummary` |
| GET | `/me/points/transactions` 👤 | ประวัติแต้ม (paging) | `page,size` | `PageResponse<PointTransactionResponse>` |

```jsonc
// MeResponse
{ "id","role","phone","email","nickname","firstName","lastName","memberCode","pointsBalance","avatarUrl","passwordChangeRequired" }
// ProfileResponse
{ "phone","email","memberCode","firstName","lastName","nickname","birthDate":"1995-04-01","gender","avatarUrl","pointsBalance","lifetimePoints","memberSince" }
// ProfileUpdateRequest  (nickname จำเป็น, birthDate ต้องเป็นอดีต)
{ "firstName?","lastName?","nickname","birthDate?","gender?","email?" }
// OrderListItem
{ "id","orderNo","channel","status","queueNo","customerName","member","totalAmount","createdAt" }
// PointSummary
{ "balance","expiringSoon","nextExpiryAt","expiringWindowDays":30 }
// PointTransactionResponse
{ "id","orderId","type","points","balanceAfter","remaining","expiresAt","remark","createdAt" }
```
- เปลี่ยนรหัสผ่านสำเร็จ: session อื่นทั้งหมดถูก revoke → **ต้องเอา `accessToken` ใหม่จาก response มาแทนที่ token เดิมทันที**

---

## 5. พนักงานหน้าร้าน (POS / ครัว / ตรวจการชำระเงิน) — 🧑‍🍳 STAFF + ADMIN

### 5.1 POS / ออเดอร์ `/admin/orders`
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| POST | `/admin/orders/quote` | POS คำนวณราคา (ใส่ `customerPhone` เพื่อใช้สิทธิ์สมาชิก) | `AdminQuoteRequest` | `QuoteResponse` |
| POST | `/admin/orders` | เปิดบิล WALK_IN (201) | `WalkInOrderRequest` | `OrderResponse` |
| GET | `/admin/orders` | รายการออเดอร์ (paging) — **`queue=true`** = คิวที่ยังไม่ปิด (PENDING_PAYMENT, CONFIRMED, PREPARING, READY) เรียงตาม `queueNo` น้อย→มาก (ไม่สน `status`) | `status?, channel?, date? (YYYY-MM-DD), queue?, page, size` | `PageResponse<OrderListItem>` |
| GET | `/admin/orders/board` | คิวครัววันนี้ (PENDING_PAYMENT/CONFIRMED/PREPARING/READY — ยังไม่จ่ายก็ขึ้นคิว) | – | `BoardItem[]` |
| GET | `/admin/orders/{id}` | รายละเอียดออเดอร์ | – | `OrderResponse` |
| POST | `/admin/orders/{id}/payments` | รับเงิน (201) จ่ายผสมได้ | `StaffPaymentRequest` | `OrderResponse` |
| POST | `/admin/orders/{id}/pay-later` | เปลี่ยนเป็น "ยังไม่ชำระ": ได้เฉพาะ `PENDING_PAYMENT` → `CONFIRMED` (เข้าคิวครัว) + `payLater=true` · ใช้ได้ทั้ง WALK_IN/ONLINE · payment PENDING ที่ค้างอยู่คงไว้ | – | `OrderResponse` |
| POST | `/admin/orders/{id}/complete` | **ปิดออเดอร์ทีเดียว** (transaction เดียว): ถ้ายังค้างเงิน → บันทึกจ่ายเต็มยอดคงค้างจาก body → เดินไป `READY` → `COMPLETED` → ให้แต้ม · payment `PENDING` ที่ลูกค้าแจ้งไว้ถูกปฏิเสธอัตโนมัติ · **ไม่ส่ง body = ใช้ช่องทางที่ลูกค้าแจ้งไว้ล่าสุด** (payment PENDING) · ไม่มีทั้ง body และช่องทางที่แจ้งไว้ → `422 ORDER_INVALID_STATUS` | `{ "methodCode":"CASH", "cashReceived?":100, "referenceNo?":"" }` (ไม่บังคับ — ส่งเมื่อพนักงานเลือกช่องทางเอง/อยากได้เงินทอน) | `OrderResponse` |
| POST | `/admin/orders/{id}/items` | เพิ่มรายการ (201) — เฉพาะ WALK_IN, สถานะ PENDING_PAYMENT/CONFIRMED, ไม่ได้ใช้แต้มแลก · คำนวณราคาใหม่ทั้งบิล | `CartItemRequest` | `OrderResponse` |
| PUT | `/admin/orders/{id}/items/{itemId}` | แก้รายการ (เงื่อนไขเดียวกัน) | `CartItemRequest` | `OrderResponse` |
| DELETE | `/admin/orders/{id}/items/{itemId}` | ลบรายการ (ลบรายการสุดท้ายไม่ได้) | – | `OrderResponse` |
| PATCH | `/admin/orders/{id}/status` | `PREPARING` (เริ่มทำ ยังไม่จ่ายได้) / `READY` / `COMPLETED` (ต้องชำระครบ) · **สลับคิว**: จาก `PREPARING` ส่ง `PENDING_PAYMENT` หรือ `CONFIRMED` → ระบบเลือกให้เอง (จ่ายครบหรือ payLater = `CONFIRMED`, ไม่งั้น `PENDING_PAYMENT`) | `{ "status": "PREPARING" }` | `OrderResponse` |
| POST | `/admin/orders/{id}/cancel` | ยกเลิก (คืนเงิน/แต้ม/โควต้าโปร) | `{ "reason": "..." }` (จำเป็น) | `OrderResponse` |
| GET | `/admin/orders/{id}/receipt` | ข้อมูลใบเสร็จสำหรับพิมพ์ | – | `ReceiptResponse` |

```jsonc
// AdminQuoteRequest
{ "items":[CartItemRequest], "promoCode?", "redeemPoints?", "customerPhone?" }
// WalkInOrderRequest
{ "items":[CartItemRequest], "promoCode?", "redeemPoints?", "customerPhone?", "guestName?", "note?",
  "payLater?": true /* "ยังไม่ชำระ": เข้าคิวครัวทันที (CONFIRMED) เก็บเงินทีหลังผ่าน /payments */ }
// OrderListItem
{ "id","orderNo","channel","status","queueNo","customerName","member","totalAmount",
  "remainingAmount" /* ยอดค้าง = total − ที่ PAID แล้ว */, "createdAt","payLater" }
// OrderResponse.items[] = Line { "id" /* itemId สำหรับแก้/ลบ — เปลี่ยนใหม่ทุกครั้งที่แก้ */, "productId", ...,
//   "options":[{ "optionItemId","groupName","name","extraPrice" }] }
// StaffPaymentRequest — amount ว่าง = ชำระยอดคงค้างทั้งหมด, CASH ส่ง cashReceived เพื่อให้ระบบคำนวณเงินทอน
{ "methodCode":"CASH", "amount?":100.00, "cashReceived?":500.00, "referenceNo?":"" }
// PaymentResponse
{ "id","orderId","methodCode","methodName","amount","cashReceived","changeAmount","referenceNo",
  "status","paidAt","verifiedBy","rejectReason","createdAt" }
// BoardItem
{ "id","orderNo","channel","status","queueNo","customerName","items":[Line],"note","createdAt","confirmedAt","preparingAt","readyAt","payLater" }
// ReceiptResponse
{ "shopName","shopPhone","order":OrderResponse,"cashierName","printedAt" }
```
สิ่งที่ FE ต้องจัดการ: หน้า POS (ค้นสินค้า→ตะกร้า→quote→เปิดบิล→รับเงิน→พิมพ์ใบเสร็จ), หน้าคิวครัว (poll `/board` ทุก ~5–10 วิ,
ปุ่มเปลี่ยนสถานะตามลำดับที่ถูกต้อง — ถ้าผิดลำดับจะได้ `422 ORDER_INVALID_STATUS`), ใบเสร็จเรียก `window.print()` จาก `ReceiptResponse`
ยอด `PAYMENT_AMOUNT_MISMATCH` ให้แสดงข้อความและให้กรอกใหม่

### 5.2 ตรวจการชำระเงิน `/admin/payments` (ร้านเช็กยอดเข้าในแอปธนาคารเอง)
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| GET | `/admin/payments` | รายการชำระเงิน (`status=PENDING` = รอตรวจ) | `status?, page, size` | `PageResponse<PendingPaymentItem>` |
| PATCH | `/admin/payments/{id}/verify` | อนุมัติ/ปฏิเสธ — ยอดครบ → ออเดอร์เป็น CONFIRMED | `{ "approve": true, "rejectReason?": "..." }` | `PaymentResponse` |

- `PendingPaymentItem` = `{ payment:PaymentResponse, orderId, orderNo, orderStatus, orderTotal, customerName }`
- ปฏิเสธควรบังคับกรอก `rejectReason` ที่ UI

### 5.3 ลูกค้าหน้าร้าน `/admin/customers`
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| GET | `/admin/customers` | ค้นหาสมาชิก (เบอร์/ชื่อ/อีเมล/รหัสสมาชิก) | `keyword?, page, size` | `PageResponse<CustomerSummary>` |
| GET | `/admin/customers/lookup` | หาสมาชิกจากเบอร์ (ใช้ที่ POS) | `phone` (จำเป็น) | `CustomerSummary` |
| GET | `/admin/customers/{id}` | ข้อมูลสมาชิก | – | `CustomerSummary` |
| POST | `/admin/customers/quick-register` | สมัครให้ลูกค้าหน้าร้าน (201) | `{ nickname, phone, password? }` | `{ customer, temporaryPassword }` |
| GET | `/admin/customers/{id}/points/transactions` | ประวัติแต้มลูกค้า | `page,size` | `PageResponse<PointTransactionResponse>` |

- `CustomerSummary` = `{ userId, memberCode, phone, email, nickname, firstName, lastName, pointsBalance, status, memberSince }`
- **`temporaryPassword` แสดงครั้งเดียว** (เมื่อไม่ได้ใส่ password) → แสดงใน dialog พร้อมปุ่มคัดลอก, ห้ามเก็บ/log หลังปิด

### 5.4 อื่นๆ ที่ STAFF ใช้ได้
| Method | Path | ทำอะไร | Response |
|---|---|---|---|
| GET | `/admin/dashboard/today` | สรุปวันนี้ (หน้าแรกพนักงาน) | `Today` |
| GET | `/admin/products` | รายการสินค้า (paging) `categoryId?, keyword?, active?, page, size` | `PageResponse<AdminProductResponse>` |
| GET | `/admin/products/{id}` | รายละเอียดสินค้า | `AdminProductResponse` |
| PATCH | `/admin/products/{id}/availability` | ของหมด/มีของ — body `{ "available": false }` | `AdminProductResponse` |
| GET | `/admin/payment-methods` | ช่องทางชำระทั้งหมด (ใช้ที่ POS) | `PaymentMethodResponse[]` |

`Today` = `{ date, ordersByStatus:{ "CONFIRMED":3, ... }, completedCount, netSales, pendingPayments, lastQueueNo }`

---

## 6. ผู้ดูแลระบบ — 👑 ADMIN เท่านั้น

### 6.1 อัปโหลดรูป (ต้องทำก่อนบันทึกสินค้า/โปร)
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| POST | `/files` | อัปโหลดรูป (multipart) | `file` (JPG/PNG/WEBP ≤ 5MB), `folder?` = `PRODUCTS`(default) / `AVATARS` / `BANNERS` | `{ "url": "/files/products/2026/09/<uuid>.jpg" }` |

**กติกา:** `imageUrl` / `bannerUrl` ที่ส่งให้สินค้า/โปร **ต้องเป็น `url` ที่ได้จากเส้นนี้เท่านั้น** (regex `^/files/(products|avatars|banners)/yyyy/mm/<uuid>.(jpg|png|webp)$`)
ห้ามใส่ URL ภายนอกหรือ `javascript:` → ไม่งั้นได้ `400 VALIDATION_ERROR`. แสดงรูปด้วย `{API_HOST}` + url
รูปถูก cache 30 วัน (immutable) จึงไม่ต้อง cache-bust เอง

> **ลบ ≠ ปิดใช้งาน** (ใช้กับหมวด, สินค้า, กลุ่มตัวเลือก, โปรโมชั่น)
> - ปิดใช้งาน = ส่ง `active:false` ใน PUT → ลูกค้าไม่เห็น แต่หลังบ้านยังเห็นและเปิดกลับได้ → UI ใช้สวิตช์
> - ลบ = `DELETE` → หายจากทุกหน้าจอ (รวมหลังบ้าน) เปิดกลับไม่ได้ รหัส/slug นำกลับมาใช้ใหม่ได้ → UI ใช้ปุ่ม 🗑 + ยืนยัน แสดงได้ทุกแถวไม่ว่าเปิดหรือปิดอยู่
> - ยอดขาย/ออเดอร์ย้อนหลังของรายการที่ลบยังอยู่ครบ

### 6.2 หมวดหมู่ `/admin/categories`
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| GET | `/admin/categories` | หมวดทั้งหมด (รวมที่ปิด) | – | `CategoryResponse[]` |
| POST | `/admin/categories` | สร้าง (201) | `CategoryUpsertRequest` | `CategoryResponse` |
| PUT | `/admin/categories/{id}` | แก้ | `CategoryUpsertRequest` | `CategoryResponse` |
| DELETE | `/admin/categories/{id}` | **ลบ** หมวด (ต้องไม่มีสินค้าเหลือ ไม่งั้น 422 `INVALID_OPERATION`) | – | `null` |
| PATCH | `/admin/categories/sort` | จัดลำดับ (≤500) | `[{ "id":1, "sortOrder":0 }]` | `CategoryResponse[]` |

`CategoryUpsertRequest` = `{ name (≤100), slug (a-z0-9 และ - เท่านั้น เช่น "roti-wan"), description? (≤500), icon? (a-zA-Z0-9 _-), sortOrder (0..100000), active }`
→ ทำ drag&drop จัดลำดับแล้วส่งทั้งลิสต์ไป `/sort`

### 6.3 สินค้า `/admin/products`  (อ่าน/ของหมด = STAFF ได้ ตามข้อ 5.4)
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| POST | `/admin/products` | สร้างสินค้า (201) | `ProductUpsertRequest` | `AdminProductResponse` |
| PUT | `/admin/products/{id}` | แก้สินค้า | `ProductUpsertRequest` | `AdminProductResponse` |
| DELETE | `/admin/products/{id}` | **ลบ** สินค้า | – | `null` |
| POST | `/admin/products/{id}/images` | เพิ่มรูปแกลเลอรี (multipart `file`, สูงสุด 10 รูป) | form-data | `AdminProductResponse` |
| DELETE | `/admin/products/{id}/images/{imageId}` | ลบรูป | – | `AdminProductResponse` |
| PUT | `/admin/products/{id}/option-groups` | ผูกกลุ่มตัวเลือก (**แทนที่ทั้งหมด**, ≤20) | `[{ "optionGroupId":3, "sortOrder":0 }]` | `AdminProductResponse` |

```jsonc
// ProductUpsertRequest
{ "categoryId":1, "name":"โรตีไข่ (≤150)", "nameEn?":"Egg roti (≤150)", "description?":"", "descriptionEn?":"" ,
  "originalPrice?":45.00 /* ราคาก่อนลด แสดงขีดฆ่า ต้อง > price */, "badge?":"new" /* bestseller | new | recommended | null */,
  "price":35.00 /* 0..99999.99 ทศนิยม ≤2 */, "imageUrl?":"/files/products/...", "available":true, "recommended":false,
  "active":true, "sortOrder":0 }
// AdminProductResponse = ProductDetail + { active, sortOrder, updatedAt }
// ProductSummary / ProductDetail / AdminProductResponse คืน nameEn, descriptionEn, originalPrice, badge ด้วย (ไม่มีค่า = null)
// OptionGroup / OptionItem: request และ response มี nameEn? (≤100)
```
- `available` = มีของ/ของหมด (พนักงานสลับได้), `active` = เปิดขาย/ปิดถาวร (แอดมินเท่านั้น)
- **`code` ระบบ gen ให้ ห้ามส่ง** = `RT-` + id ประเภท 3 หลัก + id เมนู 4 หลัก (เช่น `RT-0010015`, ยาว 10 ตัว) — gen ตอนสร้างครั้งเดียว **แก้ไม่ได้** (ย้ายหมวดแล้วรหัสคงเดิม) → หน้าสร้างไม่มีช่องรหัส, หน้าแก้แสดงเป็นข้อความอ่านอย่างเดียว

### 6.4 กลุ่มตัวเลือก `/admin/option-groups`
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| GET | `/admin/option-groups` | ทั้งหมด | – | `OptionGroupResponse[]` |
| GET | `/admin/option-groups/{id}` | รายละเอียด | – | `OptionGroupResponse` |
| POST | `/admin/option-groups` | สร้าง + items (201) | `OptionGroupUpsertRequest` | `OptionGroupResponse` |
| PUT | `/admin/option-groups/{id}` | แก้ | `OptionGroupUpsertRequest` | `OptionGroupResponse` |
| DELETE | `/admin/option-groups/{id}` | **ลบ** กลุ่ม (ถอดออกจากทุกสินค้า) | – | `null` |

```jsonc
// OptionGroupUpsertRequest — minSelect 0..50, maxSelect 1..50 (ต้อง max ≥ min), items ≤ 50
{ "name":"ท็อปปิ้ง", "nameEn?":"", "minSelect":0, "maxSelect":2, "defaultSelected?":false /* ติ๊กไว้ให้ก่อน */, "active":true, "sortOrder":0,
  "items":[ { "id":31 /* มี id = แก้, ไม่มี id = เพิ่ม */, "name":"นมข้น", "extraPrice":5.00, "available":true, "sortOrder":0 } ] }
```
⚠️ **PUT: item ที่มี `id` = แก้, ไม่มี `id` = เพิ่ม, item ที่ไม่ส่งมา = ถูกลบ** — ต้องส่ง items ทั้งหมดกลับเสมอ

### 6.5 โปรโมชั่น `/admin/promotions`
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| GET | `/admin/promotions` | รายการ (paging) | `active?, page, size` | `PageResponse<AdminPromotion>` |
| GET | `/admin/promotions/{id}` | รายละเอียด | – | `AdminPromotion` |
| POST | `/admin/promotions` | สร้าง (201) | `PromotionUpsertRequest` | `AdminPromotion` |
| PUT | `/admin/promotions/{id}` | แก้ | `PromotionUpsertRequest` | `AdminPromotion` |
| DELETE | `/admin/promotions/{id}` | **ลบ** โปร | – | `null` |
| GET | `/admin/promotions/{id}/usages` | ประวัติการใช้โปร | `page,size` | `PageResponse<UsageResponse>` |

```jsonc
// PromotionUpsertRequest
{ "code?":"WELCOME10" /* A-Za-z0-9_- ยาว 3–30; ไม่ใส่ = โปรอัตโนมัติ */, "name":"...", "description?", "bannerUrl?":"/files/banners/...",
  "type":"PERCENT|FIXED_AMOUNT|BUY_X_GET_Y|POINT_MULTIPLIER",
  "discountValue":10.00, "maxDiscount?":50.00, "buyQty?":2, "getQty?":1, "minOrderAmount":0.00,
  "scope":"ORDER|PRODUCT|CATEGORY", "memberOnly":false, "channel":"ALL|WALK_IN|ONLINE",
  "daysOfWeek?":[1,2,3] /* 1..7 */, "startAt":"2026-10-01T00:00:00Z", "endAt":"2026-10-31T16:59:59Z",
  "usageLimit?", "usagePerCustomer?", "showOnLanding":true, "priority":0 /* -1000..1000 */, "active":true,
  "productIds?":[10], "categoryIds?":[1] }
```
- ฟอร์มควรแสดงช่องตามชนิด: `PERCENT` ใช้ discountValue เป็น % (+maxDiscount), `FIXED_AMOUNT` เป็นบาท,
  `BUY_X_GET_Y` ใช้ buyQty/getQty, `POINT_MULTIPLIER` คือตัวคูณแต้ม (ไม่ใช่ส่วนลด)
- `scope=PRODUCT` ต้องเลือก `productIds`, `scope=CATEGORY` ต้องเลือก `categoryIds`
- `AdminPromotion` = ฟิลด์ทั้งหมดข้างบน + `id, usedCount, updatedAt`

### 6.5.1 Banner หน้าร้าน `/admin/banners` (แอดมินสร้างเองทุกใบ ไม่สร้างอัตโนมัติจากโปร)
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| GET | `/public/banners?placement=HOME_HERO\|HOME_OFFER` | 🌐 banner ที่ active + อยู่ในช่วง startAt–endAt เรียงตาม sortOrder | – | `PublicBanner[]` |
| GET | `/admin/banners` | รายการทั้งหมด (รวมที่ปิด/หมดเวลา) | `placement?, active?` | `AdminBanner[]` |
| GET | `/admin/banners/{id}` | รายละเอียด | – | `AdminBanner` |
| POST | `/admin/banners/images` | อัปรูป (201) **multipart** field `file` — JPG/PNG/WEBP **≤ 2MB** แนะนำ 1920×800 | – | `{ url }` |
| POST | `/admin/banners` | สร้าง (201) | `BannerUpsertRequest` | `AdminBanner` |
| PUT | `/admin/banners/{id}` | แก้ (เปลี่ยนรูป → รูปเก่าถูกลบ) | `BannerUpsertRequest` | `AdminBanner` |
| PUT | `/admin/banners/order` | จัดลำดับ (ลากวาง) — ส่ง id **ครบทุกใบ** ของ placement บนสุดก่อน | `{ placement, ids:[...] }` | `AdminBanner[]` |
| DELETE | `/admin/banners/{id}` | ลบ (ลบรูปด้วย) | – | – |

`BannerUpsertRequest` = `{ placement, imageUrl (จำเป็น — url จาก /admin/banners/images), imageUrlMobile?, title? (≤150), titleEn?, subtitle? (≤300), subtitleEn?, ctaLabel? (≤50), ctaLabelEn?, linkUrl? (ขึ้นต้น / หรือ https://), promotionId?, sortOrder? (ว่าง = ต่อท้าย), active, startAt?, endAt? }`
`PublicBanner` = `{ id, placement, imageUrl, imageUrlMobile, title, titleEn, subtitle, subtitleEn, ctaLabel, ctaLabelEn, linkUrl, promotionId }`
`AdminBanner` = `PublicBanner` + `{ sortOrder, active, startAt, endAt, createdAt, updatedAt }`
- คลิก banner: มี `promotionId` → ไป `/promotions/{promotionId}` · ไม่มีก็ใช้ `linkUrl` · ไม่มีทั้งคู่ = ไม่ต้องคลิกได้
- จอมือถือ: ใช้ `imageUrlMobile` ถ้ามี ไม่งั้นใช้ `imageUrl`
- รูปหัวการ์ดโปร/หน้ารายละเอียดโปร ใช้ `bannerUrl` ของโปรโมชั่น (ข้อ 6.5) ไม่เกี่ยวกับตารางนี้

### 6.6 ช่องทางชำระเงิน
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| POST | `/admin/payment-methods` | เพิ่ม (201) | `PaymentMethodUpsertRequest` | `PaymentMethodResponse` |
| PUT | `/admin/payment-methods/{id}` | แก้ (**แก้ `code` ไม่ได้**) | `PaymentMethodUpsertRequest` | `PaymentMethodResponse` |

`PaymentMethodUpsertRequest` = `{ code (A-Z ตามด้วย A-Z0-9_, ≤30), name (≤100), requiresReference, allowOnline (ลูกค้าเลือกเองออนไลน์ได้ไหม), instruction? (≤1000), icon?, active, sortOrder }`

### 6.7 พนักงาน `/admin/staff`
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| GET | `/admin/staff` | พนักงานทั้งหมด | – | `StaffResponse[]` |
| GET | `/admin/staff/{id}` | ข้อมูลพนักงาน | – | `StaffResponse` |
| POST | `/admin/staff` | เพิ่มพนักงาน (201) — ต้องเปลี่ยนรหัสเมื่อ login ครั้งแรก | `{ phone, email?, password, nickname, role }` | `StaffResponse` |
| PUT | `/admin/staff/{id}` | แก้/ระงับ/เปลี่ยน role/รีเซ็ตรหัส — **revoke session ทันที** | `{ email?, nickname, role, status, newPassword? }` | `StaffResponse` |

`StaffResponse` = `{ id, phone, email, nickname, role, status, lastLoginAt, createdAt }`
(สร้าง/แก้ role ใช้ `STAFF` หรือ `ADMIN`; แสดง confirm dialog ก่อนระงับ/ลด role)

### 6.8 ปรับแต้มลูกค้า
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| POST | `/admin/customers/{id}/points/adjust` | ปรับแต้ม (+/−) | `{ "points": -50 /* -1,000,000..1,000,000 */, "remark":"เหตุผล (จำเป็น ≤300)" }` | `PointTransactionResponse` |

### 6.9 ตั้งค่าระบบ `/admin/settings`
| Method | Path | ทำอะไร | Request | Response |
|---|---|---|---|---|
| GET | `/admin/settings` | ตั้งค่าทั้งหมด | – | `[{ key, value, valueType, description }]` |
| PUT | `/admin/settings` | แก้ (ส่งเฉพาะ key ที่เปลี่ยน, ≤50) | `{ "values": { "shop.name": "..." } }` | รายการที่อัปเดต |

Keys ที่มี: `shop.name`, `shop.phone`, `shop.open_time` (HH:mm), `shop.close_time`, `shop.accept_online_order` (true/false),
`point.earn_baht_per_point`, `point.redeem_points_per_baht`, `point.redeem_min_points`, `point.redeem_max_percent`, `point.expire_days`,
`payment.promptpay_id`, `payment.bank_account` — ใช้ `valueType` เลือก input (text/number/boolean/time)

### 6.10 Dashboard `/admin/dashboard/*` (นับเฉพาะออเดอร์ COMPLETED)
| Method | Path | Query | Response |
|---|---|---|---|
| GET | `/summary` | `from,to` (YYYY-MM-DD, จำเป็น) | `{ from,to,orderCount,grossSales,totalDiscount,netSales,averagePerOrder,memberOrderCount,walkInCount,onlineCount,cancelledCount,newMembers,pointsIssued,pointsRedeemed,walkInAmount,onlineAmount,memberAmount,guestAmount }` (Amount = ยอดเงิน net) |
| GET | `/sales-trend` | `from,to,groupBy=DAY\|WEEK\|MONTH` | `[{ period, orderCount, netSales }]` |
| GET | `/top-products` | `from,to,limit=10` | `[{ productId, productName, quantity, amount }]` |
| GET | `/payment-methods` | `from,to` | `[{ methodCode, methodName, paymentCount, amount }]` |
| GET | `/hourly` | `date` | `[{ hour, orderCount, netSales }]` |

---

## 7. Error codes → พฤติกรรมที่ FE ต้องทำ

| HTTP | `error.code` | ทำอะไร |
|---|---|---|
| 400 | `VALIDATION_ERROR`, `BAD_REQUEST` | แสดง `fields[]` ใต้ช่องกรอกที่ตรงกับ `field` |
| 401 | `UNAUTHORIZED` / `TOKEN_EXPIRED` | ทำ refresh 1 ครั้ง แล้ว retry; ไม่ผ่าน → หน้า login |
| 401 | `INVALID_CREDENTIALS` | "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง" |
| 403 | `FORBIDDEN` | หน้า "ไม่มีสิทธิ์" |
| 403 | `ACCOUNT_SUSPENDED` | แจ้งบัญชีถูกระงับ + logout |
| 403 | `PASSWORD_CHANGE_REQUIRED` | ไปหน้าเปลี่ยนรหัสผ่านบังคับ |
| 404 | `NOT_FOUND` | หน้า "ไม่พบข้อมูล" |
| 409 | `PHONE_ALREADY_USED`, `EMAIL_ALREADY_USED`, `DUPLICATE_VALUE` | แจ้งที่ช่องที่ซ้ำ |
| 409 | `CONCURRENT_UPDATE` | แจ้งให้โหลดข้อมูลใหม่แล้วแก้ซ้ำ |
| 413/415/422 | `PAYLOAD_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, `FILE_INVALID` | ตรวจไฟล์ที่ FE ก่อนส่ง (ชนิด JPG/PNG/WEBP, ≤5MB) |
| 423 | `ACCOUNT_LOCKED` | "ล็อกชั่วคราว ลองใหม่ภายหลัง (15 นาที)" |
| 429 | `RATE_LIMITED` | ปิดปุ่มชั่วคราวตาม header `Retry-After` |
| 422 | `ONLINE_ORDER_CLOSED` | แจ้งร้านปิดรับออนไลน์ |
| 422 | `PRODUCT_UNAVAILABLE`, `OPTION_INVALID` | รีโหลดเมนู/ตะกร้า แจ้งรายการที่ใช้ไม่ได้ |
| 422 | `ORDER_INVALID_STATUS` | รีโหลดออเดอร์ (สถานะเปลี่ยนไปแล้ว) |
| 422 | `PAYMENT_AMOUNT_MISMATCH`, `PAYMENT_METHOD_UNAVAILABLE`, `REFERENCE_REQUIRED`, `TOO_MANY_PENDING_PAYMENTS` | แจ้งที่ฟอร์มชำระเงิน |
| 422 | `POINT_INSUFFICIENT`, `POINT_BELOW_MIN`, `POINT_EXCEED_LIMIT` | แจ้งที่ช่องแลกแต้ม |
| 422 | `PROMOTION_NOT_FOUND`, `PROMOTION_EXPIRED`, `PROMOTION_NOT_ELIGIBLE`, `PROMOTION_LIMIT_REACHED` | แจ้งที่ช่องโค้ดโปร (เอาโค้ดออกจากตะกร้าได้) |
| 422 | `INVALID_OPERATION` | แสดง `error.message` จาก backend |
| 500 | `INTERNAL_ERROR` | "เกิดข้อผิดพลาดในระบบ" + ให้ลองใหม่ (ห้ามโชว์รายละเอียดเทคนิค) |

ใช้ `error.message` (ภาษาไทยจาก backend) เป็นข้อความหลัก และ map `code` เพิ่มเมื่อต้องเปลี่ยนพฤติกรรม

---

## 8. สิ่งที่หน้าบ้านต้องจัดการ (Checklist)

**โครงสร้าง**
- [ ] `ApiService` กลาง + interceptor: แนบ Bearer, unwrap `data`, แปลง error เป็น model เดียว
- [ ] Refresh flow แบบ single-flight (queue request ระหว่าง refresh), `withCredentials` ที่ `/auth/*`
- [ ] Auth store (memory เท่านั้น) + bootstrap ด้วย `/auth/refresh` ตอนเปิดแอป
- [ ] Route guard ตาม role + guard `passwordChangeRequired`
- [ ] Global error handler (401/403/429/500) + toast ข้อความไทย
- [ ] Idle handling: ลูกค้าไม่ใช้งาน 15 นาที (staff/admin 12 ชม.) ต้อง login ใหม่ → เก็บ path เดิมไว้ redirect กลับ

**ความปลอดภัย (ห้ามทำ)**
- [ ] ไม่เก็บ access token ใน localStorage/sessionStorage/cookie ที่ JS อ่านได้
- [ ] ไม่ log token, รหัสผ่าน, `temporaryPassword`, เลขบัญชี ลง console หรือ analytics
- [ ] ไม่ render HTML จากข้อมูล API (ชื่อสินค้า/โน้ต/หมายเหตุ) ด้วย `innerHTML` — ใช้ text binding เท่านั้น
- [ ] ไม่ส่ง `imageUrl` ที่ไม่ได้มาจาก `/files`
- [ ] ไม่คำนวณราคา/ส่วนลด/แต้มเอง — ใช้ผล `quote` เสมอ (backend เป็นผู้ตัดสินตอนสร้างออเดอร์)
- [ ] ไม่ส่ง `userId`/`role` ใน request — backend อ่านจาก token

**UX / พฤติกรรม**
- [ ] Debounce การ quote (300–500ms) และ disable ปุ่ม submit ระหว่างยิง (กันสร้างออเดอร์/รับเงินซ้ำ)
- [ ] ค่าเงินแสดง 2 ตำแหน่ง รูปแบบ ฿ / th-TH; เวลาแสดงเป็นเขตเวลา Asia/Bangkok
- [ ] อัปโหลดรูป: ตรวจชนิด/ขนาดก่อน, แสดง progress/preview, ใช้ `url` ที่ได้ไปใส่ฟอร์ม
- [ ] Track page ของ guest: เก็บ `trackingToken` ให้ผู้ใช้กลับมาดูได้ (URL/บุ๊กมาร์ก) — เป็น "กุญแจ" ห้ามแชร์สาธารณะ
- [ ] ตาราง admin ทั้งหมดใช้ paging ตาม `PageResponse` (ไม่ดึงทั้งหมดมา filter ฝั่ง FE)
- [ ] ผูก `fields[]` จาก error เข้ากับ Reactive Forms (`setErrors`)

**หน้าจอที่ต้องมี**
- Public: Landing (สไลด์ HOME_HERO + banner HOME_OFFER จาก `/public/banners`, menu/promotions/shop-info), Product detail, Cart + โค้ดโปร/แต้ม, Checkout, Track order, Login/Register
- สมาชิก: โปรไฟล์ + avatar, เปลี่ยนรหัสผ่าน, ประวัติออเดอร์, แต้ม (ยอด/ใกล้หมดอายุ/ประวัติ)
- STAFF: หน้าแรก(today), POS, คิวครัว(board), ออเดอร์, ตรวจการชำระเงิน, ลูกค้า (ค้นหา/สมัครหน้าร้าน), ของหมด/มีของ
- ADMIN (= เจ้าของร้าน): หมวด, สินค้า+รูป+option, กลุ่มตัวเลือก, โปรโมชั่น, **จัดการ Banner**, ช่องทางชำระ, พนักงาน, ปรับแต้ม, ตั้งค่า, Dashboard

**หน้าจัดการ Banner (ADMIN เท่านั้น — ซ่อนจาก STAFF)** ดู API ข้อ 6.5.1
- แท็บแยก 2 ตำแหน่ง: "สไลด์หน้าแรก" (`HOME_HERO`) และ "ข้อเสนอพิเศษ" (`HOME_OFFER`)
- รายการแสดงรูปย่อ, หัวข้อ, สถานะ (เปิด/ปิด/ยังไม่ถึงเวลา/หมดเวลา — คำนวณจาก `active`, `startAt`, `endAt`), ลากวางเพื่อจัดลำดับ → `PUT /admin/banners/order`
- ฟอร์มสร้าง/แก้: อัปรูป desktop (จำเป็น) + mobile (ไม่บังคับ) ผ่าน `POST /admin/banners/images` ตรวจ ≤ 2MB ฝั่ง FE ก่อนส่ง แสดงคำแนะนำ 1920×800,
  ข้อความ TH/EN (หัวข้อ, คำโปรย, ปุ่ม), ลิงก์ = เลือกโปรโมชั่น (dropdown จาก `/admin/promotions`) **หรือ** กรอก URL, ช่วงวันแสดง, สวิตช์เปิด/ปิด
- พรีวิวก่อนบันทึก (desktop/mobile) และยืนยันก่อนลบ

---

## 9. กติกาธุรกิจเฉพาะเมนู "โรตี 5 ดาว 15 รส" (default data จาก `V10__default_roti_menu.sql`)

ข้อมูลตั้งต้น: หมวด `โรตี` (slug `roti`) มี 6 เมนู + กลุ่มตัวเลือก `ท็อปปิ้ง` (12 รายการ, เลือกได้ 0–10) ผูกกับโรตีทุกตัว
รายละเอียดราคา/ชื่อดูได้จาก `GET /public/menu` — สิ่งที่ FE **ต้องทำตามตรรกะธุรกิจ** ไม่ใช่แค่เรนเดอร์ข้อมูล:

1. **ท็อปปิ้งคือตัวเลือกเสริม ไม่ใช่สินค้าแยก** — แสดงเป็น checkbox/chip ต่อท้ายตอนลูกค้ากำลังปรับแต่งโรตี (เหมือน `optionGroups` ของสินค้าอื่น) ห้ามทำเป็นเมนูให้กดสั่งเดี่ยวๆ ได้เอง
2. **เลือกท็อปปิ้งซ้ำตัวเดิมไม่ได้** — backend reject ด้วย `OPTION_INVALID` ("เลือกตัวเลือกซ้ำ") ถ้า `optionItemIds` มีเลขซ้ำ → ฝั่ง FE ต้องเก็บเป็น `Set` ของ id ที่เลือก (toggle on/off) ไม่ใช่ list ที่กดเพิ่มได้เรื่อยๆ ถ้าลูกค้าอยากได้ท็อปปิ้งเดิม 2 เท่า ระบบปัจจุบันยังไม่รองรับ (ไม่มี "quantity ต่อท็อปปิ้ง") — ต้องแนะนำให้สั่งเป็นอีกบรรทัดสินค้าแยก
3. **กลุ่มท็อปปิ้งใช้ร่วมกันทุกเมนูในหมวดโรตี** (shared option group ไม่ได้ custom ต่อชิ้น) — ถ้าแอดมินเพิ่มเมนูโรตีใหม่แล้วไม่ไปผูก option-group ที่หน้า "ผูกกลุ่มตัวเลือกกับสินค้า" (ข้อ 6.3) เมนูนั้นจะไม่มีท็อปปิ้งให้เลือกเลย — ไม่ใช่บั๊ก backend แต่เป็นขั้นตอนที่แอดมินต้องทำเอง ควรเตือนในหน้า admin ตอนสร้างสินค้าใหม่ (เช่น banner "ยังไม่ได้ผูกตัวเลือก")
4. **⚠️ จุดที่ต้องเช็คกับเจ้าของร้านก่อนขึ้นจริง:** เมนู "โรตี 5 ดาว 15 รส" (45 บาท) ชื่อสื่อว่าเป็นคอมโบคงที่ (รวมไข่+กล้วย+แยม+ช็อกโกแลตในราคาเดียว) แต่ใน data ปัจจุบันมันถูกผูกกับกลุ่มท็อปปิ้งเหมือนเมนูอื่นทุกตัว — แปลว่าลูกค้าสามารถกดเลือกท็อปปิ้งเพิ่มบนเมนูนี้ได้อีก และจะถูกคิดเงินเพิ่มจาก 45 บาทตามจริง (ไม่ได้ฟรี) ถ้าตั้งใจให้เมนูนี้ "ราคาคงที่ ห้ามเพิ่มท็อปปิ้ง" ต้องแจ้ง backend ให้ถอด option-group ออกจากสินค้านี้ (ผ่าน `PUT /admin/products/{id}/option-groups` ส่ง list ว่าง) — **อย่าแก้ปัญหานี้ด้วยการ hardcode ซ่อน topping picker เฉพาะเมนูนี้ ฝั่ง FE** (หรือ code ของเมนูนี้) เพราะจะไม่ตรงกับสิ่งที่ API อนุญาตจริง (ถ้ายิง order ตรงๆ ผ่าน API อื่นก็ยังเพิ่มได้อยู่ดี) ควรแก้ที่ data ไม่ใช่ที่ UI
5. **ราคาท็อปปิ้งไม่เท่ากันทุกตัว** (ส่วนใหญ่ 20 บาท แต่ "โอวัลตินลูกเกด" = 25 บาท) — ห้ามตั้ง fix ราคาท็อปปิ้งเป็นค่าคงที่ฝั่ง FE ต้องอ่าน `extraPrice` จาก `OptionItemResponse` และคำนวณยอดจริงผ่าน `quote` เสมอ (ตามกติกาทั่วไปในข้อ 8)
6. **ท็อปปิ้งที่ของหมด** (`available:false`) ต้อง disable/ขีดฆ่าใน selector ไม่ใช่ซ่อนออกจากรายการ — เพื่อให้ลูกค้าเห็นว่ามีตัวเลือกนี้ปกติแต่หมดวันนี้ (พฤติกรรมเดียวกับสินค้า `available:false`)
7. สินค้าเมนูนี้ **ยังไม่มีรูป** (`imageUrl: null`) จนกว่าแอดมินจะอัปโหลด — ต้องมี placeholder image ไว้รองรับ ไม่ใช่ปล่อย broken image icon
8. `code` (เช่น `RT-0010015`) ระบบ gen แก้ไม่ได้ แต่ ใช้ `id` อ้างอิง logic ถาวร ไม่ใช่ `code` (ยกเว้นกรณีแสดงผล/debug)
