# Task 6 Ngân hàng câu hỏi

ExamGuard cung cấp CRUD câu hỏi và các lựa chọn, phân loại theo môn học và độ khó, tìm kiếm, sắp xếp và phân trang. Backend tiếp tục dùng Controller → Service → Repository, DTO và `ApiResponse`; phân trang sử dụng `PaginationUtils` chung với Task 5 theo quy ước của `backend-java-full-step`.

## Quyền truy cập

- TEACHER dùng `/api/teacher/questions` và chỉ thấy, xem chi tiết, sửa hoặc xóa câu hỏi do mình tạo.
- ADMIN dùng `/api/admin/questions` để quản lý toàn bộ ngân hàng câu hỏi. ADMIN cũng được gọi namespace teacher.
- STUDENT không được truy cập ngân hàng hoặc đáp án đúng.
- Người tạo lấy từ principal đã xác thực; API không nhận `createdById` từ client để quyết định quyền.
- `GET /api/teacher/subjects` cung cấp danh mục môn học có phân trang cho giảng viên. CRUD môn học vẫn thuộc admin.

## API

Các endpoint dưới đây có hai prefix `/api/teacher` và `/api/admin`.

| Method | Endpoint | Kết quả |
| --- | --- | --- |
| GET | `/questions` | Danh sách theo quyền truy cập và bộ lọc |
| GET | `/questions/{id}` | Chi tiết cùng đáp án và thứ tự lựa chọn |
| POST | `/questions` | Tạo câu hỏi và các lựa chọn trong một transaction, trả HTTP 201 |
| PUT | `/questions/{id}` | Cập nhật câu hỏi và các lựa chọn trong một transaction |
| DELETE | `/questions/{id}` | Xóa câu hỏi chưa được gán vào đề và các lựa chọn |

Ví dụ:

```text
GET /api/teacher/questions?keyword=java&subjectId=1&difficulty=HARD&type=SINGLE_CHOICE&active=true&sortBy=id:desc&page=1&size=10
```

`keyword`, `subjectId`, `difficulty`, `type`, `active`, `sortBy` đều không bắt buộc. `page=1` là trang đầu; `page=0` cũng ánh xạ về trang đầu để tương thích Task 5. `size` từ 1 đến 100. Các trường sắp xếp được chấp nhận: `id`, `content`, `difficulty`, `type`, `active`, `createdAt`, `updatedAt`. Response có `pageNumber`, `pageSize`, `totalPages`, `totalElements`, `questions` bên trong `data`.

```json
{
  "subjectId": 1,
  "content": "Từ khóa nào khai báo lớp trong Java?",
  "type": "SINGLE_CHOICE",
  "difficulty": "EASY",
  "active": true,
  "options": [
    { "content": "class", "correct": true },
    { "content": "function", "correct": false },
    { "content": "def", "correct": false }
  ]
}
```

Thay `subjectId` bằng ID môn học có thật. Khi cập nhật, gửi `id` của lựa chọn hiện có để giữ danh tính; lựa chọn mới không gửi `id`. Thứ tự trong mảng quyết định `displayOrder` từ 1. Lựa chọn hiện có bị bỏ khỏi mảng sẽ được xóa nếu chưa được dữ liệu phiên thi tham chiếu. Không được gửi ID lựa chọn của câu hỏi khác hoặc lặp cùng ID.

Câu hỏi `ESSAY` gửi `options: []`. Độ khó gồm `EASY`, `MEDIUM`, `HARD`. `active=false` đánh dấu câu hỏi ngừng sử dụng, vẫn có thể tìm trong ngân hàng bằng bộ lọc.

## Ràng buộc và lịch sử

Nội dung câu hỏi không được trống, tối đa 10.000 ký tự; nội dung từng lựa chọn không được trống, tối đa 2.000 ký tự. Trắc nghiệm một đáp án cần 2–10 lựa chọn, nội dung khác nhau sau khi trim và không phân biệt hoa thường, đúng một lựa chọn đúng. Tự luận không có lựa chọn. Môn học phải tồn tại.

Khi sửa hoặc xóa, Service khóa bản ghi câu hỏi để tuần tự hóa các thao tác ghi. Khi sắp xếp lại đáp án, Service tạm chuyển thứ tự cũ ra ngoài phạm vi thứ tự cuối trước khi ghi danh sách mới, tránh vi phạm unique `(question_id, display_order)` của MySQL.

Câu hỏi đã được gán vào đề thi không được xóa, đổi môn hoặc đổi loại (HTTP 409). Có thể sửa nội dung, độ khó, trạng thái và nội dung lựa chọn còn giữ ID. Khóa ngoại giữ các lựa chọn đã được snapshot phiên thi tham chiếu; cố xóa chúng trả HTTP 409 và rollback toàn bộ cập nhật. Snapshot nội dung của phiên thi vẫn độc lập với ngân hàng. Tạo và sửa đều thực hiện trong một transaction, không lưu nửa câu hỏi.

## Giao diện

Admin chọn **Ngân hàng câu hỏi** trong sidebar. Giảng viên đăng nhập sẽ vào màn hình này. Có bộ lọc môn, độ khó, loại, trạng thái, tìm theo nội dung và sắp xếp; thao tác xem/sửa/xóa; form chọn một đáp án đúng, thêm/xóa hoặc đưa lựa chọn lên trên. Modal có trạng thái đang lưu và hiển thị lỗi từ API. Danh mục môn học được tải đủ các trang.

Chạy backend và frontend như Task 4/5. Dữ liệu mẫu đã được nạp có tài khoản `demo_admin01` và `demo_teacher01`, cùng mật khẩu `Demo@123`.

## Kiểm thử

```powershell
cd ExamGuardBE
.\mvnw.cmd test
```

Để chạy cả MySQL Docker và H2:

```powershell
$env:EXAMGUARD_MYSQL_TEST = 'true'
.\mvnw.cmd test '-Dspring.jpa.show-sql=false'
Remove-Item Env:\EXAMGUARD_MYSQL_TEST
```

Các test Task 6 kiểm tra CRUD, giữ ID và đổi thứ tự đáp án, quyền chủ sở hữu, admin/student, lọc và phân trang, payload sai, chuyển trắc nghiệm sang tự luận, chặn xóa/đổi môn/đổi loại câu hỏi đã được gán vào đề, danh mục môn cho giảng viên. Dữ liệu kiểm thử dùng tiền tố riêng và transaction rollback.

```powershell
cd ExamGuardFE
npm run lint
npm run build
```
