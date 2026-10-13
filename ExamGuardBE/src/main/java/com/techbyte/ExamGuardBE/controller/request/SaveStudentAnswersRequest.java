package com.techbyte.ExamGuardBE.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record SaveStudentAnswersRequest(
        @NotBlank @Size(min = 8, max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String deviceId,
        @NotNull @Size(min = 1, max = 1000) List<@NotNull @Valid Answer> answers) {
    public record Answer(@NotNull @Positive Long sessionQuestionId,
                         @Positive Long selectedOptionId,
                         @Size(max = 10000) String answerText,
                         @PositiveOrZero Long version) {}
}
