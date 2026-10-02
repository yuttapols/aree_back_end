package com.roti5dao.common.storage;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import java.util.regex.Pattern;

/** รับเฉพาะ URL ที่ระบบออกให้ผ่านการอัปโหลด — กัน javascript:/รูปจากโดเมนภายนอก */
public final class ImageUrlValidator {

    private static final Pattern STORED = Pattern.compile(
            "^/files/(products|avatars|banners)/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.(jpg|png|webp)$");

    private ImageUrlValidator() {
    }

    public static String requireStoredOrNull(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String v = url.trim();
        if (!STORED.matcher(v).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "รูปต้องอัปโหลดผ่านระบบก่อน (/api/v1/files)");
        }
        return v;
    }
}
