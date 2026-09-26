package com.roti5dao.order.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.security.CurrentUser;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.common.web.PageQuery;
import com.roti5dao.common.web.PageResponse;
import com.roti5dao.order.dto.OrderDtos.PendingPaymentItem;
import com.roti5dao.order.dto.OrderDtos.VerifyPaymentRequest;
import com.roti5dao.order.service.OrderPaymentService;
import com.roti5dao.order.service.OrderQueryService;
import com.roti5dao.payment.dto.PaymentDtos.PaymentResponse;
import com.roti5dao.payment.entity.PaymentStatus;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
@Tag(name = "4. Admin — ตรวจสลิป", description = "การชำระเงิน (STAFF/ADMIN)")
public class AdminPaymentController {

    private final OrderQueryService queryService;
    private final OrderPaymentService orderPaymentService;

    public AdminPaymentController(OrderQueryService queryService, OrderPaymentService orderPaymentService) {
        this.queryService = queryService;
        this.orderPaymentService = orderPaymentService;
    }

    @GetMapping
    @Operation(summary = "🧑‍🍳 รายการชำระเงิน (status=PENDING = รอตรวจสลิป)")
    public ApiResponse<PageResponse<PendingPaymentItem>> list(@RequestParam(required = false) PaymentStatus status,
                                                              @RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(queryService.payments(status, PageQuery.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"))));
    }

    @PatchMapping("/{id}/verify")
    @Operation(summary = "🧑‍🍳 อนุมัติ/ปฏิเสธสลิป — ยอดครบ → ออเดอร์ CONFIRMED")
    public ApiResponse<PaymentResponse> verify(@PathVariable Long id, @Valid @RequestBody VerifyPaymentRequest req) {
        return ApiResponse.ok(orderPaymentService.verify(id, req.approve(), req.rejectReason(), CurrentUser.requireId()));
    }

    /** สลิปเป็นข้อมูลส่วนตัว (เลขบัญชี/ชื่อผู้โอน) — ดูได้เฉพาะพนักงาน และห้าม cache */
    @GetMapping("/{id}/slip")
    @Operation(summary = "🧑‍🍳 ดูรูปสลิป (private, no-store)")
    public ResponseEntity<Resource> slip(@PathVariable Long id) {
        var file = queryService.slip(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.type().contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(file.resource());
    }
}
