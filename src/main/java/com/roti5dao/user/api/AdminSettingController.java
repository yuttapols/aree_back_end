package com.roti5dao.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.common.audit.SecurityAuditService;
import com.roti5dao.common.audit.SecurityAuditService.Event;
import com.roti5dao.common.security.CurrentUser;
import com.roti5dao.common.setting.SystemSetting;
import com.roti5dao.common.setting.SystemSettingService;
import com.roti5dao.common.web.ApiResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "5. Admin — ตั้งค่า", description = "system_setting (ADMIN)")
public class AdminSettingController {

    private final SystemSettingService service;
    private final SecurityAuditService audit;

    public AdminSettingController(SystemSettingService service, SecurityAuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    public record SettingResponse(String key, String value, String valueType, String description) {
        static SettingResponse of(SystemSetting s) {
            return new SettingResponse(s.getSettingKey(), s.getSettingValue(), s.getValueType(), s.getDescription());
        }
    }

    public record SettingUpdateRequest(@NotEmpty @Size(max = 50) Map<String, String> values) {
    }

    @GetMapping
    @Operation(summary = "👑 การตั้งค่าทั้งหมด")
    public ApiResponse<List<SettingResponse>> list() {
        return ApiResponse.ok(service.findAll().stream().map(SettingResponse::of).toList());
    }

    @PutMapping
    @Operation(summary = "👑 แก้การตั้งค่า {values: {key: value}}")
    public ApiResponse<List<SettingResponse>> update(@jakarta.validation.Valid @RequestBody SettingUpdateRequest req) {
        Long actor = CurrentUser.requireId();
        var result = service.update(req.values(), CurrentUser.actorOf(actor)).stream().map(SettingResponse::of).toList();
        audit.record(Event.SETTINGS_UPDATED, actor, "keys=" + String.join(",", req.values().keySet()));
        return ApiResponse.ok(result);
    }
}
