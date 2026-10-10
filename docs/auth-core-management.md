# Xác thực và quản lý học vụ

Tài liệu này mô tả phần triển khai task 4 và task 5 của ExamGuard. Mã nguồn giữ mô hình Controller, Service, Repository, DTO và response wrapper thống nhất; cách phân trang được xây dựng theo phong cách của `backend-java-full-step`, còn luồng BCrypt, JWT và Resource Server tham khảo `identity-service` rồi điều chỉnh cho ba vai trò của ExamGuard.

## Chạy ứng dụng

MySQL chạy tại `localhost:3306`, database `exam_guard`, tài khoản `root/root` theo `application.yaml`.

```powershell
cd ExamGuardBE
$env:JWT_SIGNER_KEY = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64))
.\mvnw.cmd spring-boot:run
```

Nếu không đặt biến môi trường, dự án dùng khóa local trong cấu hình để thuận tiện chạy thử. Khi triển khai thật phải đặt `JWT_SIGNER_KEY` riêng, tối thiểu 64 byte, và không commit khóa production.

```powershell
cd ExamGuardFE
npm ci
npm run dev
```

Mở `http://localhost:5173`. Vite chuyển tiếp `/api` tới backend cổng 8080. Nếu đã nạp dữ liệu demo, dùng `demo_admin01`, `demo_teacher01` hoặc `demo_student01` với mật khẩu `Demo@123`.

## Response chung

Mọi API trả JSON có cấu trúc:

```json
{
  "status": 200,
  "message": "Danh sách người dùng",
  "data": {}
}
```

Lỗi validation trả danh sách lỗi theo tên trường trong `data`. Các lỗi nghiệp vụ dùng đúng HTTP status: 400, 401, 403, 404, 409 hoặc 500.

## Task 4

| Method | Endpoint | Quyền | Chức năng |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Công khai | Đăng ký tài khoản STUDENT và trả JWT |
| POST | `/api/auth/token` | Công khai | Đăng nhập và trả JWT |
| POST | `/api/auth/login` | Công khai | Alias của endpoint token |
| POST | `/api/auth/introspect` | Công khai | Kiểm tra chữ ký, thời hạn và trạng thái tài khoản |
| GET | `/api/auth/me` | Đã đăng nhập | Lấy thông tin tài khoản hiện tại |

Mật khẩu được băm bằng BCrypt cost 10. JWT dùng HS512, issuer `examguard`, mặc định hết hạn sau 3600 giây. Claim `sub` lưu ID người dùng; claim `roles` lưu các vai trò. Mỗi request được bảo vệ sẽ đọc lại người dùng và vai trò từ database nên khóa tài khoản hoặc thay vai trò có hiệu lực với token đã cấp.

Namespace `/api/admin/**` yêu cầu ADMIN, `/api/teacher/**` chấp nhận ADMIN hoặc TEACHER, `/api/student/**` yêu cầu STUDENT. API khác bị từ chối mặc định.

## Task 5

| Tài nguyên | Endpoint chính | Chức năng |
| --- | --- | --- |
| Người dùng | `/api/admin/users` | CRUD, tìm kiếm username/email/họ tên, lọc role/trạng thái |
| Môn học | `/api/admin/subjects` | CRUD, tìm kiếm mã/tên/mô tả |
| Lớp học | `/api/admin/classes` | CRUD, tìm kiếm mã/tên/giảng viên, lọc teacherId |
| Thành viên lớp | `/api/admin/classes/{id}/students` | Danh sách sinh viên có tìm kiếm và phân trang |
| Thêm thành viên | `PUT /api/admin/classes/{classId}/students/{studentId}` | Chỉ nhận tài khoản có role STUDENT |
| Xóa thành viên | `DELETE /api/admin/classes/{classId}/students/{studentId}` | Gỡ sinh viên khỏi lớp |

Khi tạo hoặc cập nhật lớp, `teacherId` phải thuộc người dùng có vai trò TEACHER. Xóa dữ liệu đang được đề thi, câu hỏi hoặc phiên thi tham chiếu trả 409; các khóa ngoại lịch sử không bị cascade.

## Phân trang

Hợp đồng phân trang dùng cùng cách của `backend-java-full-step`:

```text
GET /api/admin/users?keyword=an&sortBy=fullName:asc&page=1&size=20
```

- `keyword` không bắt buộc.
- `sortBy` có dạng `field:asc` hoặc `field:desc`; mặc định `id:asc`.
- `page` hiển thị theo kiểu 1-based. Giá trị 0 vẫn ánh xạ về trang đầu để tương thích code base tham khảo.
- `size` từ 1 đến 100.
- Service đổi `page > 0` thành `page - 1` trước khi tạo `PageRequest`.
- Response trả `pageNumber`, `pageSize`, `totalPages`, `totalElements` và danh sách theo tên tài nguyên (`users`, `subjects`, `classes`).
- Trường sắp xếp được whitelist cho từng tài nguyên; không cho truyền trực tiếp tên thuộc tính nhạy cảm như password hash.

## Kiểm thử

```powershell
cd ExamGuardBE
.\mvnw.cmd test
```

Chạy bộ hợp đồng trên MySQL Docker:

```powershell
$env:EXAMGUARD_MYSQL_TEST = 'true'
.\mvnw.cmd test '-Dtest=MySqlAuthManagementIntegrationTests,MySqlSchemaTests' '-Dspring.jpa.show-sql=false'
Remove-Item Env:\EXAMGUARD_MYSQL_TEST
```

Frontend:

```powershell
cd ExamGuardFE
npm run build
npm run lint
```

Nguồn tham khảo kiến trúc: [backend-java-full-step](https://github.com/khaipham25/backend-java-full-step) và [identity-service](https://github.com/khaipham25/identity-service).
