package com.roti5dao.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.catalog.dto.CatalogDtos.OptionGroupResponse;
import com.roti5dao.catalog.dto.CatalogDtos.OptionGroupUpsertRequest;
import com.roti5dao.catalog.service.OptionGroupAdminService;
import com.roti5dao.common.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/option-groups")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "5. Admin — ตัวเลือกสินค้า", description = "กลุ่มตัวเลือก + items (ADMIN)")
public class AdminOptionGroupController {

    private final OptionGroupAdminService service;

    public AdminOptionGroupController(OptionGroupAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "👑 กลุ่มตัวเลือกทั้งหมด")
    public ApiResponse<List<OptionGroupResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "👑 รายละเอียดกลุ่มตัวเลือก")
    public ApiResponse<OptionGroupResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "👑 สร้างกลุ่มตัวเลือก + items")
    public ApiResponse<OptionGroupResponse> create(@Valid @RequestBody OptionGroupUpsertRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "👑 แก้กลุ่มตัวเลือก (item มี id = แก้, ไม่มี id = เพิ่ม, ไม่ส่ง = ลบ)")
    public ApiResponse<OptionGroupResponse> update(@PathVariable Long id, @Valid @RequestBody OptionGroupUpsertRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "👑 ปิดกลุ่มตัวเลือก")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
