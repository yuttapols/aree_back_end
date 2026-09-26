package com.roti5dao.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.catalog.dto.CatalogDtos.AdminProductResponse;
import com.roti5dao.catalog.dto.CatalogDtos.AvailabilityRequest;
import com.roti5dao.catalog.dto.CatalogDtos.ProductOptionGroupLink;
import com.roti5dao.catalog.dto.CatalogDtos.ProductUpsertRequest;
import com.roti5dao.catalog.service.ProductAdminService;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.common.web.PageQuery;
import com.roti5dao.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** อ่าน + เปลี่ยนสถานะของหมด: STAFF/ADMIN · สร้าง/แก้/ลบ: ADMIN เท่านั้น */
@RestController
@RequestMapping("/api/v1/admin/products")
@Tag(name = "5. Admin — สินค้า", description = "อ่าน/ของหมด: STAFF · แก้ไข: ADMIN")
public class AdminProductController {

    private final ProductAdminService service;

    public AdminProductController(ProductAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "🧑‍🍳 รายการสินค้า (paging)")
    public ApiResponse<PageResponse<AdminProductResponse>> search(@RequestParam(required = false) Long categoryId,
                                                                  @RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) Boolean active,
                                                                  @RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer size) {
        var pageable = PageQuery.of(page, size, Sort.by("category.sortOrder", "sortOrder", "id"));
        return ApiResponse.ok(service.search(categoryId, keyword, active, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "🧑‍🍳 รายละเอียดสินค้า")
    public ApiResponse<AdminProductResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 สร้างสินค้า (imageUrl ต้องมาจาก /api/v1/files)")
    public ApiResponse<AdminProductResponse> create(@Valid @RequestBody ProductUpsertRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 แก้สินค้า")
    public ApiResponse<AdminProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductUpsertRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 ปิดสินค้า (soft delete)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/availability")
    @Operation(summary = "🧑‍🍳 ของหมด / มีของ")
    public ApiResponse<AdminProductResponse> availability(@PathVariable Long id, @RequestBody AvailabilityRequest req) {
        return ApiResponse.ok(service.setAvailability(id, req.available()));
    }

    @PostMapping(path = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 อัปโหลดรูปสินค้า (สูงสุด 10 รูป)")
    public ApiResponse<AdminProductResponse> addImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(service.addImage(id, file));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 ลบรูปสินค้า")
    public ApiResponse<AdminProductResponse> deleteImage(@PathVariable Long id, @PathVariable Long imageId) {
        return ApiResponse.ok(service.deleteImage(id, imageId));
    }

    @PutMapping("/{id}/option-groups")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 ผูกกลุ่มตัวเลือกกับสินค้า (แทนที่ทั้งหมด)")
    public ApiResponse<AdminProductResponse> optionGroups(@PathVariable Long id,
                                                          @RequestBody @Size(max = 20) List<@Valid ProductOptionGroupLink> links) {
        return ApiResponse.ok(service.setOptionGroups(id, links));
    }
}
