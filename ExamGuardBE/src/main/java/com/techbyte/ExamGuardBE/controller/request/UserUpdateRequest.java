package com.techbyte.ExamGuardBE.controller.request;

import com.techbyte.ExamGuardBE.enums.RoleName;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UserUpdateRequest {
    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9_]{3,50}", message = "Tên đăng nhập gồm 3-50 chữ cái, chữ số hoặc dấu gạch dưới")
    private String username;
    @NotBlank @Email @Size(max = 254)
    private String email;
    @Size(min = 8, max = 72)
    private String password;
    @NotBlank @Size(max = 150)
    private String fullName;
    @NotEmpty
    private Set<RoleName> roles;
    private boolean enabled;
}
