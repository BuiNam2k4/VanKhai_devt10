package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.request.StartExamSessionRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.service.ExamSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@Validated
public class ExamSessionController {
    private final ExamSessionService service;
    @PostMapping("/exams/{examId}/sessions")
    public ResponseEntity<ApiResponse<ExamSessionResponse>> start(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long examId,
            @Valid @RequestBody StartExamSessionRequest body, HttpServletRequest request) {
        var result = service.start(actor, examId, body, request.getRemoteAddr(), request.getHeader("User-Agent"));
        int status = result.created() ? 201 : 200;
        return ResponseEntity.status(status).body(ApiResponse.<ExamSessionResponse>builder().status(status)
                .message(result.created() ? "Bắt đầu phiên thi" : "Phiên thi hiện tại").data(result.session()).build());
    }
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<ExamSessionResponse> restore(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long sessionId,
            @RequestParam @Size(min = 8, max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String deviceId) {
        return ApiResponse.<ExamSessionResponse>builder().status(200).message("Đề và thời hạn đã lưu của phiên thi")
                .data(service.restore(actor, sessionId, deviceId)).build();
    }
}
