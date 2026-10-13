package com.techbyte.ExamGuardBE.controller.request;

import jakarta.validation.constraints.*;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_]{3,50}", message = "Tên đăng nhập gồm 3–50 chữ cái, chữ số hoặc dấu gạch dưới") String username,
        @NotBlank @Email @Size(max = 254) String email) {}
