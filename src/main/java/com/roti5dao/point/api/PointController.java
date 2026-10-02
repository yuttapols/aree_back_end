package com.roti5dao.point.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.security.CurrentUser;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.common.web.PageQuery;
import com.roti5dao.common.web.PageResponse;
import com.roti5dao.point.dto.PointDtos.AdjustRequest;
import com.roti5dao.point.dto.PointDtos.PointSummary;
import com.roti5dao.point.dto.PointDtos.PointTransactionResponse;
import com.roti5dao.point.service.PointService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "6. Points", description = "แต้มสะสม")
public class PointController {

    private static final Sort NEWEST = PageQuery.NEWEST_FIRST.and(PageQuery.ID_DESC);

    private final PointService pointService;

    public PointController(PointService pointService) {
        this.pointService = pointService;
    }

    @GetMapping("/api/v1/me/points")
    @Operation(summary = "👤 ยอดแต้มคงเหลือ + แต้มที่จะหมดอายุใน 30 วัน")
    public ApiResponse<PointSummary> mySummary() {
        return ApiResponse.ok(pointService.summary(CurrentUser.requireId()));
    }

    @GetMapping("/api/v1/me/points/transactions")
    @Operation(summary = "👤 ประวัติแต้มของฉัน")
    public ApiResponse<PageResponse<PointTransactionResponse>> myTransactions(@RequestParam(required = false) Integer page,
                                                                              @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(pointService.transactions(CurrentUser.requireId(), PageQuery.of(page, size, NEWEST)));
    }

    @GetMapping("/api/v1/admin/customers/{id}/points/transactions")
    @Operation(summary = "🧑‍🍳 ประวัติแต้มของลูกค้า")
    public ApiResponse<PageResponse<PointTransactionResponse>> customerTransactions(@PathVariable Long id,
                                                                                    @RequestParam(required = false) Integer page,
                                                                                    @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(pointService.transactions(id, PageQuery.of(page, size, NEWEST)));
    }

    @PostMapping("/api/v1/admin/customers/{id}/points/adjust")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 ปรับแต้มลูกค้า (+/−, ต้องระบุเหตุผล)")
    public ApiResponse<PointTransactionResponse> adjust(@PathVariable Long id, @Valid @RequestBody AdjustRequest req) {
        return ApiResponse.ok(pointService.adjust(id, req.points(), req.remark()));
    }
}
