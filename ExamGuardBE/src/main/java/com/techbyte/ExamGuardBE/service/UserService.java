package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.controller.request.*;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.enums.RoleName;

public interface UserService {
    UserPageResponse findAll(String keyword, RoleName role, Boolean enabled, String sortBy, int page, int size);
    UserResponse findById(Long id);
    UserResponse save(UserCreationRequest request);
    UserResponse update(Long id, UserUpdateRequest request);
    void delete(Long id);
}
