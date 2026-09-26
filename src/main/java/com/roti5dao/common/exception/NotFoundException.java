package com.roti5dao.common.exception;

public class NotFoundException extends BusinessException {

    public NotFoundException(String what) {
        super(ErrorCode.NOT_FOUND, "ไม่พบ" + what);
    }
}
