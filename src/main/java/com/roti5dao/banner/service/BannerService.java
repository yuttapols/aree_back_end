package com.roti5dao.banner.service;

import com.roti5dao.banner.dto.BannerDtos.AdminBanner;
import com.roti5dao.banner.dto.BannerDtos.BannerReorderRequest;
import com.roti5dao.banner.dto.BannerDtos.BannerUpsertRequest;
import com.roti5dao.banner.dto.BannerDtos.PublicBanner;
import com.roti5dao.banner.entity.Banner;
import com.roti5dao.banner.entity.BannerPlacement;
import com.roti5dao.banner.repository.BannerRepository;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import com.roti5dao.common.storage.FileStorageService;
import com.roti5dao.common.storage.ImageUrlValidator;
import com.roti5dao.common.storage.StorageFolder;
import com.roti5dao.promotion.repository.PromotionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

/** banner หน้าร้าน — แอดมินสร้างเองทุกใบ (ไม่สร้างอัตโนมัติจากโปรโมชั่น) */
@Service
public class BannerService {

    private static final long MAX_IMAGE_BYTES = DataSize.ofMegabytes(2).toBytes();

    private final BannerRepository repository;
    private final PromotionRepository promotionRepository;
    private final FileStorageService storage;
    private final ImageUrlValidator imageUrlValidator;
    private final Clock clock;

    public BannerService(BannerRepository repository, PromotionRepository promotionRepository,
                         FileStorageService storage, ImageUrlValidator imageUrlValidator, Clock clock) {
        this.repository = repository;
        this.promotionRepository = promotionRepository;
        this.storage = storage;
        this.imageUrlValidator = imageUrlValidator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PublicBanner> visible(BannerPlacement placement) {
        return repository.findVisible(placement, Instant.now(clock)).stream().map(PublicBanner::of).toList();
    }

    @Transactional(readOnly = true)
    public List<AdminBanner> search(BannerPlacement placement, Boolean active) {
        return repository.search(placement, active).stream().map(AdminBanner::of).toList();
    }

    @Transactional(readOnly = true)
    public AdminBanner get(Long id) {
        return AdminBanner.of(banner(id));
    }

    /** อัปรูป banner (≤ 2MB) → ได้ url ไปใส่ imageUrl / imageUrlMobile */
    public String uploadImage(MultipartFile file) {
        return storage.store(file, StorageFolder.BANNERS, MAX_IMAGE_BYTES).location();
    }

    @Transactional
    public AdminBanner create(BannerUpsertRequest req) {
        Banner b = new Banner();
        apply(b, req);
        if (req.sortOrder() == null) {
            b.setSortOrder(repository.maxSortOrder(req.placement()) + 1);
        }
        return AdminBanner.of(repository.saveAndFlush(b));
    }

    @Transactional
    public AdminBanner update(Long id, BannerUpsertRequest req) {
        Banner b = banner(id);
        String oldImage = b.getImageUrl();
        String oldMobile = b.getImageUrlMobile();
        apply(b, req);
        repository.flush();
        deleteIfUnused(oldImage, b);
        deleteIfUnused(oldMobile, b);
        return AdminBanner.of(b);
    }

    @Transactional
    public void delete(Long id) {
        Banner b = banner(id);
        repository.delete(b);
        repository.flush();
        deleteIfUnused(b.getImageUrl(), null);
        deleteIfUnused(b.getImageUrlMobile(), null);
    }

    /** เรียงใหม่ตาม ids — ต้องส่งครบทุกใบของ placement นั้น */
    @Transactional
    public List<AdminBanner> reorder(BannerReorderRequest req) {
        List<Banner> banners = repository.findByPlacementOrderBySortOrderAscIdAsc(req.placement());
        var ids = req.ids();
        if (new HashSet<>(ids).size() != ids.size() || ids.size() != banners.size()
                || !banners.stream().map(Banner::getId).allMatch(ids::contains)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ต้องส่ง id ของ banner ใน placement นี้ครบทุกใบและไม่ซ้ำ");
        }
        for (Banner b : banners) {
            b.setSortOrder(ids.indexOf(b.getId()));
        }
        repository.flush();
        return repository.findByPlacementOrderBySortOrderAscIdAsc(req.placement()).stream().map(AdminBanner::of).toList();
    }

    private void apply(Banner b, BannerUpsertRequest req) {
        if (req.startAt() != null && req.endAt() != null && !req.endAt().isAfter(req.startAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "วันสิ้นสุดต้องหลังวันเริ่ม");
        }
        if (req.promotionId() != null && !promotionRepository.existsById(req.promotionId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ไม่พบโปรโมชั่นที่ลิงก์");
        }
        b.setPlacement(req.placement());
        b.setImageUrl(imageUrlValidator.requireStoredOrNull(req.imageUrl()));
        b.setImageUrlMobile(imageUrlValidator.requireStoredOrNull(req.imageUrlMobile()));
        b.setTitle(trim(req.title()));
        b.setTitleEn(trim(req.titleEn()));
        b.setSubtitle(trim(req.subtitle()));
        b.setSubtitleEn(trim(req.subtitleEn()));
        b.setCtaLabel(trim(req.ctaLabel()));
        b.setCtaLabelEn(trim(req.ctaLabelEn()));
        b.setLinkUrl(trim(req.linkUrl()));
        b.setPromotionId(req.promotionId());
        if (req.sortOrder() != null) {
            b.setSortOrder(req.sortOrder());
        }
        b.setActive(req.active());
        b.setStartAt(req.startAt());
        b.setEndAt(req.endAt());
    }

    /** ลบรูปบน Cloudinary เมื่อไม่มี banner/โปรอื่นใช้แล้ว (current = banner ที่ยังอยู่ ถ้ามี) */
    private void deleteIfUnused(String url, Banner current) {
        if (url == null || (current != null && (url.equals(current.getImageUrl())
                || Objects.equals(url, current.getImageUrlMobile())))) {
            return;
        }
        if (!repository.isImageUsedByOther(url, current == null ? -1L : current.getId())
                && !promotionRepository.existsByBannerUrl(url)) {
            storage.deletePublicLater(url);
        }
    }

    private Banner banner(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("banner"));
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
