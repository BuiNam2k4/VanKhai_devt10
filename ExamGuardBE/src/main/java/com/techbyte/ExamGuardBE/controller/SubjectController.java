package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.request.SubjectRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.service.SubjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/subjects")
@RequiredArgsConstructor
@Validated
public class SubjectController {
    private final SubjectService subjectService;

    @GetMapping
    public ApiResponse<SubjectPageResponse> getList(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<SubjectPageResponse>builder().status(200).message("Danh sách môn học")
                .data(subjectService.findAll(keyword, sortBy, page, size)).build();
    }

    @GetMapping("/{id}")
    public ApiResponse<SubjectResponse> getDetail(@PathVariable @Positive Long id) {
        return ApiResponse.<SubjectResponse>builder().status(200).message("Chi tiết môn học")
                .data(subjectService.findById(id)).build();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubjectResponse>> create(@Valid @RequestBody SubjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<SubjectResponse>builder()
                .status(201).message("Tạo môn học thành công").data(subjectService.save(request)).build());
    }

    @PutMapping("/{id}")
    public ApiResponse<SubjectResponse> update(@PathVariable @Positive Long id,
                                                @Valid @RequestBody SubjectRequest request) {
        return ApiResponse.<SubjectResponse>builder().status(200).message("Cập nhật môn học thành công")
                .data(subjectService.update(id, request)).build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        subjectService.delete(id);
        return ApiResponse.<Void>builder().status(200).message("Xóa môn học thành công").build();
    }
}
