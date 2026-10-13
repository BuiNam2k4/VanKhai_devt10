package com.techbyte.ExamGuardBE.common;

import java.time.*;

public final class ExamSessionTime {
    private ExamSessionTime() {}
    public static LocalDateTime deadline(LocalDateTime start, int minutes, LocalDateTime examEnd) {
        var personalEnd = start.plusMinutes(minutes);
        return personalEnd.isBefore(examEnd) ? personalEnd : examEnd;
    }
    public static long remainingSeconds(LocalDateTime now, LocalDateTime expires) {
        if (!now.isBefore(expires)) return 0;
        var remaining = Duration.between(now, expires);
        return remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
    }
}
