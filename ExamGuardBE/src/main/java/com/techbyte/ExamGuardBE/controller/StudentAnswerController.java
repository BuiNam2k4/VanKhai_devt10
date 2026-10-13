package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.request.SaveStudentAnswersRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.service.StudentAnswerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student/sessions/{sessionId}/answers")
@RequiredArgsConstructor
@Validated
public class StudentAnswerController {
    private final StudentAnswerService service;
    @GetMapping
    public ApiResponse<StudentAnswersResponse> get(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long sessionId,
            @RequestParam @Size(min = 8, max = 100) @Pattern(regexp = "[A-Za-z0-9_-]+") String deviceId) {
        return ApiResponse.<StudentAnswersResponse>builder().status(200).message("Đáp án đã lưu")
                .data(service.findAll(actor, sessionId, deviceId)).build();
    }
    @PutMapping
    public ApiResponse<StudentAnswersResponse> save(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long sessionId,
            @Valid @RequestBody SaveStudentAnswersRequest body) {
        return ApiResponse.<StudentAnswersResponse>builder().status(200).message("Đã lưu đáp án")
                .data(service.save(actor, sessionId, body)).build();
    }
}
