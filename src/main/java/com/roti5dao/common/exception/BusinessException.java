package com.roti5dao.common.exception;

public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    /** ฟิลด์ที่ผิด (ถ้ารู้) — ส่งกลับใน error.fields ให้ FE ผูกกับฟอร์มได้ */
    private final String field;

    public BusinessException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ErrorCode code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
