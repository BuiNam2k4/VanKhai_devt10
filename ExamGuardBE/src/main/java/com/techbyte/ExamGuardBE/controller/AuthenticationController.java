package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.request.*;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.service.AuthenticationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {
    private final AuthenticationService authenticationService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<AuthenticationResponse>builder()
                .status(201).message("Đăng ký thành công").data(authenticationService.register(request)).build());
    }

    @PostMapping({"/token", "/login"})
    public ApiResponse<AuthenticationResponse> authenticate(@Valid @RequestBody AuthenticationRequest request) {
        return ApiResponse.<AuthenticationResponse>builder().status(200).message("Đăng nhập thành công")
                .data(authenticationService.authenticate(request)).build();
    }

    @PostMapping("/introspect")
    public ApiResponse<IntrospectResponse> introspect(@Valid @RequestBody IntrospectRequest request) {
        return ApiResponse.<IntrospectResponse>builder().status(200).message("Kiểm tra token thành công")
                .data(authenticationService.introspect(request)).build();
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal UserResponse user) {
        return ApiResponse.<UserResponse>builder().status(200).message("Thông tin tài khoản").data(user).build();
    }
}
