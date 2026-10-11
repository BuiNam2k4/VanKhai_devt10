package com.techbyte.ExamGuardBE.mapper;

import com.techbyte.ExamGuardBE.controller.response.ExamResponse;
import com.techbyte.ExamGuardBE.entity.*;
import java.time.*;
import java.util.Comparator;

public final class ExamMapper {
    private ExamMapper() {}

    public static ExamResponse toResponse(Exam exam, int questionCount) {
        return ExamResponse.builder().id(exam.getId()).title(exam.getTitle()).description(exam.getDescription())
                .subjectId(exam.getSubject().getId()).subjectCode(exam.getSubject().getCode()).subjectName(exam.getSubject().getName())
                .teacherId(exam.getTeacher().getId()).teacherName(exam.getTeacher().getFullName()).status(exam.getStatus())
                .startsAt(utc(exam.getStartsAt())).endsAt(utc(exam.getEndsAt()))
                .durationMinutes(exam.getDurationMinutes()).maxAttempts(exam.getMaxAttempts())
                .resultVisibility(exam.getResultVisibility()).shuffleQuestions(exam.isShuffleQuestions()).shuffleOptions(exam.isShuffleOptions())
                .questionCount(questionCount).classes(exam.getClasses().stream().sorted(Comparator.comparing(SchoolClass::getCode))
                        .map(item -> new ExamResponse.ClassResponse(item.getId(), item.getCode(), item.getName())).toList())
                .createdAt(utc(exam.getCreatedAt())).updatedAt(utc(exam.getUpdatedAt())).build();
    }

    private static OffsetDateTime utc(LocalDateTime time) { return time == null ? null : time.atOffset(ZoneOffset.UTC); }
}
