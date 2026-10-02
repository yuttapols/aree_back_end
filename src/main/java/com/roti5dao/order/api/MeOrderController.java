package com.roti5dao.order.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.security.CurrentUser;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.common.web.PageQuery;
import com.roti5dao.common.web.PageResponse;
import com.roti5dao.order.dto.OrderDtos.OrderListItem;
import com.roti5dao.order.dto.OrderDtos.OrderResponse;
import com.roti5dao.order.service.OrderQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/orders")
@Tag(name = "2. Me — ออเดอร์", description = "ประวัติออเดอร์ของสมาชิก")
public class MeOrderController {

    private final OrderQueryService queryService;

    public MeOrderController(OrderQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @Operation(summary = "👤 ประวัติออเดอร์ของฉัน")
    public ApiResponse<PageResponse<OrderListItem>> list(@RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(queryService.myOrders(CurrentUser.requireId(),
                PageQuery.of(page, size, PageQuery.NEWEST_FIRST)));
    }

    @GetMapping("/{orderNo}")
    @Operation(summary = "👤 รายละเอียดออเดอร์ของฉัน (ของคนอื่นตอบ 404)")
    public ApiResponse<OrderResponse> get(@PathVariable String orderNo) {
        return ApiResponse.ok(queryService.myOrder(CurrentUser.requireId(), orderNo));
    }
}
