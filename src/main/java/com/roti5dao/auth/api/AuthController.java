package com.roti5dao.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.auth.dto.AuthDtos.LoginRequest;
import com.roti5dao.auth.dto.AuthDtos.RegisterRequest;
import com.roti5dao.auth.dto.AuthDtos.TokenResponse;
import com.roti5dao.auth.service.AuthService;
import com.roti5dao.auth.service.AuthService.AuthResult;
import com.roti5dao.common.exception.BusinessException;
import com.roti5dao.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "1. Auth", description = "สมัคร / เข้าสู่ระบบ / ต่ออายุ token / ออกจากระบบ")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieHelper cookies;

    public AuthController(AuthService authService, RefreshCookieHelper cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @Operation(summary = "🌐 สมัครสมาชิก (phone, password, nickname, email?) — ได้ token + refresh cookie")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        return respond(HttpStatus.CREATED, authService.register(req, http.getHeader(HttpHeaders.USER_AGENT)));
    }

    @PostMapping("/login")
    @Operation(summary = "🌐 เข้าสู่ระบบด้วยเบอร์โทรหรืออีเมล — ผิด 5 ครั้งล็อก 15 นาที")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        return respond(HttpStatus.OK, authService.login(req, http.getHeader(HttpHeaders.USER_AGENT)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "🌐 ออก access token ใหม่จาก refresh cookie (rotate ทุกครั้ง)")
    public ResponseEntity<? extends ApiResponse<?>> refresh(HttpServletRequest http) {
        try {
            return respond(HttpStatus.OK, authService.refresh(cookies.read(http), http.getHeader(HttpHeaders.USER_AGENT)));
        } catch (BusinessException e) {
            // refresh ไม่ผ่าน → ลบ cookie ทิ้งด้วย
            return ResponseEntity.status(e.getCode().status())
                    .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                    .body(ApiResponse.fail(e.getCode(), e.getMessage(), List.of()));
        }
    }

    @PostMapping("/logout")
    @Operation(summary = "🌐 ยกเลิก refresh token + ลบ cookie")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest http) {
        authService.logout(cookies.read(http));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(ApiResponse.ok());
    }

    private ResponseEntity<ApiResponse<TokenResponse>> respond(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.body()));
    }
}
