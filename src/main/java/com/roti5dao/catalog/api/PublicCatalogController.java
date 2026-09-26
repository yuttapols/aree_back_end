package com.roti5dao.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.catalog.dto.CatalogDtos.CategoryResponse;
import com.roti5dao.catalog.dto.CatalogDtos.MenuCategory;
import com.roti5dao.catalog.dto.CatalogDtos.ProductDetail;
import com.roti5dao.catalog.dto.CatalogDtos.ProductSummary;
import com.roti5dao.catalog.service.ProductQueryService;
import com.roti5dao.common.web.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "3. Public — เมนู", description = "เมนูสำหรับหน้า Landing (ไม่ต้อง login)")
public class PublicCatalogController {

    private final ProductQueryService service;

    public PublicCatalogController(ProductQueryService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    @Operation(summary = "🌐 หมวดที่เปิดใช้ เรียงตาม sortOrder")
    public ApiResponse<List<CategoryResponse>> categories() {
        return ApiResponse.ok(service.publicCategories());
    }

    @GetMapping("/products")
    @Operation(summary = "🌐 รายการสินค้า (กรองหมวด / คำค้น / เมนูแนะนำ)")
    public ApiResponse<List<ProductSummary>> products(@RequestParam(required = false) Long categoryId,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Boolean recommended) {
        return ApiResponse.ok(service.publicProducts(categoryId, keyword, recommended));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "🌐 รายละเอียดสินค้า + รูป + ตัวเลือก")
    public ApiResponse<ProductDetail> product(@PathVariable Long id) {
        return ApiResponse.ok(service.publicProduct(id));
    }

    @GetMapping("/menu")
    @Operation(summary = "🌐 หมวด + สินค้าในหมวด (เรียกครั้งเดียวสำหรับ landing)")
    public ApiResponse<List<MenuCategory>> menu() {
        return ApiResponse.ok(service.menu());
    }
}
