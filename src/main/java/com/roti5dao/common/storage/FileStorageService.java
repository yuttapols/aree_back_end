package com.roti5dao.common.storage;

import com.roti5dao.common.config.AppProperties;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.exception.NotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/** เก็บไฟล์รูปบน Cloudinary (ถ้าตั้งค่า app.cloudinary) หรือบนดิสก์ (STORAGE_DIR) — ชื่อไฟล์เป็น UUID ที่ระบบสร้างเอง ไม่ใช้ชื่อจาก client */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Pattern KEY = Pattern.compile(
            "^(products|avatars|banners)/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.(jpg|png|webp)$");

    public record Stored(String location, ImageType type) {
    }

    public record LoadedFile(Resource resource, ImageType type) {
    }

    private final Path root;
    private final String publicPrefix;
    private final long maxBytes;
    private final Clock clock;
    /** null = ไม่ได้ตั้งค่า Cloudinary → ใช้ดิสก์ */
    private final CloudinaryStorage cloudinary;

    public FileStorageService(AppProperties props, Clock clock, Environment env) {
        var cfg = props.storage();
        this.root = Path.of(cfg.localDir()).toAbsolutePath().normalize();
        this.publicPrefix = cfg.publicUrlPrefix().replaceAll("/+$", "");
        this.maxBytes = cfg.maxFileSize().toBytes();
        this.clock = clock;
        this.cloudinary = props.cloudinary() != null && props.cloudinary().enabled()
                ? new CloudinaryStorage(props.cloudinary()) : null;
        // ดิสก์ของ Render หายทุกครั้งที่ deploy — prod ต้องใช้ Cloudinary เท่านั้น ไม่งั้นไม่ยอม start
        if (cloudinary == null && env.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("prod ต้องตั้ง CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET");
        }
    }

    /** location = URL ของรูป (Cloudinary หรือ /files/...) */
    public Stored store(MultipartFile file, StorageFolder folder) {
        return store(file, folder, maxBytes);
    }

    /** limitBytes = จำกัดขนาดเข้มกว่าค่ากลาง (เช่น banner 2MB) — ไม่เกิน max-file-size เสมอ */
    public Stored store(MultipartFile file, StorageFolder folder, long limitBytes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        if (file.getSize() > Math.min(limitBytes, maxBytes)) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        try {
            byte[] bytes = file.getBytes();
            ImageType type = ImageType.detect(Arrays.copyOf(bytes, Math.min(bytes.length, 16)))
                    .orElseThrow(() -> new BusinessException(ErrorCode.FILE_INVALID));
            if (cloudinary != null) {
                return new Stored(cloudinary.upload(bytes, folder, UUID.randomUUID().toString()), type);
            }
            LocalDate today = LocalDate.now(clock);
            String key = "%s/%04d/%02d/%s.%s".formatted(folder.dir(), today.getYear(), today.getMonthValue(),
                    UUID.randomUUID(), type.extension());
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return new Stored(publicPrefix + "/" + key, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** relativePath = ส่วนหลัง /files/ — รับเฉพาะ path ที่ตรง pattern เป๊ะ (กัน traversal) */
    public LoadedFile loadPublic(String relativePath) {
        var m = relativePath == null ? null : KEY.matcher(relativePath);
        if (m == null || !m.matches()) {
            throw new NotFoundException("ไฟล์");
        }
        Path p = resolve(relativePath);
        if (!Files.isRegularFile(p)) {
            throw new NotFoundException("ไฟล์");
        }
        ImageType type = ImageType.fromExtension(m.group(2)).orElseThrow(() -> new NotFoundException("ไฟล์"));
        return new LoadedFile(new FileSystemResource(p), type);
    }

    /** URL รูปสาธารณะที่ระบบออกให้ (ทั้ง /files/... และ Cloudinary ของ account นี้) */
    public boolean isStoredPublicUrl(String url) {
        return url != null && ((url.startsWith(publicPrefix + "/")
                && KEY.matcher(url.substring(publicPrefix.length() + 1)).matches())
                || (cloudinary != null && cloudinary.isPublicUrl(url)));
    }

    /**
     * ลบรูปสาธารณะที่เลิกใช้ — ทำหลัง commit และไม่โยน error (ลบไม่สำเร็จแค่เหลือไฟล์ค้าง ไม่กระทบข้อมูล)
     * ไฟล์บนดิสก์ไม่ลบ (dev เท่านั้น)
     */
    public void deletePublicLater(String url) {
        if (cloudinary == null || !cloudinary.isPublicUrl(url)) {
            return;
        }
        Runnable delete = () -> {
            try {
                cloudinary.deletePublic(url);
            } catch (RuntimeException e) {
                log.warn("ลบรูปบน Cloudinary ไม่สำเร็จ: {}", url, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete.run();
                }
            });
        } else {
            delete.run();
        }
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new NotFoundException("ไฟล์");
        }
        return p;
    }
}
