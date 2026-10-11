package com.techbyte.ExamGuardBE.mapper;

import com.techbyte.ExamGuardBE.controller.response.QuestionResponse;
import com.techbyte.ExamGuardBE.entity.*;
import java.util.List;

public final class QuestionMapper {
    private QuestionMapper() {}

    public static QuestionResponse toResponse(Question question, List<QuestionOption> options) {
        return QuestionResponse.builder().id(question.getId())
                .subjectId(question.getSubject().getId()).subjectCode(question.getSubject().getCode())
                .subjectName(question.getSubject().getName())
                .createdById(question.getCreatedBy().getId()).createdByName(question.getCreatedBy().getFullName())
                .content(question.getContent()).type(question.getType()).difficulty(question.getDifficulty())
                .active(question.isActive()).createdAt(question.getCreatedAt()).updatedAt(question.getUpdatedAt())
                .options(options.stream().map(option -> new QuestionResponse.OptionResponse(
                        option.getId(), option.getContent(), option.isCorrect(), option.getDisplayOrder())).toList())
                .build();
    }
}
