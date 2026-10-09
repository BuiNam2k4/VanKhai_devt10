package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.controller.request.SubjectRequest;
import com.techbyte.ExamGuardBE.controller.response.*;

public interface SubjectService {
    SubjectPageResponse findAll(String keyword, String sortBy, int page, int size);
    SubjectResponse findById(Long id);
    SubjectResponse save(SubjectRequest request);
    SubjectResponse update(Long id, SubjectRequest request);
    void delete(Long id);
}
