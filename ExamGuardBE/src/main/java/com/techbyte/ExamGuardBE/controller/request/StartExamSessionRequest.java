package com.techbyte.ExamGuardBE.controller.request;

import jakarta.validation.constraints.*;

public record StartExamSessionRequest(@NotBlank @Size(min = 8, max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String deviceId) {}
