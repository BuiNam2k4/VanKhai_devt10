package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.enums.*;
import com.techbyte.ExamGuardBE.service.StudentExamService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
@Validated
@RequiredArgsConstructor
public class StudentExamController {
    private final StudentExamService service;
    @GetMapping("/exams")
    public ApiResponse<StudentExamPageResponse> list(@AuthenticationPrincipal UserResponse actor,
        @RequestParam(required = false) String keyword, @RequestParam(required = false) @Positive Long subjectId,
        @RequestParam(required = false) ExamStatus status, @RequestParam(required = false) ExamWindow window,
        @RequestParam(required = false) String sortBy, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<StudentExamPageResponse>builder().status(200).message("Bài thi được giao")
            .data(service.findAll(actor, keyword, subjectId, status, window, sortBy, page, size)).build();
    }
    @GetMapping("/exams/{id}")
    public ApiResponse<StudentExamResponse> detail(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long id) {
        return ApiResponse.<StudentExamResponse>builder().status(200).message("Thông tin và điều kiện tham gia bài thi").data(service.findById(actor, id)).build();
    }
    @GetMapping("/subjects")
    public ApiResponse<SubjectPageResponse> subjects(@AuthenticationPrincipal UserResponse actor,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<SubjectPageResponse>builder().status(200).message("Môn có bài thi được giao").data(service.subjects(actor, page, size)).build();
    }
}
