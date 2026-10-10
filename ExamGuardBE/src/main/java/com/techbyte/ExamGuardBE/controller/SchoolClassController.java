package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.request.SchoolClassRequest;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.service.SchoolClassService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/classes")
@RequiredArgsConstructor
@Validated
public class SchoolClassController {
    private final SchoolClassService classService;

    @GetMapping
    public ApiResponse<SchoolClassPageResponse> getList(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long teacherId, @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<SchoolClassPageResponse>builder().status(200).message("Danh sách lớp học")
                .data(classService.findAll(keyword, teacherId, sortBy, page, size)).build();
    }

    @GetMapping("/{id}")
    public ApiResponse<SchoolClassResponse> getDetail(@PathVariable @Positive Long id) {
        return ApiResponse.<SchoolClassResponse>builder().status(200).message("Chi tiết lớp học")
                .data(classService.findById(id)).build();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SchoolClassResponse>> create(@Valid @RequestBody SchoolClassRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<SchoolClassResponse>builder()
                .status(201).message("Tạo lớp học thành công").data(classService.save(request)).build());
    }

    @PutMapping("/{id}")
    public ApiResponse<SchoolClassResponse> update(@PathVariable @Positive Long id,
                                                    @Valid @RequestBody SchoolClassRequest request) {
        return ApiResponse.<SchoolClassResponse>builder().status(200).message("Cập nhật lớp học thành công")
                .data(classService.update(id, request)).build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        classService.delete(id);
        return ApiResponse.<Void>builder().status(200).message("Xóa lớp học thành công").build();
    }

    @GetMapping("/{id}/students")
    public ApiResponse<UserPageResponse> getStudents(@PathVariable @Positive Long id,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<UserPageResponse>builder().status(200).message("Danh sách sinh viên trong lớp")
                .data(classService.findStudents(id, keyword, sortBy, page, size)).build();
    }

    @PutMapping("/{classId}/students/{studentId}")
    public ApiResponse<SchoolClassResponse> addStudent(@PathVariable @Positive Long classId,
                                                        @PathVariable @Positive Long studentId) {
        return ApiResponse.<SchoolClassResponse>builder().status(200).message("Thêm sinh viên vào lớp thành công")
                .data(classService.addStudent(classId, studentId)).build();
    }

    @DeleteMapping("/{classId}/students/{studentId}")
    public ApiResponse<SchoolClassResponse> removeStudent(@PathVariable @Positive Long classId,
                                                           @PathVariable @Positive Long studentId) {
        return ApiResponse.<SchoolClassResponse>builder().status(200).message("Xóa sinh viên khỏi lớp thành công")
                .data(classService.removeStudent(classId, studentId)).build();
    }
}
