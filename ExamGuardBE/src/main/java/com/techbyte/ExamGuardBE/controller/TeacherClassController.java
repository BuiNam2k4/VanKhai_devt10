package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.enums.RoleName;
import com.techbyte.ExamGuardBE.service.SchoolClassService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teacher/classes")
@RequiredArgsConstructor
@Validated
public class TeacherClassController {
    private final SchoolClassService classService;

    @GetMapping
    public ApiResponse<SchoolClassPageResponse> getList(@AuthenticationPrincipal UserResponse actor,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<SchoolClassPageResponse>builder().status(200).message("Danh sách lớp được quản lý")
                .data(classService.findAll(keyword, actor.getRoles().contains(RoleName.ADMIN) ? null : actor.getId(), sortBy, page, size)).build();
    }

    @GetMapping("/{id}/students")
    public ApiResponse<UserPageResponse> students(@AuthenticationPrincipal UserResponse actor, @PathVariable @Positive Long id,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<UserPageResponse>builder().status(200).message("Sinh viên trong lớp phụ trách")
                .data(classService.findTeacherStudents(actor, id, keyword, sortBy, page, size)).build();
    }
}
