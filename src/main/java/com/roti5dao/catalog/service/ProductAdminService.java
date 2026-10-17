package com.roti5dao.catalog.service;

import com.roti5dao.catalog.dto.CatalogDtos.AdminProductResponse;
import com.roti5dao.catalog.dto.CatalogDtos.OptionGroupResponse;
import com.roti5dao.catalog.dto.CatalogDtos.ProductImageResponse;
import com.roti5dao.catalog.dto.CatalogDtos.ProductOptionGroupLink;
import com.roti5dao.catalog.dto.CatalogDtos.ProductUpsertRequest;
import com.roti5dao.catalog.entity.Category;
import com.roti5dao.catalog.entity.OptionGroup;
import com.roti5dao.catalog.entity.Product;
import com.roti5dao.catalog.entity.ProductImage;
import com.roti5dao.catalog.entity.ProductOptionGroup;
import com.roti5dao.catalog.repository.CategoryRepository;
import com.roti5dao.catalog.repository.OptionGroupRepository;
import com.roti5dao.catalog.repository.ProductImageRepository;
import com.roti5dao.catalog.repository.ProductOptionGroupRepository;
import com.roti5dao.catalog.repository.ProductRepository;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.storage.FileStorageService;
import com.roti5dao.common.storage.ImageUrlValidator;
import com.roti5dao.common.storage.StorageFolder;
import com.roti5dao.common.util.LikeUtils;
import com.roti5dao.common.util.MoneyUtils;
import com.roti5dao.common.web.PageResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductAdminService {


    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository imageRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final ProductOptionGroupRepository mapRepository;
    private final FileStorageService storage;
    private final ImageUrlValidator imageUrlValidator;
    private final Clock clock;

    public ProductAdminService(ProductRepository productRepository, CategoryRepository categoryRepository,
                               ProductImageRepository imageRepository, OptionGroupRepository optionGroupRepository,
                               ProductOptionGroupRepository mapRepository, FileStorageService storage,
                               ImageUrlValidator imageUrlValidator, Clock clock) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.imageRepository = imageRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.mapRepository = mapRepository;
        this.storage = storage;
        this.imageUrlValidator = imageUrlValidator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> search(Long categoryId, String keyword, Boolean active, Pageable pageable) {
        return PageResponse.of(productRepository.searchAdmin(categoryId, active, LikeUtils.containsPattern(keyword), pageable),
                p -> toResponse(p, List.of(), List.of()));
    }

    @Transactional(readOnly = true)
    public AdminProductResponse get(Long id) {
        return detail(product(id));
    }

    @Transactional
    public AdminProductResponse create(ProductUpsertRequest req) {
        Product p = new Product();
        apply(p, req);
        // ต้องได้ id ก่อนถึงจะ gen รหัสได้ — ใส่รหัสชั่วคราวที่ไม่ซ้ำแน่นอนไว้ก่อน แล้วแทนใน transaction เดียวกัน
        p.setCode("TMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 26)); // code ยาวได้ 30
        productRepository.saveAndFlush(p);
        p.setCode(productCode(p.getCategory().getId(), p.getId()));
        return detail(productRepository.saveAndFlush(p));
    }

    /** รหัสเมนู = RT- + id ประเภท 3 หลัก + id เมนู 4 หลัก (เช่น RT-0010015) — gen ครั้งเดียวตอนสร้าง แก้ไม่ได้ */
    static String productCode(Long categoryId, Long productId) {
        return "RT-%03d%04d".formatted(categoryId, productId);
    }

    @Transactional
    public AdminProductResponse update(Long id, ProductUpsertRequest req) {
        Product p = product(id);
        apply(p, req);
        return detail(productRepository.saveAndFlush(p));
    }

    @Transactional
    public void delete(Long id) {
        // ลบ ≠ ปิดใช้งาน — แถวยังอยู่เพราะออเดอร์/ยอดขายย้อนหลังอ้างถึง
        product(id).setDeletedAt(Instant.now(clock));
    }

    /** พนักงานกด "ของหมด/มีของ" ได้ */
    @Transactional
    public AdminProductResponse setAvailability(Long id, boolean available) {
        Product p = product(id);
        p.setAvailable(available);
        return toResponse(p, List.of(), List.of());
    }

    @Transactional
    public AdminProductResponse addImage(Long id, MultipartFile file) {
        Product p = product(id);
        var stored = storage.store(file, StorageFolder.PRODUCTS);
        // เมนูละ 1 รูป — อัปใหม่ = แทนที่รูปเดิม (ลบรูปเก่าบน Cloudinary หลัง commit)
        for (ProductImage old : imageRepository.findByProductIdOrderBySortOrderAscIdAsc(id)) {
            imageRepository.delete(old);
            storage.deletePublicLater(old.getUrl());
        }
        ProductImage img = new ProductImage();
        img.setProductId(id);
        img.setUrl(stored.location());
        img.setSortOrder(0);
        img.setCreatedAt(Instant.now(clock));
        imageRepository.save(img);
        p.setImageUrl(stored.location());
        return detail(p);
    }

    @Transactional
    public AdminProductResponse deleteImage(Long id, Long imageId) {
        Product p = product(id);
        ProductImage img = imageRepository.findByIdAndProductId(imageId, id)
                .orElseThrow(() -> new NotFoundException("รูปสินค้า"));
        imageRepository.delete(img);
        if (img.getUrl().equals(p.getImageUrl())) {
            p.setImageUrl(imageRepository.findByProductIdOrderBySortOrderAscIdAsc(id).stream()
                    .filter(i -> !i.getId().equals(imageId)).map(ProductImage::getUrl).findFirst().orElse(null));
        }
        storage.deletePublicLater(img.getUrl());
        return detail(p);
    }

    /** แทนที่รายการ option group ของสินค้าทั้งหมด */
    @Transactional
    public AdminProductResponse setOptionGroups(Long id, List<ProductOptionGroupLink> links) {
        Product p = product(id);
        Set<Long> seen = new HashSet<>();
        for (ProductOptionGroupLink l : links) {
            if (!seen.add(l.optionGroupId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "option group ซ้ำ");
            }
        }
        mapRepository.deleteByProductId(id);
        mapRepository.flush();
        for (ProductOptionGroupLink l : links) {
            OptionGroup g = optionGroupRepository.findById(l.optionGroupId())
                    .orElseThrow(() -> new NotFoundException("กลุ่มตัวเลือก id=" + l.optionGroupId()));
            ProductOptionGroup m = new ProductOptionGroup();
            m.setId(new ProductOptionGroup.Key(id, g.getId()));
            m.setOptionGroup(g);
            m.setSortOrder(l.sortOrder());
            mapRepository.save(m);
        }
        mapRepository.flush();
        return detail(p);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void apply(Product p, ProductUpsertRequest req) {
        Category c = categoryRepository.findById(req.categoryId()).orElseThrow(() -> new NotFoundException("หมวดหมู่"));
        p.setCategory(c);
        BigDecimal price = MoneyUtils.scale(req.price());
        BigDecimal originalPrice = req.originalPrice() == null ? null : MoneyUtils.scale(req.originalPrice());
        if (originalPrice != null && originalPrice.compareTo(price) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ราคาก่อนลดต้องมากกว่าราคาขาย", "originalPrice");
        }
        p.setName(req.name().trim());
        p.setNameEn(blankToNull(req.nameEn()));
        p.setDescription(req.description());
        p.setDescriptionEn(blankToNull(req.descriptionEn()));
        p.setPrice(price);
        p.setOriginalPrice(originalPrice);
        p.setBadge(blankToNull(req.badge()));
        p.setImageUrl(imageUrlValidator.requireStoredOrNull(req.imageUrl()));
        p.setAvailable(req.available());
        p.setRecommended(req.recommended());
        p.setActive(req.active());
        p.setSortOrder(req.sortOrder());
    }

    private Product product(Long id) {
        return productRepository.findWithCategoryByIdIn(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("สินค้า"));
    }

    private AdminProductResponse detail(Product p) {
        List<ProductImageResponse> images = imageRepository.findByProductIdOrderBySortOrderAscIdAsc(p.getId()).stream()
                .map(ProductImageResponse::of).toList();
        List<OptionGroupResponse> groups = mapRepository.findByProductId(p.getId()).stream()
                .map(m -> OptionGroupResponse.of(m.getOptionGroup())).toList();
        return toResponse(p, images, groups);
    }

    private static AdminProductResponse toResponse(Product p, List<ProductImageResponse> images, List<OptionGroupResponse> groups) {
        return new AdminProductResponse(p.getId(), p.getCategory().getId(), p.getCategory().getName(), p.getCode(),
                p.getName(), p.getNameEn(), p.getDescription(), p.getDescriptionEn(), p.getPrice(), p.getOriginalPrice(),
                p.getBadge(), p.getImageUrl(), p.isAvailable(), p.isRecommended(),
                p.isActive(), p.getSortOrder(), p.getUpdatedAt(), images, groups);
    }
}
