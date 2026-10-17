package com.roti5dao.common.storage;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/** รับเฉพาะ URL ที่ระบบออกให้ผ่านการอัปโหลด — กัน javascript:/รูปจากโดเมนภายนอก */
@Component
public class ImageUrlValidator {

    private final FileStorageService storage;

    public ImageUrlValidator(FileStorageService storage) {
        this.storage = storage;
    }

    public String requireStoredOrNull(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String v = url.trim();
        if (!storage.isStoredPublicUrl(v)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "รูปต้องอัปโหลดผ่านระบบก่อน (/api/v1/files)");
        }
        return v;
    }
}
