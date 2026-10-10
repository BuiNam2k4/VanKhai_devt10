package com.techbyte.ExamGuardBE.controller.response;

import com.techbyte.ExamGuardBE.enums.RoleName;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private boolean enabled;
    private Set<RoleName> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
