package com.techbyte.ExamGuardBE.controller.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SchoolClassResponse {
    private Long id;
    private String code;
    private String name;
    private Long teacherId;
    private String teacherName;
    private long studentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
