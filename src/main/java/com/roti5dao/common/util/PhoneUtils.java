package com.roti5dao.common.util;

import java.util.regex.Pattern;

public final class PhoneUtils {

    /** เบอร์ไทย: ขึ้นต้น 0 ตามด้วยตัวเลข 8–9 หลัก (บ้าน 9 หลัก / มือถือ 10 หลัก) */
    private static final Pattern THAI_PHONE = Pattern.compile("^0\\d{8,9}$");

    private PhoneUtils() {
    }

    /** ตัดขีด/ช่องว่าง และแปลง +66 เป็น 0 — คืน null ถ้ารูปแบบไม่ถูกต้อง */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.trim().replaceAll("[\\s\\-()]", "");
        if (digits.startsWith("+66")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("66") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        return THAI_PHONE.matcher(digits).matches() ? digits : null;
    }

    public static boolean isValid(String raw) {
        return normalize(raw) != null;
    }

    /** แสดงเบอร์แบบปิดบัง เช่น 081-xxx-5678 (ใช้ในหน้า public) */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return phone;
        }
        return phone.substring(0, 3) + "-xxx-" + phone.substring(phone.length() - 4);
    }
}
