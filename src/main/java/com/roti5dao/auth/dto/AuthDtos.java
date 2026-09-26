package com.roti5dao.auth.dto;

import com.roti5dao.user.dto.UserDtos.MeResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(@NotBlank @Size(max = 20) String phone,
                                  @NotBlank @Size(max = 100) String password,
                                  @NotBlank @Size(max = 50) String nickname,
                                  @Email @Size(max = 150) String email) {
        @Override
        public String toString() {
            return "RegisterRequest[phone=" + phone + ", nickname=" + nickname + "]";
        }
    }

    public record LoginRequest(@NotBlank @Size(max = 150) String username,
                               @NotBlank @Size(max = 100) String password) {
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + "]";
        }
    }

    /** refresh token ไม่อยู่ใน body — ส่งเป็น HttpOnly cookie เท่านั้น */
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt, MeResponse user) {
    }
}
