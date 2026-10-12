package com.techbyte.ExamGuardBE.service;

import com.techbyte.ExamGuardBE.entity.SessionQuestion;
import java.util.List;

public interface ExamPaperSnapshotService {
    // Internal operation: call from the authorized session-creation transaction (Task 10).
    List<SessionQuestion> createOrGet(Long sessionId);
}
