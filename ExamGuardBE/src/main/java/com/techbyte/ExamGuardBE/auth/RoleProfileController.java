package com.techbyte.ExamGuardBE.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.techbyte.ExamGuardBE.auth.AuthDtos.*;

/** Role-specific entry points; business features are implemented by subsequent tasks. */
@RestController
public class RoleProfileController {
    @GetMapping({"/api/admin/profile", "/api/teacher/profile", "/api/student/profile"})
    public UserResponse profile(@AuthenticationPrincipal UserResponse user) {
        return user;
    }
}
