# VanKhai_devt10
ExamGuard – Hệ thống quản lý bài kiểm tra trực tuyến có cơ chế giám sát và phát  hiện hành vi bất thường

## Backend và database

Backend dùng Java 25, Spring Boot, Spring Data JPA, Lombok và MySQL 8.0.16 trở lên. Giữ cấu hình kết nối trong `ExamGuardBE/src/main/resources/application.yaml`, bật MySQL và tạo database `exam_guard` trước khi chạy:

```powershell
cd ExamGuardBE
$env:JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
.\mvnw.cmd spring-boot:run
```

Spring Boot tạo các bảng còn thiếu từ `db/schema.sql`, thêm ba role từ `db/data.sql` rồi Hibernate kiểm tra mapping entity. Thiết kế 16 bảng, sơ đồ quan hệ, quy tắc dữ liệu và lệnh kiểm thử nằm trong [tài liệu database](docs/database-design.md).

Để thêm bộ dữ liệu mẫu (24 bản ghi mỗi bảng nghiệp vụ và liên kết, 3 role), chạy:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'
```

Script `db/sample-data.sql` có thể chạy lại tuần tự mà không nhân đôi dữ liệu. Tài khoản mẫu gồm `demo_admin01`, `demo_teacher01..03`, `demo_student01..20`, dùng mật khẩu `Demo@123` được lưu bằng BCrypt.

## Task 4 Xác thực và phân quyền

Đã có API đăng ký, đăng nhập, thông tin tài khoản, BCrypt và JWT với ba vai trò ADMIN/TEACHER/STUDENT. Khởi chạy frontend bằng `npm run dev` trong `ExamGuardFE`, mở `http://localhost:5173`. Đăng ký công khai tạo tài khoản STUDENT.

Backend yêu cầu biến môi trường `JWT_SECRET` tối thiểu 32 byte; lệnh phía trên sinh khóa ngẫu nhiên cho local. Xem [hướng dẫn xác thực](docs/auth-security.md) để biết API, ma trận quyền, cấu hình triển khai và lệnh kiểm thử H2/MySQL.
