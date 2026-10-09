# Task 4 Xác thực và phân quyền

## Chạy ứng dụng

MySQL phải có database `exam_guard` và truy cập được tại `localhost:3306` theo `ExamGuardBE/src/main/resources/application.yaml`. Container Docker hiện dùng cổng ánh xạ `3306:3306`.

Trong terminal backend, tạo khóa ngẫu nhiên cho phiên chạy local rồi khởi động:

```powershell
cd ExamGuardBE
$env:JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
.\mvnw.cmd spring-boot:run
```

Khóa phải có ít nhất 32 byte UTF-8. Không có khóa mặc định trong mã nguồn. Khi triển khai, cấu hình một khóa ngẫu nhiên ổn định bằng biến môi trường hoặc secret manager; việc thay khóa làm các JWT đang lưu mất hiệu lực. Không commit khóa. Token có thời hạn mặc định 30 phút, cấu hình qua `APP_SECURITY_ACCESS_TOKEN_TTL` (tối đa 1 ngày).

Trong terminal frontend:

```powershell
cd ExamGuardFE
npm ci
npm run dev
```

Mở `http://localhost:5173`. Vite chuyển `/api` tới `http://localhost:8080`. Cổng 5173 được cố định để khớp CORS; nếu cổng đang được sử dụng, dừng tiến trình dev cũ hoặc cấu hình lại cả Vite và `APP_SECURITY_ALLOWED_ORIGINS`. Bản production cần reverse proxy `/api` về backend và HTTPS.

Nếu đã nạp dữ liệu mẫu ở task 3, có thể dùng `demo_admin01`, `demo_teacher01`, `demo_student01` với mật khẩu `Demo@123`. Nếu chưa có, profile `demo` nạp dữ liệu mẫu bằng lệnh đã mô tả trong README; chỉ dùng các tài khoản này ở môi trường demo. Đăng ký từ giao diện luôn tạo tài khoản STUDENT. Việc cấp vai trò ADMIN/TEACHER thuộc quy trình quản trị, không nhận vai trò từ payload đăng ký.

## API

| Phương thức | Endpoint | Quyền | Kết quả |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Công khai | 201 và JWT cùng thông tin tài khoản STUDENT |
| POST | `/api/auth/login` | Công khai | 200 và JWT cùng thông tin tài khoản |
| GET | `/api/auth/me` | Đã đăng nhập | Thông tin người dùng hiện tại |
| GET | `/api/admin/profile` | ADMIN | Kiểm tra quyền vào không gian quản trị |
| GET | `/api/teacher/profile` | ADMIN hoặc TEACHER | Kiểm tra quyền vào không gian giảng viên |
| GET | `/api/student/profile` | STUDENT | Kiểm tra quyền vào không gian sinh viên |

Payload đăng ký:

```json
{
  "username": "student_new",
  "email": "student_new@example.test",
  "password": "Example@123",
  "fullName": "Nguyễn Văn An"
}
```

Payload đăng nhập:

```json
{
  "username": "student_new",
  "password": "Example@123"
}
```

Phản hồi đăng nhập/đăng ký có `accessToken`, `tokenType: "Bearer"`, `expiresAt` (UTC) và `user` gồm `id`, `username`, `email`, `fullName`, `roles`. Không trả password/hash hoặc toàn bộ entity JPA. Gửi token trong header `Authorization: Bearer <accessToken>`.

Mã lỗi: `400` khi dữ liệu không hợp lệ, `401` khi xác thực không hợp lệ hoặc tài khoản bị khóa, `403` khi thiếu quyền, `409` khi trùng username/email. JSON lỗi có `status`, `message`; lỗi validation có thêm `errors` theo trường. Không trả mật khẩu, chi tiết SQL hoặc stack trace.

Username đăng ký gồm 3–50 chữ cái ASCII, chữ số hoặc `_`; username/email được chuẩn hóa chữ thường, kiểm tra trùng không phân biệt hoa thường và được bảo vệ bằng UNIQUE ở database. Mật khẩu tối thiểu 8 ký tự, tối đa 72 byte UTF-8 để tránh giới hạn đầu vào BCrypt; mật khẩu không bị trim. Họ tên tối đa 150 ký tự, email tối đa 254 ký tự.

## Cách phân quyền

- Spring Security Resource Server xác minh chữ ký HS256, issuer `examguard`, thời hạn và các claim bắt buộc. `sub` là ID người dùng, không phải username có thể thay đổi.
- BCrypt dùng cost 12 cho mật khẩu đăng ký mới; vẫn xác minh được hash dữ liệu demo với cost khác.
- Mỗi request có JWT hợp lệ sẽ tải lại trạng thái `enabled` và các vai trò từ database. Khóa tài khoản, xóa tài khoản hoặc thay đổi vai trò có hiệu lực với token đã cấp; không tin vai trò cũ trong token để quyết định quyền.
- Quy tắc namespace `/api/admin/**`, `/api/teacher/**`, `/api/student/**` áp dụng cho API nghiệp vụ bổ sung sau này. API chưa khai báo bị từ chối theo mặc định. Đã bật `@EnableMethodSecurity` để task sau thêm kiểm tra quyền ở service.
- ADMIN có quyền vào không gian TEACHER; không tự động được quyền làm bài dưới vai trò STUDENT. Tài khoản có nhiều vai trò được hợp quyền.
- Task 4 thiết lập quyền theo vai trò. Các task nghiệp vụ sau cần kiểm tra quyền trên từng đối tượng, ví dụ giảng viên sở hữu bài thi và sinh viên sở hữu phiên thi.
- Xác thực stateless bằng header, không dùng cookie đăng nhập, form login hoặc HTTP Basic. CORS chỉ cho phép các origin cấu hình. CSRF tắt vì API không dùng credential được trình duyệt tự gửi.
- Giao diện giữ token trong `sessionStorage`, gọi `/me` khi tải lại và xử lý hết hạn/401. Đăng xuất xóa token phía trình duyệt; token đã sao chép vẫn còn hiệu lực tới khi hết hạn. Refresh token và danh sách thu hồi token chưa thuộc task 4.
- Các trang vai trò hiện hiển thị thông tin tài khoản và kiểm tra quyền qua API; CRUD người dùng, câu hỏi, bài thi thuộc các task tiếp theo.

## Kiểm thử

Bộ test mặc định dùng H2, tự cấu hình khóa kiểm thử riêng:

```powershell
cd ExamGuardBE
.\mvnw.cmd test
```

Chạy thêm trên MySQL đang cấu hình; các dữ liệu fixture trong từng test được rollback. SQL khởi tạo của ứng dụng vẫn tạo bảng/role còn thiếu theo cấu hình hiện có:

```powershell
$env:EXAMGUARD_MYSQL_TEST = 'true'
$env:JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
.\mvnw.cmd test '-Dtest=MySqlAuthIntegrationTests,MySqlSchemaTests' '-Dspring.jpa.show-sql=false'
Remove-Item Env:\EXAMGUARD_MYSQL_TEST
```

`AuthIntegrationTests` chạy cùng 14 ca trên H2 và MySQL: đăng ký chỉ có STUDENT, BCrypt, trùng username/email, validation và giới hạn byte, JSON lỗi, đăng nhập/ma trận ba vai trò, tài khoản sai hoặc bị khóa, thay đổi vai trò sau khi cấp token, token thiếu/hỏng/bị sửa/hết hạn/sai issuer/thiếu expiry/sai subject, và CORS.

Frontend:

```powershell
cd ExamGuardFE
npm run build
npm run lint
```

Tham khảo API JWT chính thức: [Spring Security Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
