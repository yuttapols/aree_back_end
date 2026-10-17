package com.roti5dao.common.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.roti5dao.common.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * เก็บรูปบน Cloudinary (type=upload เปิดผ่าน URL ได้เลย)
 * ไม่เก็บ public_id แยก — ถอดเอาจาก URL ตอนลบ
 */
class CloudinaryStorage {

    private final Cloudinary cloudinary;
    private final String folder;
    private final Pattern publicUrl;

    CloudinaryStorage(AppProperties.Cloudinary cfg) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cfg.cloudName(),
                "api_key", cfg.apiKey(),
                "api_secret", cfg.apiSecret(),
                "secure", true));
        this.folder = cfg.folder();
        this.publicUrl = Pattern.compile("^https://res\\.cloudinary\\.com/" + Pattern.quote(cfg.cloudName())
                + "/image/upload/(?:v\\d+/)?(" + Pattern.quote(folder)
                + "/(?:products|avatars|banners)/[0-9a-f-]{36})\\.(?:jpg|png|webp)$");
    }

    /** คืน URL สาธารณะของรูป */
    String upload(byte[] bytes, StorageFolder target, String uuid) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "public_id", folder + "/" + target.dir() + "/" + uuid,
                    "resource_type", "image",
                    "type", "upload",
                    "overwrite", false,
                    "transformation", incoming(target)));
            return (String) result.get("secure_url");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** ย่อ + บีบอัดก่อนเก็บ (incoming transformation) — ต้นฉบับจากมือถือ 3-5MB เหลือราว 100-300KB */
    private static Transformation<?> incoming(StorageFolder target) {
        var t = new Transformation<>();
        return switch (target) {
            case AVATARS -> t.width(400).height(400).crop("fill").gravity("auto").quality("auto");
            case PRODUCTS -> t.width(1200).height(1200).crop("limit").quality("auto");
            // แบนเนอร์เต็มจอ (แนะนำ 1920×800) — ให้ความละเอียดมากกว่า
            case BANNERS -> t.width(1920).height(1920).crop("limit").quality("auto");
        };
    }

    boolean isPublicUrl(String url) {
        return url != null && publicUrl.matcher(url).matches();
    }

    /** ลบรูปจาก URL — URL ที่ไม่ใช่ของ Cloudinary account นี้จะถูกข้าม */
    void deletePublic(String url) {
        var m = url == null ? null : publicUrl.matcher(url);
        if (m == null || !m.matches()) {
            return;
        }
        try {
            cloudinary.uploader().destroy(m.group(1), ObjectUtils.asMap("type", "upload", "invalidate", true));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
