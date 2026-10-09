package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.controller.request.SchoolClassRequest;
import com.techbyte.ExamGuardBE.controller.response.*;

public interface SchoolClassService {
    SchoolClassPageResponse findAll(String keyword, Long teacherId, String sortBy, int page, int size);
    SchoolClassResponse findById(Long id);
    SchoolClassResponse save(SchoolClassRequest request);
    SchoolClassResponse update(Long id, SchoolClassRequest request);
    void delete(Long id);
    UserPageResponse findStudents(Long classId, String keyword, String sortBy, int page, int size);
    SchoolClassResponse addStudent(Long classId, Long studentId);
    SchoolClassResponse removeStudent(Long classId, Long studentId);
}
