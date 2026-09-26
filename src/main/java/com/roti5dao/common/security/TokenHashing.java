package com.roti5dao.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenHashing {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenHashing() {
    }

    /** token สุ่ม 256 bit (base64url) */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** รหัสผ่านชั่วคราว 10 ตัว (ตัดตัวอักษรที่สับสนง่ายออก) */
    public static String temporaryPassword() {
        String letters = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
        String digits = "23456789";
        String all = letters + digits;
        StringBuilder sb = new StringBuilder();
        sb.append(letters.charAt(RANDOM.nextInt(letters.length())));
        sb.append(digits.charAt(RANDOM.nextInt(digits.length())));
        for (int i = 0; i < 8; i++) {
            sb.append(all.charAt(RANDOM.nextInt(all.length())));
        }
        return sb.toString();
    }
}
