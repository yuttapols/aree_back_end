package com.roti5dao.banner.api;

import com.roti5dao.banner.dto.BannerDtos.AdminBanner;
import com.roti5dao.banner.dto.BannerDtos.BannerReorderRequest;
import com.roti5dao.banner.dto.BannerDtos.BannerUpsertRequest;
import com.roti5dao.banner.dto.BannerDtos.PublicBanner;
import com.roti5dao.banner.dto.BannerDtos.UploadResponse;
import com.roti5dao.banner.entity.BannerPlacement;
import com.roti5dao.banner.service.BannerService;
import com.roti5dao.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "7. Banners", description = "banner หน้าร้าน (HOME_HERO / HOME_OFFER) — public และหลังบ้าน (ADMIN)")
public class BannerController {

    private final BannerService service;

    public BannerController(BannerService service) {
        this.service = service;
    }

    @GetMapping("/public/banners")
    @Operation(summary = "🌐 banner ที่ active และอยู่ในช่วงเวลา เรียงตาม sortOrder")
    public ApiResponse<List<PublicBanner>> visible(@RequestParam BannerPlacement placement) {
        return ApiResponse.ok(service.visible(placement));
    }

    // ---------- admin (ADMIN)

    @GetMapping("/admin/banners")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 รายการ banner ทั้งหมด (รวมที่ปิด/หมดเวลา)")
    public ApiResponse<List<AdminBanner>> search(@RequestParam(required = false) BannerPlacement placement,
                                                 @RequestParam(required = false) Boolean active) {
        return ApiResponse.ok(service.search(placement, active));
    }

    @GetMapping("/admin/banners/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 รายละเอียด banner")
    public ApiResponse<AdminBanner> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping(path = "/admin/banners/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 อัปโหลดรูป banner (JPG/PNG/WEBP ≤ 2MB, แนะนำ 1920×800) → url ไปใส่ imageUrl/imageUrlMobile")
    public ApiResponse<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(new UploadResponse(service.uploadImage(file)));
    }

    @PostMapping("/admin/banners")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 สร้าง banner (sortOrder ว่าง = ต่อท้าย)")
    public ApiResponse<AdminBanner> create(@Valid @RequestBody BannerUpsertRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/admin/banners/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 แก้ banner (เปลี่ยนรูป → รูปเก่าถูกลบ)")
    public ApiResponse<AdminBanner> update(@PathVariable Long id, @Valid @RequestBody BannerUpsertRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @PutMapping("/admin/banners/order")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 จัดลำดับ banner ใน placement (ส่ง ids ครบทุกใบ บนสุดก่อน)")
    public ApiResponse<List<AdminBanner>> reorder(@Valid @RequestBody BannerReorderRequest req) {
        return ApiResponse.ok(service.reorder(req));
    }

    @DeleteMapping("/admin/banners/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 ลบ banner (ลบรูปด้วย)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
