package com.techbyte.ExamGuardBE.service.impl;

import com.techbyte.ExamGuardBE.controller.request.*;
import com.techbyte.ExamGuardBE.controller.response.UserResponse;
import com.techbyte.ExamGuardBE.exception.*;
import com.techbyte.ExamGuardBE.mapper.ManagementMapper;
import com.techbyte.ExamGuardBE.repository.UserRepository;
import com.techbyte.ExamGuardBE.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    @Override
    @Transactional
    public UserResponse updateProfile(UserResponse actor, UpdateProfileRequest request) {
        var user = users.findForUpdate(actor.getId()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!user.isEnabled()) throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCaseAndIdNot(username, user.getId()) || users.existsByEmailIgnoreCaseAndIdNot(email, user.getId())) {
            throw new AppException(ErrorCode.USER_EXISTS);
        }
        user.setFullName(request.fullName().trim()); user.setUsername(username); user.setEmail(email);
        return ManagementMapper.toUserResponse(users.saveAndFlush(user));
    }

    @Override
    @Transactional
    public void changePassword(UserResponse actor, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) throw new AppException(ErrorCode.PASSWORD_CONFIRMATION_MISMATCH);
        if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72 || request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AppException(ErrorCode.PASSWORD_TOO_LONG);
        }
        var user = users.findForUpdate(actor.getId()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (!user.isEnabled()) throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) throw new AppException(ErrorCode.INCORRECT_CURRENT_PASSWORD);
        user.setPasswordHash(passwords.encode(request.newPassword()));
        users.saveAndFlush(user);
    }
}
