package com.techbyte.ExamGuardBE.controller.response;

import com.techbyte.ExamGuardBE.entity.StudentAnswer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record StudentAnswersResponse(Long sessionId, OffsetDateTime serverTime, OffsetDateTime expiresAt,
                                     boolean canSave, List<Answer> answers) {
    public record Answer(Long sessionQuestionId, Long selectedOptionId, String answerText, long version, OffsetDateTime savedAt) {}
    public static Answer from(StudentAnswer answer) {
        return new Answer(answer.getSessionQuestion().getId(), answer.getSelectedOption() == null ? null : answer.getSelectedOption().getId(),
                answer.getAnswerText(), answer.getVersion(), answer.getSavedAt().atOffset(ZoneOffset.UTC));
    }
}
