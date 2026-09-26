package com.roti5dao.user.dto;

import com.roti5dao.user.entity.AppUser;
import com.roti5dao.user.entity.CustomerProfile;
import com.roti5dao.user.entity.Gender;
import com.roti5dao.user.entity.Role;
import com.roti5dao.user.entity.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class UserDtos {

    private UserDtos() {
    }

    public record MeResponse(Long id, Role role, String phone, String email, String nickname, String firstName,
                             String lastName, String memberCode, int pointsBalance, String avatarUrl,
                             boolean passwordChangeRequired) {
        public static MeResponse of(AppUser u, CustomerProfile p) {
            return new MeResponse(u.getId(), u.getRole(), u.getPhone(), u.getEmail(),
                    p == null ? null : p.getNickname(), p == null ? null : p.getFirstName(),
                    p == null ? null : p.getLastName(), p == null ? null : p.getMemberCode(),
                    p == null ? 0 : p.getPointsBalance(), p == null ? null : p.getAvatarUrl(),
                    u.isPasswordChangeRequired());
        }
    }

    public record ProfileResponse(String phone, String email, String memberCode, String firstName, String lastName,
                                  String nickname, LocalDate birthDate, Gender gender, String avatarUrl,
                                  int pointsBalance, int lifetimePoints, Instant memberSince) {
        public static ProfileResponse of(AppUser u, CustomerProfile p) {
            return new ProfileResponse(u.getPhone(), u.getEmail(), p.getMemberCode(), p.getFirstName(), p.getLastName(),
                    p.getNickname(), p.getBirthDate(), p.getGender(), p.getAvatarUrl(), p.getPointsBalance(),
                    p.getLifetimePoints(), p.getCreatedAt());
        }
    }

    public record ProfileUpdateRequest(
            @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @NotBlank @Size(max = 50) String nickname,
            @Past LocalDate birthDate,
            Gender gender,
            @Email @Size(max = 150) String email) {
    }

    public record ChangePasswordRequest(@NotBlank @Size(max = 100) String currentPassword,
                                        @NotBlank @Size(max = 100) String newPassword) {
    }

    /** ข้อมูลสมาชิกสำหรับพนักงาน (POS / ค้นหา) */
    public record CustomerSummary(Long userId, String memberCode, String phone, String email, String nickname,
                                  String firstName, String lastName, int pointsBalance, UserStatus status,
                                  Instant memberSince) {
        public static CustomerSummary of(CustomerProfile p) {
            AppUser u = p.getUser();
            return new CustomerSummary(u.getId(), p.getMemberCode(), u.getPhone(), u.getEmail(), p.getNickname(),
                    p.getFirstName(), p.getLastName(), p.getPointsBalance(), u.getStatus(), p.getCreatedAt());
        }
    }

    public record QuickRegisterRequest(@NotBlank @Size(max = 50) String nickname,
                                       @NotBlank @Size(max = 20) String phone,
                                       @Size(max = 100) String password) {
    }

    /** temporaryPassword แสดงครั้งเดียว (กรณีพนักงานไม่ได้ตั้งรหัสให้) — ผู้ใช้ต้องเปลี่ยนเมื่อ login ครั้งแรก */
    public record QuickRegisterResponse(CustomerSummary customer, String temporaryPassword) {
    }

    public record StaffResponse(Long id, String phone, String email, String nickname, Role role, UserStatus status,
                                Instant lastLoginAt, Instant createdAt) {
        public static StaffResponse of(AppUser u, CustomerProfile p) {
            return new StaffResponse(u.getId(), u.getPhone(), u.getEmail(), p == null ? null : p.getNickname(),
                    u.getRole(), u.getStatus(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record StaffCreateRequest(@NotBlank @Size(max = 20) String phone,
                                     @Email @Size(max = 150) String email,
                                     @NotBlank @Size(max = 100) String password,
                                     @NotBlank @Size(max = 50) String nickname,
                                     @NotNull Role role) {
    }

    public record StaffUpdateRequest(@Email @Size(max = 150) String email,
                                     @NotBlank @Size(max = 50) String nickname,
                                     @NotNull Role role,
                                     @NotNull UserStatus status,
                                     @Size(max = 100) String newPassword) {
    }
}
