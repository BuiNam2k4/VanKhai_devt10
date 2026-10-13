package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.controller.request.StartExamSessionRequest;

public interface ExamSessionService {
    StartResult start(UserResponse actor, Long examId, StartExamSessionRequest request, String ipAddress, String userAgent);
    ExamSessionResponse restore(UserResponse actor, Long sessionId, String deviceId);
    record StartResult(ExamSessionResponse session, boolean created) {}
}
