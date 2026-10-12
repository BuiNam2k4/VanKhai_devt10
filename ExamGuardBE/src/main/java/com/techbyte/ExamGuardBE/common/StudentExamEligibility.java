package com.techbyte.ExamGuardBE.common;

import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.*;
import java.time.LocalDateTime;

public final class StudentExamEligibility {
    private StudentExamEligibility() {}

    public static ExamParticipationStatus evaluate(Exam exam, long attemptsUsed, ExamSession active, LocalDateTime now) {
        // Closing an exam prevents new attempts; a live existing attempt may still be resumed.
        if (active != null && active.getExpiresAt().isAfter(now) && exam.getEndsAt() != null && exam.getEndsAt().isAfter(now)) {
            return ExamParticipationStatus.IN_PROGRESS;
        }
        if (exam.getStatus() == ExamStatus.CLOSED) return ExamParticipationStatus.CLOSED;
        if (exam.getStatus() != ExamStatus.PUBLISHED || exam.getStartsAt() == null || exam.getEndsAt() == null
                || !exam.getEndsAt().isAfter(exam.getStartsAt()) || exam.getDurationMinutes() <= 0 || exam.getMaxAttempts() <= 0) {
            return ExamParticipationStatus.UNAVAILABLE;
        }
        if (now.isBefore(exam.getStartsAt())) return ExamParticipationStatus.UPCOMING;
        if (!now.isBefore(exam.getEndsAt())) return ExamParticipationStatus.EXPIRED;
        // Expired IN_PROGRESS rows must be finalized before a later task can allocate a new attempt.
        if (active != null) return ExamParticipationStatus.SESSION_EXPIRED;
        if (attemptsUsed >= exam.getMaxAttempts()) return ExamParticipationStatus.ATTEMPTS_EXHAUSTED;
        return ExamParticipationStatus.AVAILABLE;
    }

    public static String message(ExamParticipationStatus status) {
        return switch (status) {
            case AVAILABLE -> "Bài thi đang mở và bạn còn lượt tham gia.";
            case UPCOMING -> "Chưa đến thời gian mở bài thi.";
            case IN_PROGRESS -> "Bạn có một lượt thi đang làm; cần tiếp tục lượt hiện tại.";
            case SESSION_EXPIRED -> "Lượt đang làm đã hết giờ, cần được chốt trước khi tham gia lượt mới.";
            case ATTEMPTS_EXHAUSTED -> "Bạn đã sử dụng hết số lượt thi cho phép.";
            case EXPIRED -> "Đã hết thời gian tham gia bài thi.";
            case CLOSED -> "Giảng viên đã đóng bài thi, không nhận lượt mới.";
            case UNAVAILABLE -> "Bài thi chưa đáp ứng điều kiện tham gia.";
        };
    }
}
