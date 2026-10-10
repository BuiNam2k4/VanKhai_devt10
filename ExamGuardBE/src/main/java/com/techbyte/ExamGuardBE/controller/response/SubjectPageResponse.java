package com.techbyte.ExamGuardBE.controller.response;

import com.techbyte.ExamGuardBE.common.PageResponseAbstract;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SubjectPageResponse extends PageResponseAbstract {
    private List<SubjectResponse> subjects;
}
