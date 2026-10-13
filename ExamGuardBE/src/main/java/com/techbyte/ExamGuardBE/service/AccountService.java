package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.controller.request.*;
import com.techbyte.ExamGuardBE.controller.response.UserResponse;

public interface AccountService {
    UserResponse updateProfile(UserResponse actor, UpdateProfileRequest request);
    void changePassword(UserResponse actor, ChangePasswordRequest request);
}
