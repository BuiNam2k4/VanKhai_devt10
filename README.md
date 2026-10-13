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
| Sinh viên (STUDENT) | `demo_student01` đến `demo_student20` | `Demo@123` | Tra cứu bài thi, bắt đầu/tiếp tục phiên, xem đề đã trộn và thời gian còn lại |

Ví dụ sinh viên: `demo_student01`, `demo_student02`, …, `demo_student20`. Đây là 1 admin, 3 giảng viên và 20 sinh viên. Script chỉ tạo tài khoản còn thiếu, không đặt lại mật khẩu tài khoản đã được bạn đổi.

## Xem tài khoản thuộc lớp nào

- **Admin:** vào **Người dùng**, tìm tên đăng nhập rồi chọn **Xem lớp**. Danh sách hiển thị các lớp tài khoản đang tham gia hoặc phụ trách; chọn **Xem sinh viên** để xem tên đăng nhập trong lớp. Thêm/xóa thành viên vẫn thực hiện tại **Lớp học → Sinh viên**.
- **Teacher:** vào **Lớp phụ trách → Xem sinh viên**, tìm theo tên đăng nhập, họ tên hoặc email. Chỉ xem được sinh viên trong các lớp mình phụ trách.
- **Student:** vào **Lớp của tôi** để xem mã lớp, tên lớp và giảng viên. Một tài khoản có thể tham gia nhiều lớp; không cần có đề đã công bố mới xem được lớp.

Ví dụ dữ liệu mẫu: `demo_student01` thuộc `DEMO-CLS-01` và `DEMO-CLS-21`; `demo_student02` thuộc `DEMO-CLS-02` và `DEMO-CLS-22`. Đề Docker giao cho `DEMO-CLS-22` cần test bằng `demo_student02`. Xem [API và kiểm thử tra cứu lớp](docs/class-membership.md).

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

## Task 9

Sinh viên đăng nhập vào màn hình **Bài thi của tôi**, chỉ thấy đề PUBLISHED/CLOSED của lớp mình. Có tìm kiếm theo tên/môn/mô tả, lọc môn/trạng thái/lịch, sắp xếp, phân trang và xem chi tiết. Backend tính trạng thái tham gia theo UTC, lượt đã dùng và phiên đang làm; response danh sách không chứa đề, đáp án đúng hoặc điểm. Đề thực hành mới đang DRAFT nên chưa hiển thị cho sinh viên; giảng viên cần đặt lịch và công bố trước. Xem [hướng dẫn bài thi sinh viên](docs/student-exams.md).

## Task 10

Sinh viên có thể **Bắt đầu thi** hoặc **Tiếp tục thi**. Backend kiểm tra quyền, lớp, lịch, trạng thái và lượt còn lại, cấp ExamSession cùng ảnh chụp đề trong một transaction. Thời hạn là min(bắt đầu + thời lượng, giờ đóng đề). Gửi lại yêu cầu trên cùng thiết bị hoặc tải lại trang giữ nguyên phiên, thứ tự câu/đáp án và hạn thi. Bộ đếm dựa vào giờ máy chủ và thời gian đơn điệu của trình duyệt, không dựa vào đồng hồ máy khách. Hết hạn hiển thị 00:00 và phiên không được gia hạn; tự nộp/chấm điểm thuộc Task 12, lưu đáp án thuộc Task 11.

Xem [hướng dẫn phiên thi và bộ đếm giờ](docs/exam-session-timer.md) và [collection Postman Task 10](docs/postman/exam-session-timer.postman_collection.json) để chạy thử API. Một bộ mẫu đã có phiên từ trước có thể thuộc deviceId cũ; để thử luồng mới, giảng viên công bố một đề thực hành còn nháp với lịch đang mở rồi sinh viên thuộc lớp được giao bắt đầu lượt.
