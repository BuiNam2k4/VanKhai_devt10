# VanKhai_devt10
ExamGuard – Hệ thống quản lý bài kiểm tra trực tuyến có cơ chế giám sát và phát  hiện hành vi bất thường

## Backend và database

Backend dùng Java 25, Spring Boot, Spring Data JPA, Lombok và MySQL 8.0.16 trở lên. Giữ cấu hình kết nối trong `ExamGuardBE/src/main/resources/application.yaml`, bật MySQL và tạo database `exam_guard` trước khi chạy:

```powershell
cd ExamGuardBE
.\mvnw.cmd spring-boot:run
```

Spring Boot tạo các bảng còn thiếu từ `db/schema.sql`, thêm ba role từ `db/data.sql` rồi Hibernate kiểm tra mapping entity. Thiết kế 16 bảng, sơ đồ quan hệ, quy tắc dữ liệu và lệnh kiểm thử nằm trong [tài liệu database](docs/database-design.md).

Để thêm bộ dữ liệu mẫu, chạy profile `demo`. Bộ này gồm 24 tài khoản, 24 môn/lớp, 24 đề có lịch sử thi và **24 đề nháp mới, mỗi đề 10 câu**, với tổng cộng 240 câu hỏi thuộc các môn tương ứng:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=demo'
```

Profile chạy `db/sample-data.sql` rồi `db/sample-exam-data.sql`. Có thể chạy lại tuần tự để thêm các bản ghi còn thiếu; dữ liệu đã có được giữ. Phần bổ sung không sửa các đề cũ đã có lịch sử thi.

## Tài khoản và mật khẩu mẫu

Đăng nhập bằng **tên đăng nhập**, không dùng email. Tất cả tài khoản dưới đây có mật khẩu **`Demo@123`**, được lưu bằng BCrypt trong database.

| Loại tài khoản | Tên đăng nhập | Mật khẩu | Mục đích sử dụng |
| --- | --- | --- | --- |
| Admin (ADMIN) | `demo_admin01` | `Demo@123` | Quản lý người dùng, môn, lớp, toàn bộ câu hỏi và bài thi |
| Giảng viên (TEACHER) | `demo_teacher01`, `demo_teacher02`, `demo_teacher03` | `Demo@123` | Quản lý ngân hàng câu hỏi và đề do mình phụ trách |
| Sinh viên (STUDENT) | `demo_student01` đến `demo_student20` | `Demo@123` | Đăng nhập vai trò sinh viên; dùng kiểm thử các chức năng tham gia thi ở các task tiếp theo |

Ví dụ sinh viên: `demo_student01`, `demo_student02`, …, `demo_student20`. Đây là 1 admin, 3 giảng viên và 20 sinh viên. Script chỉ tạo tài khoản còn thiếu, không đặt lại mật khẩu tài khoản đã được bạn đổi.

## Test đề có nhiều câu

Mở `http://localhost:5173`, đăng nhập giảng viên rồi chọn **Quản lý bài thi**. Tìm `Đề thực hành 10 câu`, chọn **Câu hỏi** để xem 10 câu, tổng 10 điểm. Mỗi giảng viên có 8 đề nháp của các môn mình phụ trách.

Các đề này đã giao lớp, có thời lượng 45 phút, tối đa 3 lượt và bật trộn câu/đáp án. Bạn có thể sửa điểm, đổi thứ tự, bỏ/thêm câu và **Xem thử đề thi**. Muốn công bố, vào **Sửa** đặt đủ thời gian mở/đóng còn hiệu lực, rồi **Công bố**. Các đề mẫu cũ có tên `Bài kiểm tra mẫu` vẫn giữ dữ liệu lịch sử và một câu như trước.

## Task 4 và Task 5

Backend đã có đăng ký, đăng nhập, BCrypt, JWT HS512 và phân quyền ADMIN/TEACHER/STUDENT. Phần quản trị hỗ trợ CRUD người dùng, môn học, lớp học; quản lý sinh viên trong lớp; tìm kiếm, sắp xếp và phân trang. Frontend cung cấp màn hình xác thực và giao diện quản trị tương ứng.

Khởi động frontend trong `ExamGuardFE` bằng `npm run dev`, sau đó mở `http://localhost:5173`. Xem [hướng dẫn xác thực và quản lý học vụ](docs/auth-core-management.md) để biết API, quy tắc phân trang và cách kiểm thử MySQL Docker.

## Task 6

Ngân hàng câu hỏi hỗ trợ CRUD, trắc nghiệm một đáp án và tự luận, phân loại môn học/độ khó, tìm kiếm và phân trang. Giảng viên quản lý câu hỏi của mình, admin quản lý toàn bộ; sinh viên không truy cập đáp án đúng. Xem [hướng dẫn ngân hàng câu hỏi](docs/question-bank.md) để biết API, validation, bảo vệ dữ liệu đã gán vào đề và cách kiểm thử.

## Task 7

Giảng viên và admin có thể tạo, xem, sửa, xóa bài thi nháp; cấu hình lịch mở/đóng, thời lượng, số lượt thi, hiển thị kết quả, cờ trộn câu/đáp án và lớp được giao. Danh sách có tìm kiếm, lọc, sắp xếp và phân trang theo hợp đồng hiện có. Backend kiểm tra điều kiện công bố và khóa cấu hình sau khi công bố. Xem [hướng dẫn quản lý bài thi](docs/exam-management.md) để biết API và các bước kiểm thử.

## Task 8

Trong Quản lý bài thi, nút **Câu hỏi** mở màn hình chọn câu hỏi cùng môn từ ngân hàng có tìm kiếm/phân trang, đặt điểm, đổi thứ tự và bỏ câu khỏi đề. API kiểm tra quyền, trạng thái nháp, câu hỏi hợp lệ và giới hạn điểm; giữ mã liên kết khi sắp xếp. Có bản xem thử áp dụng cấu hình trộn câu/đáp án và dịch vụ tạo ảnh chụp đề lưu thứ tự cố định cho phiên thi. Xem [hướng dẫn câu hỏi trong đề và trộn đề](docs/exam-questions.md). Task 10 sẽ gọi dịch vụ ảnh chụp trong luồng bắt đầu phiên của sinh viên.
