package com.roti5dao.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {

    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private MoneyUtils() {
    }

    public static BigDecimal scale(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** ส่วนลดปัดลงเสมอ (ร้านไม่เสียเปรียบจากเศษสตางค์) */
    public static BigDecimal floor(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.DOWN);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static boolean isPositive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}
