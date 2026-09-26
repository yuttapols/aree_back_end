package com.roti5dao.promotion.entity;

public final class PromotionEnums {

    private PromotionEnums() {
    }

    public enum PromotionType {
        PERCENT, FIXED_AMOUNT, BUY_X_GET_Y, POINT_MULTIPLIER;

        public boolean isDiscount() {
            return this != POINT_MULTIPLIER;
        }
    }

    public enum PromotionScope {
        ORDER, PRODUCT, CATEGORY
    }

    public enum PromotionChannel {
        ALL, WALK_IN, ONLINE
    }
}
