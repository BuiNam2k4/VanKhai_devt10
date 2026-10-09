package com.techbyte.ExamGuardBE.common;

import com.techbyte.ExamGuardBE.exception.AppException;
import com.techbyte.ExamGuardBE.exception.ErrorCode;
import org.springframework.data.domain.*;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaginationUtils {
    private static final Pattern SORT_PATTERN = Pattern.compile("(\\w+?)(:)(asc|desc)", Pattern.CASE_INSENSITIVE);

    private PaginationUtils() {}

    public static Pageable pageable(int page, int size, String sortBy, Set<String> allowedFields) {
        if (page < 0 || size < 1 || size > 100) {
            throw new AppException(ErrorCode.INVALID_PAGINATION);
        }

        Sort.Order order = Sort.Order.asc("id");
        if (StringUtils.hasText(sortBy)) {
            Matcher matcher = SORT_PATTERN.matcher(sortBy.trim());
            if (!matcher.matches() || !allowedFields.contains(matcher.group(1))) {
                throw new AppException(ErrorCode.INVALID_SORT);
            }
            Sort.Direction direction = matcher.group(3).equalsIgnoreCase("asc")
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
            order = new Sort.Order(direction, matcher.group(1));
        }

        int pageNo = page > 0 ? page - 1 : 0;
        return PageRequest.of(pageNo, size, Sort.by(order).and(Sort.by("id")));
    }

    public static void fill(PageResponseAbstract response, int page, int size, Page<?> result) {
        response.setPageNumber(page);
        response.setPageSize(size);
        response.setTotalPages(result.getTotalPages());
        response.setTotalElements(result.getTotalElements());
    }
}
