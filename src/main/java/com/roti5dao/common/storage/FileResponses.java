package com.roti5dao.common.storage;

import com.roti5dao.common.storage.FileStorageService.LoadedFile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * สร้าง response สำหรับส่งไฟล์รูปให้ browser แสดงผล (inline) — ใช้ร่วมกันระหว่างรูปสาธารณะและสลิป
 * (header X-Content-Type-Options: nosniff ถูกใส่ให้ทุก response โดย SecurityConfig อยู่แล้ว)
 */
public final class FileResponses {

    private FileResponses() {
    }

    public static ResponseEntity<Resource> inline(LoadedFile file, CacheControl cacheControl) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.type().contentType()))
                .cacheControl(cacheControl)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(file.resource());
    }
}
