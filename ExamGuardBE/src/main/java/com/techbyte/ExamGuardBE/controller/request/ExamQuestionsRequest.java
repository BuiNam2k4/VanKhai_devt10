package com.techbyte.ExamGuardBE.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ExamQuestionsRequest {
    @NotNull @Size(max = 1000)
    private List<@NotNull @Valid Item> questions;

    public record Item(@NotNull @Positive Long questionId,
                       @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal points) {}
}
