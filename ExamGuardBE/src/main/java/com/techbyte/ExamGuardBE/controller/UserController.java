package com.techbyte.ExamGuardBE.controller;

import com.techbyte.ExamGuardBE.common.ApiResponse;
import com.techbyte.ExamGuardBE.controller.request.*;
import com.techbyte.ExamGuardBE.controller.response.*;
import com.techbyte.ExamGuardBE.enums.RoleName;
import com.techbyte.ExamGuardBE.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
@Slf4j(topic = "USER-CONTROLLER")
public class UserController {
    private final UserService userService;

    @GetMapping
    public ApiResponse<UserPageResponse> getList(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) RoleName role, @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) String sortBy, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Get user list page={} size={}", page, size);
        return ApiResponse.<UserPageResponse>builder().status(200).message("Danh sách người dùng")
                .data(userService.findAll(keyword, role, enabled, sortBy, page, size)).build();
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getDetail(@PathVariable @Positive Long id) {
        return ApiResponse.<UserResponse>builder().status(200).message("Chi tiết người dùng")
                .data(userService.findById(id)).build();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserCreationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<UserResponse>builder()
                .status(201).message("Tạo người dùng thành công").data(userService.save(request)).build());
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable @Positive Long id,
                                             @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.<UserResponse>builder().status(200).message("Cập nhật người dùng thành công")
                .data(userService.update(id, request)).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable @Positive Long id) {
        userService.delete(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder().status(200).message("Xóa người dùng thành công").build());
    }
}
