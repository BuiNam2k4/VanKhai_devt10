package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.response.SubjectPageResponse;
import com.techbyte.ExamGuardBE.service.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teacher/subjects")
@RequiredArgsConstructor
public class TeacherSubjectController {
    private final SubjectService subjectService;

    @GetMapping
    public ApiResponse<SubjectPageResponse> getList(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortBy, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.<SubjectPageResponse>builder().status(200).message("Danh mục môn học")
                .data(subjectService.findAll(keyword, sortBy, page, size)).build();
    }
}
