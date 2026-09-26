package com.roti5dao.catalog.service;

import com.roti5dao.catalog.dto.CatalogDtos.CategoryResponse;
import com.roti5dao.catalog.dto.CatalogDtos.MenuCategory;
import com.roti5dao.catalog.dto.CatalogDtos.OptionGroupResponse;
import com.roti5dao.catalog.dto.CatalogDtos.OptionItemResponse;
import com.roti5dao.catalog.dto.CatalogDtos.ProductDetail;
import com.roti5dao.catalog.dto.CatalogDtos.ProductImageResponse;
import com.roti5dao.catalog.dto.CatalogDtos.ProductSummary;
import com.roti5dao.catalog.dto.OrderableProduct;
import com.roti5dao.catalog.entity.OptionGroup;
import com.roti5dao.catalog.entity.Product;
import com.roti5dao.catalog.entity.ProductOptionGroup;
import com.roti5dao.catalog.repository.CategoryRepository;
import com.roti5dao.catalog.repository.ProductImageRepository;
import com.roti5dao.catalog.repository.ProductOptionGroupRepository;
import com.roti5dao.catalog.repository.ProductRepository;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.util.LikeUtils;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** อ่านข้อมูลเมนู: ฝั่ง public + ให้ module order ใช้ตรวจ/คิดราคา */
@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ProductOptionGroupRepository mapRepository;

    public ProductQueryService(CategoryRepository categoryRepository, ProductRepository productRepository,
                               ProductImageRepository imageRepository, ProductOptionGroupRepository mapRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.mapRepository = mapRepository;
    }

    public List<CategoryResponse> publicCategories() {
        return categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream().map(CategoryResponse::of).toList();
    }

    public List<ProductSummary> publicProducts(Long categoryId, String keyword, Boolean recommended) {
        List<Product> products = productRepository.findPublic(categoryId, recommended, LikeUtils.containsPattern(keyword));
        Set<Long> withOptions = productsWithOptions(products);
        return products.stream().map(p -> ProductSummary.of(p, withOptions.contains(p.getId()))).toList();
    }

    public List<MenuCategory> menu() {
        List<Product> products = productRepository.findPublic(null, null, null);
        Set<Long> withOptions = productsWithOptions(products);
        Map<Long, List<ProductSummary>> byCategory = products.stream()
                .collect(Collectors.groupingBy(p -> p.getCategory().getId(), LinkedHashMap::new,
                        Collectors.mapping(p -> ProductSummary.of(p, withOptions.contains(p.getId())), Collectors.toList())));
        return categoryRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(c -> new MenuCategory(CategoryResponse.of(c), byCategory.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    public ProductDetail publicProduct(Long id) {
        Product p = productRepository.findPublicById(id).orElseThrow(() -> new NotFoundException("สินค้า"));
        List<OptionGroupResponse> groups = mapRepository.findActiveByProductIds(List.of(id)).stream()
                .map(m -> publicGroup(m.getOptionGroup()))
                .toList();
        List<ProductImageResponse> images = imageRepository.findByProductIdOrderBySortOrderAscIdAsc(id).stream()
                .map(ProductImageResponse::of).toList();
        return new ProductDetail(p.getId(), p.getCategory().getId(), p.getCategory().getName(), p.getCode(), p.getName(),
                p.getDescription(), p.getPrice(), p.getImageUrl(), p.isAvailable(), p.isRecommended(), images, groups);
    }

    /** snapshot สินค้า + ตัวเลือก สำหรับคำนวณราคาออเดอร์ (รวมสินค้าที่ปิดขาย เพื่อให้แจ้ง error ได้ถูก) */
    public Map<Long, OrderableProduct> getForOrder(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ProductOptionGroup>> maps = mapRepository.findActiveByProductIds(productIds).stream()
                .collect(Collectors.groupingBy(m -> m.getId().getProductId()));
        return productRepository.findWithCategoryByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> new OrderableProduct(
                        p.getId(), p.getCategory().getId(), p.getName(), p.getPrice(), p.isOrderable(),
                        maps.getOrDefault(p.getId(), List.of()).stream().map(m -> toOrderGroup(m.getOptionGroup())).toList()),
                        (a, b) -> a));
    }

    private static OrderableProduct.Group toOrderGroup(OptionGroup g) {
        Map<Long, OrderableProduct.Option> options = g.getItems().stream()
                .map(i -> new OrderableProduct.Option(i.getId(), i.getName(), i.getExtraPrice(), i.isAvailable()))
                .collect(Collectors.toMap(OrderableProduct.Option::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        return new OrderableProduct.Group(g.getId(), g.getName(), g.getMinSelect(), g.getMaxSelect(), options);
    }

    private static OptionGroupResponse publicGroup(OptionGroup g) {
        return new OptionGroupResponse(g.getId(), g.getName(), g.getMinSelect(), g.getMaxSelect(), g.isActive(),
                g.getSortOrder(), g.getItems().stream().map(OptionItemResponse::of).toList());
    }

    private Set<Long> productsWithOptions(List<Product> products) {
        if (products.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(mapRepository.findProductIdsHavingActiveOptions(products.stream().map(Product::getId).toList()));
    }
}
