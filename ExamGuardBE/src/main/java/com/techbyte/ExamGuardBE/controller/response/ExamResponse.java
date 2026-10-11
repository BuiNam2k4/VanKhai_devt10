package com.techbyte.ExamGuardBE.controller.response;

import com.techbyte.ExamGuardBE.enums.*;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class ExamResponse {
    private Long id;
    private String title;
    private String description;
    private Long subjectId;
    private String subjectCode;
    private String subjectName;
    private Long teacherId;
    private String teacherName;
    private ExamStatus status;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private int durationMinutes;
    private int maxAttempts;
    private ResultVisibility resultVisibility;
    private boolean shuffleQuestions;
    private boolean shuffleOptions;
    private int questionCount;
    private List<ClassResponse> classes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public record ClassResponse(Long id, String code, String name) {}
}
