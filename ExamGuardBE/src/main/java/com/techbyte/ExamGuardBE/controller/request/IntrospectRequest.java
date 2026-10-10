package com.techbyte.ExamGuardBE.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IntrospectRequest {
    @NotBlank
    private String token;
}
