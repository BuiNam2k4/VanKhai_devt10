package com.techbyte.ExamGuardBE.controller.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_]{3,50}", message = "Tên đăng nhập gồm 3-50 chữ cái, chữ số hoặc dấu gạch dưới")
    private String username;
    @NotBlank @Email @Size(max = 254)
    private String email;
    @NotBlank @Size(min = 8, max = 72)
    private String password;
    @NotBlank @Size(max = 150)
    private String fullName;
}
