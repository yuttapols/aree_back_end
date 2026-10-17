package com.roti5dao.banner.dto;

import com.roti5dao.banner.entity.Banner;
import com.roti5dao.banner.entity.BannerPlacement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class BannerDtos {

    private BannerDtos() {
    }

    /** มุมมองหน้าร้าน — ไม่มีข้อมูลการจัดการ (active/ช่วงเวลา/ผู้แก้) */
    public record PublicBanner(Long id, BannerPlacement placement, String imageUrl, String imageUrlMobile,
                               String title, String titleEn, String subtitle, String subtitleEn,
                               String ctaLabel, String ctaLabelEn, String linkUrl, Long promotionId) {
        public static PublicBanner of(Banner b) {
            return new PublicBanner(b.getId(), b.getPlacement(), b.getImageUrl(), b.getImageUrlMobile(),
                    b.getTitle(), b.getTitleEn(), b.getSubtitle(), b.getSubtitleEn(),
                    b.getCtaLabel(), b.getCtaLabelEn(), b.getLinkUrl(), b.getPromotionId());
        }
    }

    public record AdminBanner(Long id, BannerPlacement placement, String imageUrl, String imageUrlMobile,
                              String title, String titleEn, String subtitle, String subtitleEn,
                              String ctaLabel, String ctaLabelEn, String linkUrl, Long promotionId,
                              int sortOrder, boolean active, Instant startAt, Instant endAt,
                              Instant createdAt, Instant updatedAt) {
        public static AdminBanner of(Banner b) {
            return new AdminBanner(b.getId(), b.getPlacement(), b.getImageUrl(), b.getImageUrlMobile(),
                    b.getTitle(), b.getTitleEn(), b.getSubtitle(), b.getSubtitleEn(),
                    b.getCtaLabel(), b.getCtaLabelEn(), b.getLinkUrl(), b.getPromotionId(),
                    b.getSortOrder(), b.isActive(), b.getStartAt(), b.getEndAt(), b.getCreatedAt(), b.getUpdatedAt());
        }
    }

    /**
     * imageUrl/imageUrlMobile ต้องได้จาก POST /admin/banners/images เท่านั้น
     * linkUrl รับ path ภายในเว็บ (/menu) หรือ https:// · sortOrder ว่าง = ต่อท้าย
     */
    public record BannerUpsertRequest(
            @NotNull BannerPlacement placement,
            @NotBlank @Size(max = 500) String imageUrl,
            @Size(max = 500) String imageUrlMobile,
            @Size(max = 150) String title,
            @Size(max = 150) String titleEn,
            @Size(max = 300) String subtitle,
            @Size(max = 300) String subtitleEn,
            @Size(max = 50) String ctaLabel,
            @Size(max = 50) String ctaLabelEn,
            @Size(max = 500) @Pattern(regexp = "^(/(?![/\\\\])[^\\s]*|https://[^\\s]+)$",
                    message = "ลิงก์ต้องขึ้นต้นด้วย / หรือ https://") String linkUrl,
            Long promotionId,
            @Min(0) @Max(100000) Integer sortOrder,
            boolean active,
            Instant startAt,
            Instant endAt) {
    }

    /** ids = ลำดับใหม่ทั้งหมดของ placement นั้น (บนสุดก่อน) */
    public record BannerReorderRequest(@NotNull BannerPlacement placement,
                                       @NotEmpty @Size(max = 100) List<@NotNull Long> ids) {
    }

    public record UploadResponse(String url) {
    }
}
