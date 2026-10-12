package com.techbyte.ExamGuardBE.controller.response;

import com.techbyte.ExamGuardBE.enums.*;
import java.time.OffsetDateTime;

public record StudentExamResponse(Long id, String title, String description, Long subjectId, String subjectCode,
        String subjectName, String teacherName, ExamStatus status, OffsetDateTime startsAt, OffsetDateTime endsAt,
        int durationMinutes, int maxAttempts, int questionCount, long attemptsUsed, long attemptsRemaining,
        ExamParticipationStatus participationStatus, String participationMessage, boolean canStart, boolean canResume,
        Long activeSessionId, OffsetDateTime activeSessionExpiresAt, ResultVisibility resultVisibility, OffsetDateTime serverTime) {}
