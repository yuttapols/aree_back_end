package com.roti5dao.common.util;

import java.util.Locale;

public final class LikeUtils {

    private LikeUtils() {
    }

    /**
     * สร้าง pattern สำหรับ LIKE แบบ contains โดย escape อักขระพิเศษ (% _ \) ของผู้ใช้
     * ใช้คู่กับ {@code ESCAPE '\'} ใน query — คืน null ถ้า keyword ว่าง
     */
    public static String containsPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String k = keyword.trim();
        if (k.length() > 100) {
            k = k.substring(0, 100);
        }
        String escaped = k.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
