package com.techbyte.ExamGuardBE;

import com.techbyte.ExamGuardBE.common.StudentExamEligibility;
import com.techbyte.ExamGuardBE.entity.*;
import com.techbyte.ExamGuardBE.enums.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class StudentExamEligibilityTests {
    @Test
    void exactTimeBoundariesAndExistingAttemptTakePrecedenceOverNewAttemptEligibility() {
        var start = LocalDateTime.of(2026, 10, 12, 9, 0); var end = start.plusHours(2);
        var exam = new Exam(); exam.setStatus(ExamStatus.PUBLISHED); exam.setStartsAt(start); exam.setEndsAt(end); exam.setDurationMinutes(45); exam.setMaxAttempts(2);
        assertThat(StudentExamEligibility.evaluate(exam, 0, null, start.minusNanos(1))).isEqualTo(ExamParticipationStatus.UPCOMING);
        assertThat(StudentExamEligibility.evaluate(exam, 0, null, start)).isEqualTo(ExamParticipationStatus.AVAILABLE);
        assertThat(StudentExamEligibility.evaluate(exam, 2, null, start)).isEqualTo(ExamParticipationStatus.ATTEMPTS_EXHAUSTED);
        assertThat(StudentExamEligibility.evaluate(exam, 0, null, end)).isEqualTo(ExamParticipationStatus.EXPIRED);
        var active = new ExamSession(); active.setExpiresAt(start.plusMinutes(45));
        assertThat(StudentExamEligibility.evaluate(exam, 2, active, start)).isEqualTo(ExamParticipationStatus.IN_PROGRESS);
        assertThat(StudentExamEligibility.evaluate(exam, 1, active, active.getExpiresAt())).isEqualTo(ExamParticipationStatus.SESSION_EXPIRED);
        exam.setStatus(ExamStatus.CLOSED);
        assertThat(StudentExamEligibility.evaluate(exam, 1, active, start)).isEqualTo(ExamParticipationStatus.IN_PROGRESS);
        assertThat(StudentExamEligibility.evaluate(exam, 0, null, start)).isEqualTo(ExamParticipationStatus.CLOSED);
    }
}
