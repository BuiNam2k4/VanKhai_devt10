package com.techbyte.ExamGuardBE.controller.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchoolClassRequest {
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,30}", message = "Mã lớp gồm 2-30 chữ cái, chữ số, _ hoặc -")
    private String code;
    @NotBlank @Size(max = 150)
    private String name;
    @NotNull @Positive
    private Long teacherId;
}
