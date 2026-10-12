package com.techbyte.ExamGuardBE.controller.response;

import com.techbyte.ExamGuardBE.enums.QuestionType;
import java.math.BigDecimal;
import java.util.List;

// The preview deliberately excludes grading keys. It is never a student's persisted attempt.
public record ExamPaperResponse(Long examId, String title, BigDecimal totalPoints, List<Item> questions) {
    public record Item(Long examQuestionId, String content, QuestionType type, BigDecimal points,
                       int displayOrder, List<Option> options) {}
    public record Option(String content, int displayOrder) {}
}
