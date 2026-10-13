package com.techbyte.ExamGuardBE.controller.response;

import com.techbyte.ExamGuardBE.enums.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ExamSessionResponse(Long id, Long examId, String examTitle, String subjectName, int attemptNumber,
        SessionStatus status, OffsetDateTime startedAt, OffsetDateTime expiresAt, OffsetDateTime serverTime,
        long remainingSeconds, boolean expired, boolean canContinue, BigDecimal maxScore, List<Question> questions,
        List<StudentAnswersResponse.Answer> answers) {
    public record Question(Long id, String content, QuestionType type, BigDecimal points, int displayOrder, List<Option> options) {}
    public record Option(Long id, String content, int displayOrder) {}
}
