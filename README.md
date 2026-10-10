# VanKhai_devt10
ExamGuard – Hệ thống quản lý bài kiểm tra trực tuyến có cơ chế giám sát và phát  hiện hành vi bất thường

## Backend và database

Backend dùng Java 25, Spring Boot, Spring Data JPA, Lombok và MySQL 8.0.16 trở lên. Giữ cấu hình kết nối trong `ExamGuardBE/src/main/resources/application.yaml`, bật MySQL và tạo database `exam_guard` trước khi chạy:

```powershell
cd ExamGuardBE
.\mvnw.cmd spring-boot:run
```

Spring Boot tạo các bảng còn thiếu từ `db/schema.sql`, thêm ba role từ `db/data.sql` rồi Hibernate kiểm tra mapping entity. Thiết kế 16 bảng, sơ đồ quan hệ, quy tắc dữ liệu và lệnh kiểm thử nằm trong [tài liệu database](docs/database-design.md).

Để thêm bộ dữ liệu mẫu (24 bản ghi mỗi bảng nghiệp vụ và liên kết, 3 role), chạy:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'
```

Script `db/sample-data.sql` có thể chạy lại tuần tự mà không nhân đôi dữ liệu. Tài khoản mẫu gồm `demo_admin01`, `demo_teacher01..03`, `demo_student01..20`, dùng mật khẩu `Demo@123` được lưu bằng BCrypt.

## Task 4 và Task 5

Backend đã có đăng ký, đăng nhập, BCrypt, JWT HS512 và phân quyền ADMIN/TEACHER/STUDENT. Phần quản trị hỗ trợ CRUD người dùng, môn học, lớp học; quản lý sinh viên trong lớp; tìm kiếm, sắp xếp và phân trang. Frontend cung cấp màn hình xác thực và giao diện quản trị tương ứng.

Khởi động frontend trong `ExamGuardFE` bằng `npm run dev`, sau đó mở `http://localhost:5173`. Xem [hướng dẫn xác thực và quản lý học vụ](docs/auth-core-management.md) để biết API, quy tắc phân trang và cách kiểm thử MySQL Docker.
