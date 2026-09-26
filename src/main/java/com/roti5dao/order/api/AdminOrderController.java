package com.roti5dao.order.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.security.CurrentUser;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.common.web.PageQuery;
import com.roti5dao.common.web.PageResponse;
import com.roti5dao.order.dto.OrderDtos.AdminQuoteRequest;
import com.roti5dao.order.dto.OrderDtos.BoardItem;
import com.roti5dao.order.dto.OrderDtos.CancelRequest;
import com.roti5dao.order.dto.OrderDtos.OrderListItem;
import com.roti5dao.order.dto.OrderDtos.OrderResponse;
import com.roti5dao.order.dto.OrderDtos.QuoteResponse;
import com.roti5dao.order.dto.OrderDtos.ReceiptResponse;
import com.roti5dao.order.dto.OrderDtos.StaffPaymentRequest;
import com.roti5dao.order.dto.OrderDtos.StatusChangeRequest;
import com.roti5dao.order.dto.OrderDtos.WalkInOrderRequest;
import com.roti5dao.order.entity.OrderChannel;
import com.roti5dao.order.entity.OrderStatus;
import com.roti5dao.order.service.OrderPaymentService;
import com.roti5dao.order.service.OrderQueryService;
import com.roti5dao.order.service.OrderService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** POS / คิวครัว — STAFF/ADMIN */
@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "4. Admin — POS / ออเดอร์", description = "หน้าร้าน คิวครัว (STAFF/ADMIN)")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderPaymentService orderPaymentService;
    private final OrderQueryService queryService;

    public AdminOrderController(OrderService orderService, OrderPaymentService orderPaymentService,
                                OrderQueryService queryService) {
        this.orderService = orderService;
        this.orderPaymentService = orderPaymentService;
        this.queryService = queryService;
    }

    @PostMapping("/quote")
    @Operation(summary = "🧑‍🍳 POS: คำนวณราคา (ใส่ customerPhone เพื่อใช้สิทธิ์สมาชิก)")
    public ApiResponse<QuoteResponse> quote(@Valid @RequestBody AdminQuoteRequest req) {
        return ApiResponse.ok(orderService.quoteWalkIn(req.items(), req.promoCode(), req.redeemPoints(), req.customerPhone()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "🧑‍🍳 POS: เปิดบิล WALK_IN (customerPhone ไม่บังคับ)")
    public ApiResponse<OrderResponse> create(@Valid @RequestBody WalkInOrderRequest req) {
        return ApiResponse.ok(orderService.createWalkIn(req, CurrentUser.requireId()));
    }

    @GetMapping
    @Operation(summary = "🧑‍🍳 รายการออเดอร์ (กรอง status / channel / date)")
    public ApiResponse<PageResponse<OrderListItem>> search(@RequestParam(required = false) OrderStatus status,
                                                           @RequestParam(required = false) OrderChannel channel,
                                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(queryService.search(status, channel, date,
                PageQuery.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @GetMapping("/board")
    @Operation(summary = "🧑‍🍳 คิวครัววันนี้ (CONFIRMED / PREPARING / READY)")
    public ApiResponse<List<BoardItem>> board() {
        return ApiResponse.ok(queryService.board());
    }

    @GetMapping("/{id}")
    @Operation(summary = "🧑‍🍳 รายละเอียดออเดอร์")
    public ApiResponse<OrderResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(queryService.get(id));
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "🧑‍🍳 รับเงิน (CASH คำนวณเงินทอน, จ่ายผสมได้) — amount ว่าง = ยอดคงค้าง")
    public ApiResponse<OrderResponse> pay(@PathVariable Long id, @Valid @RequestBody StaffPaymentRequest req) {
        return ApiResponse.ok(orderPaymentService.payByStaff(id, req, CurrentUser.requireId()));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "🧑‍🍳 เปลี่ยนสถานะ → PREPARING / READY / COMPLETED")
    public ApiResponse<OrderResponse> status(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req) {
        return ApiResponse.ok(orderService.changeStatus(id, req.status()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "🧑‍🍳 ยกเลิกออเดอร์ (คืนเงิน/แต้ม/โควต้าโปร)")
    public ApiResponse<OrderResponse> cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return ApiResponse.ok(orderService.cancel(id, req.reason(), CurrentUser.requireId()));
    }

    @GetMapping("/{id}/receipt")
    @Operation(summary = "🧑‍🍳 ข้อมูลใบเสร็จสำหรับพิมพ์")
    public ApiResponse<ReceiptResponse> receipt(@PathVariable Long id) {
        return ApiResponse.ok(queryService.receipt(id));
    }
}
