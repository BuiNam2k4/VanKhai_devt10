package com.techbyte.ExamGuardBE.controller.request;

import com.techbyte.ExamGuardBE.enums.ResultVisibility;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class ExamRequest {
    @NotNull @Positive
    private Long subjectId;
    @Positive
    private Long teacherId;
    @NotBlank @Size(max = 200)
    private String title;
    @Size(max = 10000)
    private String description;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    @NotNull @Positive
    private Integer durationMinutes;
    @NotNull @Positive
    private Integer maxAttempts;
    @NotNull
    private ResultVisibility resultVisibility;
    @NotNull
    private Boolean shuffleQuestions = false;
    @NotNull
    private Boolean shuffleOptions = false;
    @NotNull @Size(max = 1000)
    private Set<@NotNull @Positive Long> classIds = new HashSet<>();
}
