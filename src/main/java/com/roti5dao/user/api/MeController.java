package com.roti5dao.user.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.roti5dao.auth.api.RefreshCookieHelper;
import com.roti5dao.auth.dto.AuthDtos.TokenResponse;
import com.roti5dao.auth.service.AuthService;
import com.roti5dao.common.security.CurrentUser;
import com.roti5dao.common.web.ApiResponse;
import com.roti5dao.user.dto.UserDtos.ChangePasswordRequest;
import com.roti5dao.user.dto.UserDtos.MeResponse;
import com.roti5dao.user.dto.UserDtos.ProfileResponse;
import com.roti5dao.user.dto.UserDtos.ProfileUpdateRequest;
import com.roti5dao.user.service.ProfileService;
import com.roti5dao.user.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** ข้อมูลของผู้ใช้ที่ login อยู่เท่านั้น — userId มาจาก token เสมอ ไม่รับจาก client */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "2. Me (สมาชิก)", description = "ข้อมูลของผู้ใช้ที่ login อยู่")
public class MeController {

    private final UserAccountService userAccountService;
    private final ProfileService profileService;
    private final AuthService authService;
    private final RefreshCookieHelper cookies;

    public MeController(UserAccountService userAccountService, ProfileService profileService, AuthService authService,
                        RefreshCookieHelper cookies) {
        this.userAccountService = userAccountService;
        this.profileService = profileService;
        this.authService = authService;
        this.cookies = cookies;
    }

    @GetMapping
    @Operation(summary = "👤 ข้อมูลผู้ใช้ปัจจุบัน (role, memberCode, pointsBalance, passwordChangeRequired)")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(userAccountService.getMe(CurrentUser.requireId()));
    }

    @GetMapping("/profile")
    @Operation(summary = "👤 ดูโปรไฟล์")
    public ApiResponse<ProfileResponse> profile() {
        return ApiResponse.ok(profileService.get(CurrentUser.requireId()));
    }

    @PutMapping("/profile")
    @Operation(summary = "👤 แก้โปรไฟล์")
    public ApiResponse<ProfileResponse> updateProfile(@Valid @RequestBody ProfileUpdateRequest req) {
        return ApiResponse.ok(profileService.update(CurrentUser.requireId(), req));
    }

    @PostMapping(path = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "👤 อัปโหลดรูปโปรไฟล์ (JPG/PNG/WEBP ≤ 5MB)")
    public ApiResponse<ProfileResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(profileService.updateAvatar(CurrentUser.requireId(), file));
    }

    /** เปลี่ยนรหัสผ่าน — revoke ทุก session เดิม แล้วออก token + cookie ใหม่ให้ session นี้ */
    @PutMapping("/password")
    @Operation(summary = "👤 เปลี่ยนรหัสผ่าน — revoke session อื่นทั้งหมด แล้วได้ token + cookie ใหม่")
    public ResponseEntity<ApiResponse<TokenResponse>> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                                                     HttpServletRequest http) {
        var result = authService.changePassword(CurrentUser.requireId(), req.currentPassword(), req.newPassword(),
                http.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.body()));
    }
}
