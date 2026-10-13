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
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy câu hỏi"),
    EXAM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bài thi"),
    INVALID_EXAM_SCHEDULE(HttpStatus.BAD_REQUEST, "Lịch thi phải có đủ thời gian mở và đóng; thời gian đóng phải sau thời gian mở"),
    EXAM_NOT_DRAFT(HttpStatus.CONFLICT, "Chỉ được sửa cấu hình hoặc xóa bài thi ở trạng thái nháp"),
    INVALID_EXAM_TRANSITION(HttpStatus.CONFLICT, "Trạng thái bài thi không cho phép thao tác này"),
    EXAM_NOT_READY(HttpStatus.BAD_REQUEST, "Chưa thể công bố: cần lịch còn hiệu lực, lớp được giao và câu hỏi hoạt động cùng môn với điểm và đáp án hợp lệ"),
    INVALID_EXAM_CLASS(HttpStatus.BAD_REQUEST, "Lớp được giao phải thuộc giảng viên phụ trách bài thi"),
    EXAM_SUBJECT_IN_USE(HttpStatus.CONFLICT, "Bài thi đã có câu hỏi; không thể đổi môn học"),
    INVALID_EXAM_QUESTIONS(HttpStatus.BAD_REQUEST, "Câu hỏi trong đề phải khác nhau, hoạt động, cùng môn và có điểm hợp lệ; tổng điểm tối đa 999999.99"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thi"),
    INVALID_SESSION_PAPER(HttpStatus.CONFLICT, "Chỉ tạo đề cho phiên đang thi của bài đã công bố"),
    EXAM_NOT_OPEN(HttpStatus.CONFLICT, "Bài thi chưa mở hoặc đã kết thúc thời gian tham gia"),
    EXAM_CLOSED(HttpStatus.CONFLICT, "Bài thi đã đóng, không thể bắt đầu lượt mới"),
    ATTEMPTS_EXHAUSTED(HttpStatus.CONFLICT, "Bạn đã sử dụng hết số lượt thi cho phép"),
    SESSION_DEVICE_CONFLICT(HttpStatus.CONFLICT, "Phiên thi thuộc thiết bị khác; hãy tiếp tục trên thiết bị đã bắt đầu lượt này"),
    SESSION_PAPER_MISSING(HttpStatus.CONFLICT, "Phiên thi thiếu ảnh chụp đề; hãy liên hệ giảng viên, không thể tạo lại đề hay gia hạn phiên"),
    SESSION_NOT_WRITABLE(HttpStatus.CONFLICT, "Phiên thi đã hết giờ hoặc đã kết thúc; không thể lưu thêm đáp án"),
    INVALID_STUDENT_ANSWERS(HttpStatus.BAD_REQUEST, "Đáp án phải thuộc câu hỏi của phiên này và đúng định dạng trắc nghiệm/tự luận; không được trùng câu hỏi"),
    ANSWER_VERSION_CONFLICT(HttpStatus.CONFLICT, "Đáp án đã được cập nhật từ yêu cầu khác; hãy tải lại đáp án đã lưu trước khi sửa tiếp"),
    INVALID_QUESTION_OPTIONS(HttpStatus.BAD_REQUEST, "Trắc nghiệm cần từ 2 đến 10 lựa chọn khác nhau và đúng một đáp án đúng; tự luận không có lựa chọn"),
    INVALID_OPTION_ID(HttpStatus.BAD_REQUEST, "ID lựa chọn bị trùng hoặc không thuộc câu hỏi này"),
    QUESTION_IN_EXAM(HttpStatus.CONFLICT, "Câu hỏi đã được gán vào đề thi; không thể xóa hoặc đổi môn học và loại câu hỏi"),
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
    INCORRECT_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),
    PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp"),
    PASSWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "Mật khẩu không được vượt quá 72 byte UTF-8; ký tự có dấu có thể chiếm nhiều byte"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập chức năng này");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
