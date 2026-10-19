package com.roti5dao.order.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.security.CurrentUser;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.order.dto.OrderDtos.OnlineOrderRequest;
import com.roti5dao.order.dto.OrderDtos.OnlinePaymentRequest;
import com.roti5dao.order.dto.OrderDtos.OrderResponse;
import com.roti5dao.order.dto.OrderDtos.QuoteRequest;
import com.roti5dao.order.dto.OrderDtos.QuoteResponse;
import com.roti5dao.order.dto.OrderDtos.TrackResponse;
import com.roti5dao.order.entity.OrderChannel;
import com.roti5dao.order.service.OrderPaymentService;
import com.roti5dao.order.service.OrderQueryService;
import com.roti5dao.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** สั่งออนไลน์ได้ทั้ง guest และสมาชิก (ถ้าแนบ Bearer token จะผูกกับสมาชิกอัตโนมัติ) */
@RestController
@RequestMapping("/api/v1/public/orders")
@Tag(name = "3. Public — สั่งออนไลน์", description = "guest หรือสมาชิก (แนบ Bearer เพื่อผูกสมาชิก)")
public class PublicOrderController {

    private final OrderService orderService;
    private final OrderPaymentService orderPaymentService;
    private final OrderQueryService queryService;

    public PublicOrderController(OrderService orderService, OrderPaymentService orderPaymentService,
                                 OrderQueryService queryService) {
        this.orderService = orderService;
        this.orderPaymentService = orderPaymentService;
        this.queryService = queryService;
    }

    @PostMapping("/quote")
    @Operation(summary = "🌐 คำนวณราคาตะกร้า (ไม่บันทึก) รวมโปร/แลกแต้ม/แต้มที่จะได้")
    public ApiResponse<QuoteResponse> quote(@Valid @RequestBody QuoteRequest req) {
        Long customerId = CurrentUser.idOrNull();
        return ApiResponse.ok(orderService.quote(req.items(), req.promoCode(), req.redeemPoints(), customerId, OrderChannel.ONLINE));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "🌐 สร้างออเดอร์ ONLINE — guest ต้องส่ง guestName (guestPhone ไม่บังคับ ถ้าส่งต้องถูกรูปแบบ)")
    public ApiResponse<OrderResponse> create(@Valid @RequestBody OnlineOrderRequest req) {
        return ApiResponse.ok(orderService.createOnline(req, CurrentUser.get()));
    }

    @GetMapping("/track/{trackingToken}")
    @Operation(summary = "🌐 ติดตามออเดอร์ด้วย trackingToken")
    public ApiResponse<TrackResponse> track(@PathVariable UUID trackingToken) {
        return ApiResponse.ok(queryService.track(trackingToken));
    }

    @PostMapping("/track/{trackingToken}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "🌐 เลือกช่องทางชำระเงิน (ไม่มีสลิป) → PENDING รอร้านตรวจยอดเข้าเอง")
    public ApiResponse<TrackResponse> submitPayment(@PathVariable UUID trackingToken,
                                                    @Valid @RequestBody OnlinePaymentRequest req) {
        return ApiResponse.ok(orderPaymentService.submitPayment(trackingToken, req.methodCode(), req.amount(),
                req.referenceNo()));
    }
}
