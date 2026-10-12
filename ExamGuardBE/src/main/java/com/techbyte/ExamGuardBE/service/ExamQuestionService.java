package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.controller.request.ExamQuestionsRequest;
import com.techbyte.ExamGuardBE.controller.response.*;

public interface ExamQuestionService {
    ExamQuestionsResponse findAll(UserResponse actor, Long examId);
    ExamQuestionsResponse replace(UserResponse actor, Long examId, ExamQuestionsRequest request);
    ExamPaperResponse preview(UserResponse actor, Long examId);
}
