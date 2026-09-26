package com.roti5dao.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.catalog.dto.CatalogDtos.CategoryResponse;
import com.roti5dao.catalog.dto.CatalogDtos.CategoryUpsertRequest;
import com.roti5dao.catalog.dto.CatalogDtos.SortItem;
import com.roti5dao.catalog.service.CategoryAdminService;
import com.roti5dao.common.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/categories")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "5. Admin — หมวดหมู่", description = "ADMIN")
public class AdminCategoryController {

    private final CategoryAdminService service;

    public AdminCategoryController(CategoryAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "👑 หมวดทั้งหมด (รวมที่ปิด)")
    public ApiResponse<List<CategoryResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "👑 สร้างหมวด")
    public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategoryUpsertRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "👑 แก้หมวด")
    public ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryUpsertRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "👑 ปิดหมวด (soft delete)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PatchMapping("/sort")
    @Operation(summary = "👑 จัดลำดับหมวด [{id, sortOrder}]")
    public ApiResponse<List<CategoryResponse>> sort(@RequestBody @Size(max = 500) List<@Valid SortItem> items) {
        return ApiResponse.ok(service.sort(items));
    }
}
