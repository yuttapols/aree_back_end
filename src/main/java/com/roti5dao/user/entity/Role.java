package com.roti5dao.user.entity;

import java.util.Arrays;

public enum Role {
    CUSTOMER, STAFF, ADMIN;

    public static boolean isValid(String value) {
        return Arrays.stream(values()).anyMatch(r -> r.name().equals(value));
    }
}
