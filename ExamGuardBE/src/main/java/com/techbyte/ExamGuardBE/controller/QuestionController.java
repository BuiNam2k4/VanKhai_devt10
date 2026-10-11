package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.request.QuestionRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.enums.*;
import com.techbyte.ExamGuardBE.service.QuestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/teacher/questions", "/api/admin/questions"})
@RequiredArgsConstructor
@Validated
public class QuestionController {
    private final QuestionService questionService;

    @GetMapping
    public ApiResponse<QuestionPageResponse> getList(@AuthenticationPrincipal UserResponse actor,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) @Positive Long subjectId,
            @RequestParam(required = false) Difficulty difficulty, @RequestParam(required = false) QuestionType type,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<QuestionPageResponse>builder().status(200).message("Danh sách câu hỏi")
                .data(questionService.findAll(actor, keyword, subjectId, difficulty, type, active, sortBy, page, size)).build();
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionResponse> getDetail(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long id) {
        return ApiResponse.<QuestionResponse>builder().status(200).message("Chi tiết câu hỏi")
                .data(questionService.findById(actor, id)).build();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<QuestionResponse>> create(@AuthenticationPrincipal UserResponse actor,
                                                                @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<QuestionResponse>builder()
                .status(201).message("Tạo câu hỏi thành công").data(questionService.save(actor, request)).build());
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionResponse> update(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long id,
                                                @Valid @RequestBody QuestionRequest request) {
        return ApiResponse.<QuestionResponse>builder().status(200).message("Cập nhật câu hỏi thành công")
                .data(questionService.update(actor, id, request)).build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long id) {
        questionService.delete(actor, id);
        return ApiResponse.<Void>builder().status(200).message("Xóa câu hỏi thành công").build();
    }
}
