package com.roti5dao.common.storage;

import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Tag(name = "5. Admin — อัปโหลดรูป", description = "อัปโหลด: ADMIN · เสิร์ฟ /files/** เฉพาะโฟลเดอร์ public")
public class FileController {

    private static final String FILES_PREFIX = "/files/";
    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable();

    public record UploadResponse(String url) {
    }

    private final FileStorageService storage;

    public FileController(FileStorageService storage) {
        this.storage = storage;
    }

    @PostMapping("/api/v1/files")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 อัปโหลดรูป (JPG/PNG/WEBP) → ได้ url ไปใส่ imageUrl/bannerUrl")
    public ApiResponse<UploadResponse> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(defaultValue = "PRODUCTS") StorageFolder folder) {
        if (!folder.isPublic()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ไม่สามารถอัปโหลดไปโฟลเดอร์นี้ได้");
        }
        return ApiResponse.ok(new UploadResponse(storage.store(file, folder).location()));
    }

    @GetMapping("/files/**")
    @Operation(summary = "ดูรูปสาธารณะ (products/avatars/banners)")
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        String uri = request.getRequestURI();
        int i = uri.indexOf(FILES_PREFIX);
        if (i < 0) {
            throw new NotFoundException("ไฟล์");
        }
        // ชื่อไฟล์เป็น UUID ไม่เคยถูกเขียนทับ → cache ได้ยาวแบบ immutable
        return FileResponses.inline(storage.loadPublic(uri.substring(i + FILES_PREFIX.length())), PUBLIC_CACHE);
    }
}
