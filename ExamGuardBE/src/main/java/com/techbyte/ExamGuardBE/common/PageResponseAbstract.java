package com.techbyte.ExamGuardBE.common;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class PageResponseAbstract {
    private long pageNumber;
    private long pageSize;
    private long totalPages;
    private long totalElements;
}
