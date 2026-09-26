package com.roti5dao.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.security.CurrentUser;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.user.dto.UserDtos.StaffCreateRequest;
import com.roti5dao.user.dto.UserDtos.StaffResponse;
import com.roti5dao.user.dto.UserDtos.StaffUpdateRequest;
import com.roti5dao.user.service.StaffAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "5. Admin — พนักงาน", description = "ADMIN")
public class AdminStaffController {

    private final StaffAdminService service;

    public AdminStaffController(StaffAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "👑 พนักงานทั้งหมด")
    public ApiResponse<List<StaffResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @GetMapping("/{id}")
    @Operation(summary = "👑 ข้อมูลพนักงาน")
    public ApiResponse<StaffResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "👑 เพิ่มพนักงาน (ต้องเปลี่ยนรหัสเมื่อ login ครั้งแรก)")
    public ApiResponse<StaffResponse> create(@Valid @RequestBody StaffCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "👑 แก้/ระงับ/เปลี่ยน role/รีเซ็ตรหัส — revoke session ทันที")
    public ApiResponse<StaffResponse> update(@PathVariable Long id, @Valid @RequestBody StaffUpdateRequest req) {
        return ApiResponse.ok(service.update(id, req, CurrentUser.requireId()));
    }
}
