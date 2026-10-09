package com.techbyte.ExamGuardBE.controller.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class AuthenticationResponse {
    private String token;
    private String tokenType;
    private boolean authenticated;
    private Instant expiresAt;
    private UserResponse user;
}
