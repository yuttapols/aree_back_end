package com.roti5dao.common.exception;

import org.springframework.http.HttpStatus;

/** รหัส error รวมทั้งระบบ — FE ใช้ map เป็นข้อความภาษาไทย */
public enum ErrorCode {
    // Phase 1 — common / auth
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "ข้อมูลไม่ถูกต้อง"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "คำขอไม่ถูกต้อง"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "กรุณาเข้าสู่ระบบ"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "เซสชันหมดอายุ"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "บัญชีถูกล็อกชั่วคราว กรุณาลองใหม่ภายหลัง"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "บัญชีถูกระงับการใช้งาน"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "ไม่มีสิทธิ์เข้าถึง"),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "กรุณาเปลี่ยนรหัสผ่านก่อนใช้งาน"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "ไม่พบข้อมูล"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "ไม่รองรับคำขอนี้"),
    PHONE_ALREADY_USED(HttpStatus.CONFLICT, "เบอร์โทรนี้ถูกใช้แล้ว"),
    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "อีเมลนี้ถูกใช้แล้ว"),
    DUPLICATE_VALUE(HttpStatus.CONFLICT, "ข้อมูลซ้ำ"),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "ข้อมูลถูกแก้ไขพร้อมกัน กรุณาลองใหม่"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "ไฟล์หรือข้อมูลมีขนาดใหญ่เกินไป"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "ไม่รองรับชนิดข้อมูลนี้"),
    FILE_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "ไฟล์ไม่ถูกต้อง (รองรับ JPG, PNG, WEBP)"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "มีการเรียกใช้งานถี่เกินไป กรุณาลองใหม่ภายหลัง"),
    INVALID_OPERATION(HttpStatus.UNPROCESSABLE_CONTENT, "ไม่สามารถทำรายการนี้ได้"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "เกิดข้อผิดพลาดในระบบ"),

    // Phase 3 — order / payment
    ONLINE_ORDER_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT, "ร้านปิดรับออเดอร์ออนไลน์ชั่วคราว"),
    PRODUCT_UNAVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "สินค้าบางรายการหมดหรือไม่พร้อมขาย"),
    OPTION_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "ตัวเลือกสินค้าไม่ถูกต้อง"),
    ORDER_INVALID_STATUS(HttpStatus.UNPROCESSABLE_CONTENT, "สถานะออเดอร์ไม่อนุญาตให้ทำรายการนี้"),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "ยอดชำระไม่ถูกต้อง"),
    PAYMENT_METHOD_UNAVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "ช่องทางชำระเงินนี้ไม่พร้อมใช้งาน"),
    SLIP_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "กรุณาแนบสลิป"),
    REFERENCE_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "กรุณาระบุเลขอ้างอิง"),
    TOO_MANY_PENDING_PAYMENTS(HttpStatus.UNPROCESSABLE_CONTENT, "มีสลิปรอตรวจสอบอยู่แล้ว กรุณารอพนักงานตรวจสอบ"),

    // Phase 4 — point / promotion
    POINT_INSUFFICIENT(HttpStatus.UNPROCESSABLE_CONTENT, "แต้มไม่พอ"),
    POINT_BELOW_MIN(HttpStatus.UNPROCESSABLE_CONTENT, "แต้มที่แลกต่ำกว่าขั้นต่ำ"),
    POINT_EXCEED_LIMIT(HttpStatus.UNPROCESSABLE_CONTENT, "ส่วนลดจากแต้มเกินกว่าที่กำหนด"),
    PROMOTION_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "ไม่พบโค้ดโปรโมชั่น"),
    PROMOTION_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "โปรโมชั่นหมดอายุหรือยังไม่เริ่ม"),
    PROMOTION_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_CONTENT, "ออเดอร์ไม่ตรงเงื่อนไขโปรโมชั่น"),
    PROMOTION_LIMIT_REACHED(HttpStatus.UNPROCESSABLE_CONTENT, "โปรโมชั่นถูกใช้ครบจำนวนแล้ว");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
