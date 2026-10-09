package com.techbyte.ExamGuardBE.controller.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IntrospectResponse {
    private boolean valid;
}
