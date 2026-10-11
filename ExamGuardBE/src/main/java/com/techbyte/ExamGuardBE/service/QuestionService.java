package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.controller.request.QuestionRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.enums.*;

public interface QuestionService {
    QuestionPageResponse findAll(UserResponse actor, String keyword, Long subjectId, Difficulty difficulty,
                                 QuestionType type, Boolean active, String sortBy, int page, int size);
    QuestionResponse findById(UserResponse actor, Long id);
    QuestionResponse save(UserResponse actor, QuestionRequest request);
    QuestionResponse update(UserResponse actor, Long id, QuestionRequest request);
    void delete(UserResponse actor, Long id);
}
