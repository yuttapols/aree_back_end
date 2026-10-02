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
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** เก็บไฟล์รูปบนดิสก์ (STORAGE_DIR) — ชื่อไฟล์เป็น UUID ที่ระบบสร้างเอง ไม่ใช้ชื่อจาก client */
@Service
public class FileStorageService {

    private static final Pattern KEY = Pattern.compile("^([a-z]+)/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.(jpg|png|webp)$");

    public record Stored(String location, ImageType type) {
    }

    public record LoadedFile(Resource resource, ImageType type) {
    }

    private final Path root;
    private final String publicPrefix;
    private final long maxBytes;
    private final Clock clock;

    public FileStorageService(AppProperties props, Clock clock) {
        var cfg = props.storage();
        this.root = Path.of(cfg.localDir()).toAbsolutePath().normalize();
        this.publicPrefix = cfg.publicUrlPrefix().replaceAll("/+$", "");
        this.maxBytes = cfg.maxFileSize().toBytes();
        this.clock = clock;
    }

    /** public folder → location เป็น URL (/files/...) · private folder → location เป็น storage key */
    public Stored store(MultipartFile file, StorageFolder folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_INVALID);
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        try {
            byte[] bytes = file.getBytes();
            ImageType type = ImageType.detect(Arrays.copyOf(bytes, Math.min(bytes.length, 16)))
                    .orElseThrow(() -> new BusinessException(ErrorCode.FILE_INVALID));
            LocalDate today = LocalDate.now(clock);
            String key = "%s/%04d/%02d/%s.%s".formatted(folder.dir(), today.getYear(), today.getMonthValue(),
                    UUID.randomUUID(), type.extension());
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            return new Stored(folder.isPublic() ? publicPrefix + "/" + key : key, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** relativePath = ส่วนหลัง /files/ — รับเฉพาะโฟลเดอร์ public ที่ตรง pattern เป๊ะ (กัน traversal และ slips) */
    public LoadedFile loadPublic(String relativePath) {
        return load(relativePath, true);
    }

    public LoadedFile loadPrivate(String key) {
        return load(key, false);
    }

    private LoadedFile load(String key, boolean publicOnly) {
        var m = key == null ? null : KEY.matcher(key);
        if (m == null || !m.matches() || StorageFolder.isPublicDir(m.group(1)) != publicOnly) {
            throw new NotFoundException("ไฟล์");
        }
        Path p = resolve(key);
        if (!Files.isRegularFile(p)) {
            throw new NotFoundException("ไฟล์");
        }
        ImageType type = ImageType.fromExtension(m.group(2)).orElseThrow(() -> new NotFoundException("ไฟล์"));
        return new LoadedFile(new FileSystemResource(p), type);
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new NotFoundException("ไฟล์");
        }
        return p;
    }
}
