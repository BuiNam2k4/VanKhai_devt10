package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.controller.request.*;
import com.techbyte.ExamGuardBE.controller.response.*;

public interface AuthenticationService {
    AuthenticationResponse register(RegisterRequest request);
    AuthenticationResponse authenticate(AuthenticationRequest request);
    IntrospectResponse introspect(IntrospectRequest request);
    UserResponse currentUser(Long userId);
}
