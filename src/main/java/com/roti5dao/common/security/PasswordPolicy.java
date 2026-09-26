package com.roti5dao.common.security;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.web.ApiResponse.FieldErrorItem;
import java.nio.charset.StandardCharsets;

/**
 * นโยบายรหัสผ่าน: 8 ตัวขึ้นไป มีทั้งตัวอักษรและตัวเลข
 * และไม่เกิน 72 byte (ข้อจำกัดของ BCrypt — เกินจากนี้จะถูกตัดทิ้งเงียบๆ)
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password, String field) {
        String msg = check(password);
        if (msg != null) {
            throw new PolicyViolation(field, msg);
        }
    }

    public static String check(String password) {
        if (password == null || password.length() < 8) {
            return "รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "รหัสผ่านยาวเกินไป";
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            return "รหัสผ่านต้องมีทั้งตัวอักษรและตัวเลข";
        }
        return null;
    }

    public static class PolicyViolation extends BusinessException {
        private final FieldErrorItem field;

        PolicyViolation(String field, String message) {
            super(ErrorCode.VALIDATION_ERROR, message);
            this.field = new FieldErrorItem(field, message);
        }

        public FieldErrorItem field() {
            return field;
        }
    }
}
