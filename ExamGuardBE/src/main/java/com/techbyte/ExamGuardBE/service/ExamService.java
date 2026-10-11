package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.controller.request.ExamRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.enums.ExamStatus;

public interface ExamService {
    ExamPageResponse findAll(UserResponse actor, String keyword, Long subjectId, ExamStatus status,
                              Long teacherId, String sortBy, int page, int size);
    ExamResponse findById(UserResponse actor, Long id);
    ExamResponse save(UserResponse actor, ExamRequest request);
    ExamResponse update(UserResponse actor, Long id, ExamRequest request);
    ExamResponse publish(UserResponse actor, Long id);
    ExamResponse close(UserResponse actor, Long id);
    void delete(UserResponse actor, Long id);
}
