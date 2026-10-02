package com.roti5dao.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.common.web.PageQuery;
import com.roti5dao.common.web.PageResponse;
import com.roti5dao.user.dto.UserDtos.CustomerSummary;
import com.roti5dao.user.dto.UserDtos.QuickRegisterRequest;
import com.roti5dao.user.dto.UserDtos.QuickRegisterResponse;
import com.roti5dao.user.service.CustomerAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** STAFF/ADMIN (กำหนดที่ SecurityConfig: /api/v1/admin/**) */
@RestController
@RequestMapping("/api/v1/admin/customers")
@Tag(name = "4. Admin — ลูกค้า", description = "ค้นหา/สมัครสมาชิกหน้าร้าน (STAFF/ADMIN)")
public class AdminCustomerController {

    private final CustomerAdminService service;

    public AdminCustomerController(CustomerAdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "🧑‍🍳 ค้นหาสมาชิก (เบอร์ / ชื่อ / อีเมล / รหัสสมาชิก)")
    public ApiResponse<PageResponse<CustomerSummary>> search(@RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.search(keyword, PageQuery.of(page, size, PageQuery.NEWEST_FIRST)));
    }

    @GetMapping("/lookup")
    @Operation(summary = "🧑‍🍳 หาสมาชิกจากเบอร์ (ใช้ที่ POS)")
    public ApiResponse<CustomerSummary> lookup(@RequestParam String phone) {
        return ApiResponse.ok(service.lookupByPhone(phone));
    }

    @GetMapping("/{id}")
    @Operation(summary = "🧑‍🍳 ข้อมูลสมาชิก")
    public ApiResponse<CustomerSummary> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/quick-register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "🧑‍🍳 สมัครให้ลูกค้าหน้าร้าน — ไม่ใส่รหัสจะได้ temporaryPassword (แสดงครั้งเดียว)")
    public ApiResponse<QuickRegisterResponse> quickRegister(@Valid @RequestBody QuickRegisterRequest req) {
        return ApiResponse.ok(service.quickRegister(req));
    }
}
