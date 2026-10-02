package com.roti5dao.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.setting.SettingKey;
import com.roti5dao.common.setting.SystemSettingService;
import com.roti5dao.common.time.BusinessTime;
import com.roti5dao.common.web.ApiResponse;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
@Tag(name = "3. Public — ร้าน", description = "ข้อมูลร้าน")
public class PublicShopController {

    private final SystemSettingService settings;
    private final BusinessTime time;

    public PublicShopController(SystemSettingService settings, BusinessTime time) {
        this.settings = settings;
        this.time = time;
    }

    public record ShopInfo(String name, String phone, String openTime, String closeTime, boolean acceptOnlineOrder,
                           boolean openNow) {
    }

    /** เฉพาะข้อมูลร้านที่เปิดเผยได้ — ไม่ส่ง setting อื่น (เช่น กติกาแต้มภายใน) */
    @GetMapping("/shop-info")
    @Operation(summary = "🌐 ชื่อร้าน เวลาเปิด-ปิด เปิดรับออนไลน์หรือไม่")
    public ApiResponse<ShopInfo> shopInfo() {
        String open = settings.getString(SettingKey.SHOP_OPEN_TIME);
        String close = settings.getString(SettingKey.SHOP_CLOSE_TIME);
        return ApiResponse.ok(new ShopInfo(
                settings.getString(SettingKey.SHOP_NAME),
                settings.getString(SettingKey.SHOP_PHONE),
                open, close,
                settings.getBoolean(SettingKey.SHOP_ACCEPT_ONLINE_ORDER),
                isOpen(open, close)));
    }

    private boolean isOpen(String open, String close) {
        try {
            LocalTime o = LocalTime.parse(open);
            LocalTime c = LocalTime.parse(close);
            LocalTime now = time.timeNow();
            return o.isBefore(c) ? !now.isBefore(o) && now.isBefore(c) : !now.isBefore(o) || now.isBefore(c);
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
