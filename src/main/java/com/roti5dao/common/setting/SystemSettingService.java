package com.roti5dao.common.setting;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** อ่านค่า system_setting ผ่าน cache (หมดอายุ 60 วินาที เผื่อรันหลาย instance) */
@Service
public class SystemSettingService {

    private static final String ALL = "all";

    private final SystemSettingRepository repository;
    private final Clock clock;
    private final LoadingCache<String, Map<String, String>> cache;

    public SystemSettingService(SystemSettingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .build(k -> repository.findAll().stream()
                        .collect(Collectors.toUnmodifiableMap(SystemSetting::getSettingKey, SystemSetting::getSettingValue)));
    }

    public String getString(SettingKey key) {
        return cache.get(ALL).getOrDefault(key.key(), "");
    }

    public int getInt(SettingKey key) {
        try {
            return Integer.parseInt(getString(key).trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Setting " + key.key() + " is not a number");
        }
    }

    public BigDecimal getDecimal(SettingKey key) {
        return new BigDecimal(getString(key).trim());
    }

    public boolean getBoolean(SettingKey key) {
        return Boolean.parseBoolean(getString(key).trim());
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> findAll() {
        return repository.findAll().stream().sorted(Comparator.comparing(SystemSetting::getSettingKey)).toList();
    }

    /** อัปเดตได้เฉพาะ key ที่มีอยู่แล้ว และค่าต้องตรงกับ value_type */
    @Transactional
    public List<SystemSetting> update(Map<String, String> values, String actor) {
        Map<String, SystemSetting> existing = repository.findAllById(values.keySet()).stream()
                .collect(Collectors.toMap(SystemSetting::getSettingKey, Function.identity()));
        for (Map.Entry<String, String> e : values.entrySet()) {
            SystemSetting s = existing.get(e.getKey());
            if (s == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ไม่รู้จักการตั้งค่า: " + e.getKey());
            }
            String value = e.getValue() == null ? "" : e.getValue().trim();
            validate(s, value);
            s.setSettingValue(value);
            s.setUpdatedAt(Instant.now(clock));
            s.setUpdatedBy(actor);
        }
        cache.invalidateAll();
        return findAll();
    }

    private static void validate(SystemSetting s, String value) {
        if (value.length() > 1000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ค่าการตั้งค่ายาวเกินไป: " + s.getSettingKey());
        }
        boolean ok = switch (s.getValueType()) {
            case "NUMBER" -> isNonNegativeNumber(value) && (!s.getSettingKey().startsWith("point.") || isPositiveInt(value));
            case "BOOLEAN" -> value.equals("true") || value.equals("false");
            default -> true;
        };
        if (ok && s.getSettingKey().endsWith("_time")) {
            ok = isTime(value);
        }
        if (ok && s.getSettingKey().equals(SettingKey.POINT_REDEEM_MAX_PERCENT.key())) {
            ok = Integer.parseInt(value) <= 100;
        }
        if (!ok) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "ค่าไม่ถูกต้องสำหรับ " + s.getSettingKey());
        }
    }

    private static boolean isNonNegativeNumber(String v) {
        try {
            return new BigDecimal(v).signum() >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isPositiveInt(String v) {
        try {
            return Integer.parseInt(v) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isTime(String v) {
        try {
            LocalTime.parse(v);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
