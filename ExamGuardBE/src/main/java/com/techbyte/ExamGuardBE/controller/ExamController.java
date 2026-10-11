package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.request.ExamRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.enums.ExamStatus;
import com.techbyte.ExamGuardBE.service.ExamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/teacher/exams", "/api/admin/exams"})
@RequiredArgsConstructor
@Validated
public class ExamController {
    private final ExamService examService;

    @GetMapping
    public ApiResponse<ExamPageResponse> getList(@AuthenticationPrincipal UserResponse actor,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) @Positive Long subjectId,
            @RequestParam(required = false) ExamStatus status, @RequestParam(required = false) @Positive Long teacherId,
            @RequestParam(required = false) String sortBy, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<ExamPageResponse>builder().status(200).message("Danh sách bài thi")
                .data(examService.findAll(actor, keyword, subjectId, status, teacherId, sortBy, page, size)).build();
    }

    @GetMapping("/{id}")
    public ApiResponse<ExamResponse> detail(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long id) {
        return success("Chi tiết bài thi", examService.findById(actor, id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ExamResponse>> create(@AuthenticationPrincipal UserResponse actor, @Valid @RequestBody ExamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<ExamResponse>builder().status(201)
                .message("Tạo bài thi nháp thành công").data(examService.save(actor, request)).build());
    }

    @PutMapping("/{id}")
    public ApiResponse<ExamResponse> update(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long id,
                                           @Valid @RequestBody ExamRequest request) {
        return success("Cập nhật bài thi thành công", examService.update(actor, id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ExamResponse> publish(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long id) {
        return success("Công bố bài thi thành công", examService.publish(actor, id));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<ExamResponse> close(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long id) {
        return success("Đóng bài thi thành công", examService.close(actor, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long id) {
        examService.delete(actor, id);
        return ApiResponse.<Void>builder().status(200).message("Xóa bài thi nháp thành công").build();
    }

    private static ApiResponse<ExamResponse> success(String message, ExamResponse data) {
        return ApiResponse.<ExamResponse>builder().status(200).message(message).data(data).build();
    }
}
