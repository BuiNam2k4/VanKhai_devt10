package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.enums.*;

public interface StudentExamService {
    StudentExamPageResponse findAll(UserResponse actor, String keyword, Long subjectId, ExamStatus status, ExamWindow window, String sortBy, int page, int size);
    StudentExamResponse findById(UserResponse actor, Long id);
    SubjectPageResponse subjects(UserResponse actor, int page, int size);
}
