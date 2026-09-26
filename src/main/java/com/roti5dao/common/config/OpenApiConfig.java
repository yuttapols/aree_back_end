package com.roti5dao.common.config;

import com.roti5dao.common.exception.ErrorCode;
import com.roti5dao.common.web.ApiResponse.ApiError;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: /swagger-ui.html · OpenAPI JSON: /v3/api-docs (FE ใช้ generate client/model)
 * เปิดใน dev เสมอ · prod ปิดเป็นค่าเริ่มต้น (เปิดด้วย API_DOCS_ENABLED=true)
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String BEARER = "bearer";
    static final String REFRESH_COOKIE = "refreshCookie";
    private static final String ERROR_SCHEMA = "ErrorResponse";

    /** โครง error response สำหรับเอกสาร (รูปแบบเดียวกับ ApiResponse.fail) */
    record ErrorResponse(boolean success, Object data, ApiError error, Instant timestamp) {
    }

    @Bean
    OpenAPI roti5daoOpenApi(AppProperties props) {
        Components components = new Components()
                .addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("access token จาก /api/v1/auth/login (อายุ 15 นาที) — กด Authorize แล้ววางเฉพาะ token"))
                .addSecuritySchemes(REFRESH_COOKIE, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE)
                        .name(props.security().refreshCookie().name())
                        .description("HttpOnly cookie ที่ server ตั้งให้ตอน login/register — browser ส่งให้อัตโนมัติ"));
        ModelConverters.getInstance().read(ErrorResponse.class).forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("ร้านโรตี 5 ดาว API")
                        .version("v1")
                        .description(description()))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** ปรับ security + error response ต่อ endpoint ตาม prefix ของ path */
    @Bean
    OpenApiCustomizer roti5daoOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> {
                if (path.equals("/api/v1/auth/refresh") || path.equals("/api/v1/auth/logout")) {
                    op.setSecurity(List.of(new SecurityRequirement().addList(REFRESH_COOKIE)));
                } else if (path.startsWith("/api/v1/auth/") || path.startsWith("/files/")) {
                    op.setSecurity(List.of());
                } else if (path.startsWith("/api/v1/public/")) {
                    // สมาชิกแนบ token ได้ (ผูกออเดอร์/แต้ม/โปรสมาชิก) — guest ไม่ต้องแนบ
                    op.setSecurity(List.of(new SecurityRequirement(), new SecurityRequirement().addList(BEARER)));
                }
                addErrorResponses(path, op);
            }));
        };
    }

    private static void addErrorResponses(String path, Operation op) {
        boolean secured = !path.startsWith("/api/v1/public/") && !path.startsWith("/api/v1/auth/") && !path.startsWith("/files/");
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        errors.put("400", "VALIDATION_ERROR / BAD_REQUEST — ดู error.fields");
        if (secured) {
            errors.put("401", "UNAUTHORIZED / TOKEN_EXPIRED — TOKEN_EXPIRED ให้เรียก /auth/refresh แล้วลองใหม่ 1 ครั้ง");
            errors.put("403", "FORBIDDEN / PASSWORD_CHANGE_REQUIRED");
        }
        errors.put("422", "ผิดกติกาธุรกิจ — ดู error.code");
        errors.put("429", "RATE_LIMITED — ดู header Retry-After");
        errors.put("500", "INTERNAL_ERROR");
        Content content = new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA)));
        errors.forEach((status, desc) -> {
            if (!op.getResponses().containsKey(status)) {
                op.getResponses().addApiResponse(status, new ApiResponse().description(desc).content(content));
            }
        });
    }

    private static String description() {
        String codes = Arrays.stream(ErrorCode.values())
                .map(c -> "| `" + c.name() + "` | " + c.status().value() + " | " + c.defaultMessage() + " |")
                .collect(Collectors.joining("\n"));
        return """
                ### รูปแบบ response
                ทุก endpoint ตอบรูปแบบเดียวกัน:
                ```json
                { "success": true, "data": { }, "error": null, "timestamp": "2026-09-25T10:00:00Z" }
                { "success": false, "data": null, "error": { "code": "POINT_INSUFFICIENT", "message": "แต้มไม่พอ", "fields": [] }, "timestamp": "..." }
                ```
                FE ใช้ `error.code` map เป็นข้อความภาษาไทย · paging ตอบ `{ items, page, size, totalItems, totalPages }` (page เริ่ม 0, size สูงสุด 100)

                ### Authentication
                1. `POST /api/v1/auth/login` → ได้ `accessToken` ใน body + refresh token ใน **HttpOnly cookie** (path `/api/v1/auth`)
                2. แนบ `Authorization: Bearer <accessToken>` (เก็บใน memory เท่านั้น ห้ามเก็บ localStorage)
                3. ได้ 401 `TOKEN_EXPIRED` → `POST /api/v1/auth/refresh` (ต้องส่ง cookie: `withCredentials: true`) แล้วลองใหม่ 1 ครั้ง
                4. เปิดเว็บใหม่ → เรียก `/api/v1/auth/refresh` เพื่อกู้ session
                5. `user.passwordChangeRequired = true` → ต้องพาไปเปลี่ยนรหัส (`PUT /api/v1/me/password`) ก่อน API อื่นจะใช้ได้

                ### สิทธิ์
                🌐 public · 👤 login แล้ว · 🧑‍🍳 STAFF/ADMIN · 👑 ADMIN

                ### Error codes
                | Code | HTTP | ข้อความเริ่มต้น |
                |---|---|---|
                """ + codes;
    }
}
