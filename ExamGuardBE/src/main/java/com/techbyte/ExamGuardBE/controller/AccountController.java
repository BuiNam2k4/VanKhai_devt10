package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.request.*;
import com.techbyte.ExamGuardBE.controller.response.UserResponse;
import com.techbyte.ExamGuardBE.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService service;
    @PutMapping("/me")
    public ApiResponse<UserResponse> update(@AuthenticationPrincipal UserResponse actor, @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.<UserResponse>builder().status(200).message("Đã cập nhật thông tin tài khoản")
                .data(service.updateProfile(actor, request)).build();
    }
    @PutMapping("/password")
    public ApiResponse<Void> password(@AuthenticationPrincipal UserResponse actor, @Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(actor, request);
        return ApiResponse.<Void>builder().status(200).message("Đổi mật khẩu thành công. Vui lòng đăng nhập lại.").build();
    }
}
