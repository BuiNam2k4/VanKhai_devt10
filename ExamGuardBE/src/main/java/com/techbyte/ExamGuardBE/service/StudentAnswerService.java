package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.controller.request.SaveStudentAnswersRequest;
import com.techbyte.ExamGuardBE.controller.response.*;

public interface StudentAnswerService {
    StudentAnswersResponse findAll(UserResponse actor, Long sessionId, String deviceId);
    StudentAnswersResponse save(UserResponse actor, Long sessionId, SaveStudentAnswersRequest request);
}
