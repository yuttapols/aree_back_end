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
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductAdminService {

    private static final int MAX_IMAGES = 10;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductImageRepository imageRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final ProductOptionGroupRepository mapRepository;
    private final FileStorageService storage;
    private final Clock clock;

    public ProductAdminService(ProductRepository productRepository, CategoryRepository categoryRepository,
                               ProductImageRepository imageRepository, OptionGroupRepository optionGroupRepository,
                               ProductOptionGroupRepository mapRepository, FileStorageService storage, Clock clock) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.imageRepository = imageRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.mapRepository = mapRepository;
        this.storage = storage;
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
        if (productRepository.existsByCode(req.code())) {
            throw new BusinessException(ErrorCode.DUPLICATE_VALUE, "รหัสสินค้านี้ถูกใช้แล้ว");
        }
        Product p = new Product();
        apply(p, req);
        return detail(productRepository.saveAndFlush(p));
    }

    @Transactional
    public AdminProductResponse update(Long id, ProductUpsertRequest req) {
        Product p = product(id);
        if (productRepository.existsByCodeAndIdNot(req.code(), id)) {
            throw new BusinessException(ErrorCode.DUPLICATE_VALUE, "รหัสสินค้านี้ถูกใช้แล้ว");
        }
        apply(p, req);
        return detail(productRepository.saveAndFlush(p));
    }

    @Transactional
    public void delete(Long id) {
        product(id).setActive(false);
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
        if (imageRepository.countByProductId(id) >= MAX_IMAGES) {
            throw new BusinessException(ErrorCode.INVALID_OPERATION, "รูปสินค้าได้สูงสุด " + MAX_IMAGES + " รูป");
        }
        var stored = storage.store(file, StorageFolder.PRODUCTS);
        ProductImage img = new ProductImage();
        img.setProductId(id);
        img.setUrl(stored.location());
        img.setSortOrder((int) imageRepository.countByProductId(id));
        img.setCreatedAt(Instant.now(clock));
        imageRepository.save(img);
        if (p.getImageUrl() == null) {
            p.setImageUrl(stored.location());
        }
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

    private void apply(Product p, ProductUpsertRequest req) {
        Category c = categoryRepository.findById(req.categoryId()).orElseThrow(() -> new NotFoundException("หมวดหมู่"));
        p.setCategory(c);
        p.setCode(req.code());
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setPrice(MoneyUtils.scale(req.price()));
        p.setImageUrl(ImageUrlValidator.requireStoredOrNull(req.imageUrl()));
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
                p.getName(), p.getDescription(), p.getPrice(), p.getImageUrl(), p.isAvailable(), p.isRecommended(),
                p.isActive(), p.getSortOrder(), p.getUpdatedAt(), images, groups);
    }
}
