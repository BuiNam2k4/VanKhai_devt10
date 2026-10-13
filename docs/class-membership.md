# Tra cứu lớp theo tài khoản

Admin tra từ **Người dùng → tìm tên đăng nhập → Xem lớp**. Danh sách bao gồm lớp tham gia và lớp phụ trách, không trùng lặp nếu tài khoản có cả hai quan hệ. Màn hình hiển thị mã lớp, tên lớp, giảng viên và số sinh viên. **Xem sinh viên** mở danh sách chỉ đọc; quản lý thành viên vẫn dùng màn hình Lớp học hiện có.

Teacher có mục **Lớp phụ trách**, tìm lớp theo mã/tên/giảng viên rồi chọn **Xem sinh viên**. Có tìm sinh viên theo username, họ tên hoặc email, phân trang và làm mới. Teacher chỉ đọc danh sách lớp mình phụ trách; không thêm/xóa sinh viên hoặc đọc lớp của giảng viên khác.

Student có mục **Lớp của tôi** tại `#/classes`; tải lại trang vẫn ở mục này. Danh sách lấy trực tiếp từ quan hệ thành viên hiện tại, độc lập với lịch thi/trạng thái công bố đề. Student không được đọc danh sách sinh viên khác. Giao diện góc trên hiển thị cả tên đăng nhập để dễ đối chiếu tài khoản đang dùng.

## API

Tất cả endpoint cần Bearer token và trả ApiResponse như API hiện có.

| Endpoint GET | Quyền | Kết quả |
| --- | --- | --- |
| `/api/admin/users/{id}/classes` | ADMIN | Lớp tham gia/phụ trách của tài khoản; tài khoản không tồn tại trả 404 |
| `/api/student/classes` | STUDENT | Chỉ lớp đang tham gia của người đăng nhập; không nhận userId để đổi đối tượng |
| `/api/teacher/classes` | TEACHER/ADMIN | Danh mục lớp đã có; teacher chỉ có lớp phụ trách |
| `/api/teacher/classes/{id}/students` | TEACHER sở hữu lớp hoặc ADMIN | Danh sách sinh viên của lớp; lớp khác teacher trả 403 |

Các API lớp hỗ trợ `keyword`, `page`, `size`, `sortBy`; whitelist sort `id`, `code`, `name`, `createdAt`. API sinh viên dùng whitelist `id`, `username`, `email`, `fullName`, `createdAt`. Giữ hợp đồng phân trang cũ: page 0 và 1 trỏ trang đầu, size 1–100, response có pageNumber/pageSize/totalPages/totalElements và classes hoặc users. Không trả passwordHash hoặc entity/collection thành viên trong DTO lớp.

## Kiểm tra

Kiểm thử tích hợp trong `AbstractExamManagementIntegrationTests` chạy trên cả H2 và MySQL: tài khoản nhiều lớp, phân trang/tìm kiếm, lớp phụ trách, không trùng quan hệ, lớp rỗng, cập nhật thành viên, giả mạo userId, quyền admin/teacher/student, teacher không được xem lớp khác, validation và không lộ mật khẩu. Fixture có tiền tố riêng và transaction rollback.

```powershell
cd ExamGuardBE
$env:EXAMGUARD_MYSQL_TEST = 'true'
.\mvnw.cmd test '-Dtest=H2ExamManagementIntegrationTests,MySqlExamManagementIntegrationTests' '-Dspring.jpa.show-sql=false'
```

Kiểm tra giao diện bằng các tài khoản demo, mật khẩu `Demo@123`:

1. Admin tìm `demo_student02`, chọn Xem lớp: thấy DEMO-CLS-02 và DEMO-CLS-22. Tìm lớp Docker, xem sinh viên: thấy username demo_student02.
2. Teacher `demo_teacher01` vào Lớp phụ trách: thấy DEMO-CLS-22. Xem sinh viên và tìm demo_student02; tìm nick không có trong lớp cho trạng thái rỗng.
3. Student `demo_student02` vào Lớp của tôi: thấy hai lớp, không có nút xem sinh viên khác. Tải lại trang vẫn ở mục Lớp của tôi; chuyển về Bài thi được giao vẫn xem được đề Docker đã công bố.
4. Giao diện `npm run lint` và `npm run build`.
