package com.techbyte.ExamGuardBE;

import com.techbyte.ExamGuardBE.common.ExamSessionTime;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class ExamSessionTimeTests {
    @Test void deadlineIsBoundedByExamClosingAndNeverExtendsAtTheExactExpiry() {
        var start = LocalDateTime.of(2026, 10, 13, 2, 0);
        assertThat(ExamSessionTime.deadline(start, 45, start.plusHours(2))).isEqualTo(start.plusMinutes(45));
        assertThat(ExamSessionTime.deadline(start, 45, start.plusMinutes(10))).isEqualTo(start.plusMinutes(10));
        assertThat(ExamSessionTime.remainingSeconds(start, start.plusSeconds(1).plusNanos(1))).isEqualTo(2);
        assertThat(ExamSessionTime.remainingSeconds(start, start)).isZero();
        assertThat(ExamSessionTime.remainingSeconds(start.plusSeconds(1), start)).isZero();
    }
}
