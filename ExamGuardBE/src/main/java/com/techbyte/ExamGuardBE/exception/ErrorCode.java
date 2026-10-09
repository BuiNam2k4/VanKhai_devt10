package com.techbyte.ExamGuardBE.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    UNCATEGORIZED(HttpStatus.INTERNAL_SERVER_ERROR, "Có lỗi xảy ra trên máy chủ"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Dữ liệu yêu cầu không hợp lệ"),
    INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "page phải từ 0 và size phải từ 1 đến 100"),
    INVALID_SORT(HttpStatus.BAD_REQUEST, "sortBy không hợp lệ; dùng định dạng field:asc hoặc field:desc"),
    USER_EXISTS(HttpStatus.CONFLICT, "Tên đăng nhập hoặc email đã tồn tại"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    SUBJECT_EXISTS(HttpStatus.CONFLICT, "Mã môn học đã tồn tại"),
    SUBJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy môn học"),
    CLASS_EXISTS(HttpStatus.CONFLICT, "Mã lớp đã tồn tại"),
    CLASS_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lớp học"),
    ROLE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "Danh mục vai trò chưa được khởi tạo"),
    INVALID_TEACHER(HttpStatus.BAD_REQUEST, "Người phụ trách phải có vai trò TEACHER"),
    INVALID_STUDENT(HttpStatus.BAD_REQUEST, "Thành viên lớp phải có vai trò STUDENT"),
    STUDENT_ALREADY_IN_CLASS(HttpStatus.CONFLICT, "Sinh viên đã có trong lớp"),
    STUDENT_NOT_IN_CLASS(HttpStatus.NOT_FOUND, "Sinh viên không có trong lớp"),
    RESOURCE_IN_USE(HttpStatus.CONFLICT, "Không thể xóa dữ liệu đang được sử dụng"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Tên đăng nhập hoặc mật khẩu không đúng"),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "Tài khoản đã bị khóa"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập chức năng này");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
