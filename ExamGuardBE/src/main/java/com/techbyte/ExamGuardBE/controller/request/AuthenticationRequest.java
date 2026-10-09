package com.techbyte.ExamGuardBE.controller.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthenticationRequest {
    @NotBlank @Size(max = 50)
    private String username;
    @NotBlank @Size(max = 72)
    private String password;
}
