package com.techbyte.ExamGuardBE.auth;

import com.techbyte.ExamGuardBE.entity.User;
import com.techbyte.ExamGuardBE.enums.RoleName;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,50}", message = "Tên đăng nhập gồm 3–50 chữ cái, chữ số hoặc dấu gạch dưới") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 150) String fullName) {}

    public record LoginRequest(@NotBlank @Size(max = 50) String username,
                               @NotBlank @Size(max = 72) String password) {}

    public record UserResponse(Long id, String username, String email, String fullName, Set<RoleName> roles) {
        public static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getFullName(),
                    user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toSet()));
        }
    }

    public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {}
}
