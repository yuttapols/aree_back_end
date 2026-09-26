package com.roti5dao.payment.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.payment.dto.PaymentDtos.PaymentMethodResponse;
import com.roti5dao.payment.dto.PaymentDtos.PaymentMethodUpsertRequest;
import com.roti5dao.payment.dto.PaymentDtos.PublicPaymentMethod;
import com.roti5dao.payment.service.PaymentMethodService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "5. Admin — ประเภทการจ่ายเงิน", description = "public: ช่องทางที่เลือกได้ · admin: จัดการ")
public class PaymentMethodController {

    private final PaymentMethodService service;

    public PaymentMethodController(PaymentMethodService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/public/payment-methods")
    @Operation(summary = "🌐 ช่องทางชำระเงิน (channel=ONLINE เฉพาะที่ลูกค้าเลือกเองได้, ALL ทั้งหมด)")
    public ApiResponse<List<PublicPaymentMethod>> publicList(@RequestParam(defaultValue = "ONLINE") String channel) {
        return ApiResponse.ok(service.publicList(!"ALL".equalsIgnoreCase(channel)));
    }

    /** STAFF ดูได้ (ใช้ที่ POS) · แก้ไขได้เฉพาะ ADMIN */
    @GetMapping("/api/v1/admin/payment-methods")
    @Operation(summary = "🧑‍🍳 ประเภทการจ่ายเงินทั้งหมด")
    public ApiResponse<List<PaymentMethodResponse>> adminList() {
        return ApiResponse.ok(service.adminList());
    }

    @PostMapping("/api/v1/admin/payment-methods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 เพิ่มประเภทการจ่ายเงิน")
    public ApiResponse<PaymentMethodResponse> create(@Valid @RequestBody PaymentMethodUpsertRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/api/v1/admin/payment-methods/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 แก้ประเภทการจ่ายเงิน (แก้ code ไม่ได้)")
    public ApiResponse<PaymentMethodResponse> update(@PathVariable Long id, @Valid @RequestBody PaymentMethodUpsertRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }
}
