package com.techbyte.ExamGuardBE.controller.response;
import com.techbyte.ExamGuardBE.common.PageResponseAbstract;

import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class StudentExamPageResponse extends PageResponseAbstract {
    private OffsetDateTime serverTime;
    private List<StudentExamResponse> exams;
}
