package com.techbyte.ExamGuardBE.controller.request;

import com.techbyte.ExamGuardBE.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class QuestionRequest {
    @NotNull @Positive
    private Long subjectId;
    @NotBlank @Size(max = 10000)
    private String content;
    @NotNull
    private QuestionType type;
    @NotNull
    private Difficulty difficulty;
    @NotNull
    private Boolean active = true;
    @NotNull @Size(max = 10) @Valid
    private List<@NotNull OptionRequest> options = new ArrayList<>();

    @Getter
    @Setter
    public static class OptionRequest {
        @Positive
        private Long id;
        @NotBlank @Size(max = 2000)
        private String content;
        @NotNull
        private Boolean correct;
    }
}
