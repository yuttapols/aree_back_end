package com.roti5dao.dashboard.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.dashboard.dto.DashboardDtos.GroupBy;
import com.roti5dao.dashboard.dto.DashboardDtos.HourlyPoint;
import com.roti5dao.dashboard.dto.DashboardDtos.PaymentMethodShare;
import com.roti5dao.dashboard.dto.DashboardDtos.Summary;
import com.roti5dao.dashboard.dto.DashboardDtos.Today;
import com.roti5dao.dashboard.dto.DashboardDtos.TopProduct;
import com.roti5dao.dashboard.dto.DashboardDtos.TrendPoint;
import com.roti5dao.dashboard.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "8. Dashboard", description = "รายงาน (นับเฉพาะออเดอร์ COMPLETED)")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 สรุปยอดขาย / ออเดอร์ / สมาชิกใหม่ / แต้ม")
    public ApiResponse<Summary> summary(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.summary(from, to));
    }

    @GetMapping("/sales-trend")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 กราฟยอดขาย (DAY / WEEK / MONTH)")
    public ApiResponse<List<TrendPoint>> salesTrend(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                                    @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                                    @RequestParam(defaultValue = "DAY") GroupBy groupBy) {
        return ApiResponse.ok(service.salesTrend(from, to, groupBy));
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 สินค้าขายดี")
    public ApiResponse<List<TopProduct>> topProducts(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                                     @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to,
                                                     @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(service.topProducts(from, to, limit));
    }

    @GetMapping("/payment-methods")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 สัดส่วนช่องทางชำระเงิน")
    public ApiResponse<List<PaymentMethodShare>> paymentMethods(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate from,
                                                                @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate to) {
        return ApiResponse.ok(service.paymentMethods(from, to));
    }

    @GetMapping("/hourly")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 ยอดขายรายชั่วโมง")
    public ApiResponse<List<HourlyPoint>> hourly(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate date) {
        return ApiResponse.ok(service.hourly(date));
    }

    /** สรุปวันนี้ — หน้าแรกของพนักงาน (STAFF/ADMIN) */
    @GetMapping("/today")
    @Operation(summary = "🧑‍🍳 สรุปวันนี้ (หน้าแรกพนักงาน)")
    public ApiResponse<Today> today() {
        return ApiResponse.ok(service.today());
    }
}
