package com.techbyte.ExamGuardBE.controller.response;

import com.techbyte.ExamGuardBE.enums.ExamStatus;
import java.math.BigDecimal;
import java.util.List;

public record ExamQuestionsResponse(Long examId, String title, ExamStatus status, BigDecimal totalPoints,
                                    List<Item> questions) {
    public record Item(Long id, QuestionResponse question, BigDecimal points, int displayOrder) {}
}
