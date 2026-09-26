package com.roti5dao.common.setting;

import java.util.Arrays;
import java.util.Optional;

public enum SettingKey {
    SHOP_NAME("shop.name"),
    SHOP_PHONE("shop.phone"),
    SHOP_OPEN_TIME("shop.open_time"),
    SHOP_CLOSE_TIME("shop.close_time"),
    SHOP_ACCEPT_ONLINE_ORDER("shop.accept_online_order"),
    POINT_EARN_BAHT_PER_POINT("point.earn_baht_per_point"),
    POINT_REDEEM_POINTS_PER_BAHT("point.redeem_points_per_baht"),
    POINT_REDEEM_MIN_POINTS("point.redeem_min_points"),
    POINT_REDEEM_MAX_PERCENT("point.redeem_max_percent"),
    POINT_EXPIRE_DAYS("point.expire_days"),
    PAYMENT_PROMPTPAY_ID("payment.promptpay_id"),
    PAYMENT_BANK_ACCOUNT("payment.bank_account");

    private final String key;

    SettingKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<SettingKey> fromKey(String key) {
        return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
    }
}
