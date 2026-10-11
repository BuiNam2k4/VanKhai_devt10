package com.techbyte.ExamGuardBE.controller.response;

import com.techbyte.ExamGuardBE.enums.*;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class QuestionResponse {
    private Long id;
    private Long subjectId;
    private String subjectCode;
    private String subjectName;
    private Long createdById;
    private String createdByName;
    private String content;
    private QuestionType type;
    private Difficulty difficulty;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OptionResponse> options;

    public record OptionResponse(Long id, String content, boolean correct, int displayOrder) {}
}
