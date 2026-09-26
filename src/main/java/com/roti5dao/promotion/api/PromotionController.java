package com.roti5dao.promotion.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.security.AuthUser;
import com.roti5dao.common.security.CurrentUser;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.common.web.PageQuery;
import com.roti5dao.common.web.PageResponse;
import com.roti5dao.order.dto.OrderDtos.QuoteRequest;
import com.roti5dao.order.dto.OrderDtos.QuoteResponse;
import com.roti5dao.order.entity.OrderChannel;
import com.roti5dao.order.service.OrderService;
import com.roti5dao.promotion.dto.PromotionDtos.AdminPromotion;
import com.roti5dao.promotion.dto.PromotionDtos.PromotionUpsertRequest;
import com.roti5dao.promotion.dto.PromotionDtos.PublicPromotion;
import com.roti5dao.promotion.dto.PromotionDtos.UsageResponse;
import com.roti5dao.promotion.service.PromotionAdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "6. Promotions", description = "โปรโมชั่น — public และหลังบ้าน (ADMIN)")
public class PromotionController {

    private final PromotionAdminService service;
    private final OrderService orderService;

    public PromotionController(PromotionAdminService service, OrderService orderService) {
        this.service = service;
        this.orderService = orderService;
    }

    @GetMapping("/public/promotions")
    @Operation(summary = "🌐 โปรที่แสดงบน landing และยังไม่หมดอายุ")
    public ApiResponse<List<PublicPromotion>> landing() {
        return ApiResponse.ok(service.landing());
    }

    @GetMapping("/public/promotions/{id}")
    @Operation(summary = "🌐 รายละเอียดโปร")
    public ApiResponse<PublicPromotion> publicDetail(@PathVariable Long id) {
        return ApiResponse.ok(service.publicDetail(id));
    }

    /** ตรวจโค้ดกับตะกร้า — คำนวณผ่าน pricing pipeline เดียวกับ quote (error = โค้ดใช้ไม่ได้) */
    @PostMapping("/public/promotions/validate")
    @Operation(summary = "🌐 ตรวจโค้ดโปรกับตะกร้า (ผลเหมือน quote)")
    public ApiResponse<QuoteResponse> validate(@Valid @RequestBody QuoteRequest req) {
        Long customerId = CurrentUser.get().map(AuthUser::id).orElse(null);
        return ApiResponse.ok(orderService.quote(req.items(), req.promoCode(), 0, customerId, OrderChannel.ONLINE));
    }

    // ---------- admin (ADMIN)

    @GetMapping("/admin/promotions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 รายการโปรโมชั่น")
    public ApiResponse<PageResponse<AdminPromotion>> search(@RequestParam(required = false) Boolean active,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.search(active, PageQuery.of(page, size, Sort.by(Sort.Direction.DESC, "id"))));
    }

    @GetMapping("/admin/promotions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 รายละเอียดโปรโมชั่น")
    public ApiResponse<AdminPromotion> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping("/admin/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 สร้างโปรโมชั่น")
    public ApiResponse<AdminPromotion> create(@Valid @RequestBody PromotionUpsertRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/admin/promotions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 แก้โปรโมชั่น")
    public ApiResponse<AdminPromotion> update(@PathVariable Long id, @Valid @RequestBody PromotionUpsertRequest req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/admin/promotions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 ปิดโปรโมชั่น (soft delete)")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/admin/promotions/{id}/usages")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "👑 ประวัติการใช้โปร")
    public ApiResponse<PageResponse<UsageResponse>> usages(@PathVariable Long id,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(service.usages(id, PageQuery.of(page, size, Sort.by(Sort.Direction.DESC, "id"))));
    }
}
