package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.service.SchoolClassService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
public class UserClassController {
    private final SchoolClassService classService;

    @GetMapping("/api/admin/users/{id}/classes")
    public ApiResponse<SchoolClassPageResponse> forUser(@PathVariable @Positive Long id,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<SchoolClassPageResponse>builder().status(200).message("Lớp học của tài khoản")
                .data(classService.findForUser(id, true, keyword, sortBy, page, size)).build();
    }

    @GetMapping("/api/student/classes")
    public ApiResponse<SchoolClassPageResponse> myClasses(@AuthenticationPrincipal UserResponse actor,
            @RequestParam(required = false) String keyword, @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<SchoolClassPageResponse>builder().status(200).message("Lớp học của tôi")
                .data(classService.findForUser(actor.getId(), false, keyword, sortBy, page, size)).build();
    }
}
