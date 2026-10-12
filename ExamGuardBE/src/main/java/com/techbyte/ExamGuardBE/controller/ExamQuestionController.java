package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.request.ExamQuestionsRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.service.ExamQuestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/teacher/exams/{examId}/questions", "/api/admin/exams/{examId}/questions"})
@RequiredArgsConstructor
@Validated
public class ExamQuestionController {
    private final ExamQuestionService service;
    @GetMapping
    public ApiResponse<ExamQuestionsResponse> list(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long examId) {
        return ApiResponse.<ExamQuestionsResponse>builder().status(200).message("Câu hỏi trong đề").data(service.findAll(actor, examId)).build();
    }
    @PutMapping
    public ApiResponse<ExamQuestionsResponse> replace(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long examId,
                                                      @Valid @RequestBody ExamQuestionsRequest request) {
        return ApiResponse.<ExamQuestionsResponse>builder().status(200).message("Đã lưu câu hỏi trong đề").data(service.replace(actor, examId, request)).build();
    }
    @PostMapping("/preview")
    public ApiResponse<ExamPaperResponse> preview(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long examId) {
        return ApiResponse.<ExamPaperResponse>builder().status(200).message("Bản xem thử đề thi").data(service.preview(actor, examId)).build();
    }
}
