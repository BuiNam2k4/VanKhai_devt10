# Task 11 — Lưu đáp án và tự lưu

Sinh viên chọn một đáp án trắc nghiệm hoặc nhập câu tự luận trong phiên thi. Nút **Bỏ chọn** xóa lựa chọn; xóa hết nội dung tự luận cũng lưu việc bỏ trả lời. Nội dung tiếng Việt và xuống dòng được giữ nguyên, tự luận tối đa 10.000 ký tự.

Giao diện lưu sau 1,5 giây ngừng nhập và kiểm tra phần chưa lưu mỗi 10 giây. **Lưu ngay** gửi ngay các câu thay đổi; quay về danh sách bài thi sẽ chờ lưu thành công trước khi rời phiên đang làm. Mỗi câu có trạng thái/thời điểm lưu. Chỉ báo **Đã lưu tất cả đáp án lên máy chủ** khi không còn thay đổi chờ xác nhận. Reload và Tiếp tục thi khôi phục đáp án từ cùng ảnh chụp đề, không đổi lượt hoặc hạn thi.

Mất kết nối giữ bản đang nhập và thử lại khi có mạng hoặc ở nhịp tự lưu tiếp theo. Bản nháp chưa xác nhận được giữ trong localStorage theo phiên, thiết bị và mã riêng của tab (mã tab giữ trong sessionStorage để reload khôi phục). Một tab lưu xong không xóa bản nháp của tab khác. Nếu trình duyệt chặn lưu bản nháp, giao diện thông báo rõ; lưu trên máy chủ vẫn hoạt động. Bản nháp tại trình duyệt không phải đáp án đã lưu/chấm. Khi rời hoặc tải lại với câu chưa lưu, trình duyệt có thể yêu cầu xác nhận.

## API

Bearer token STUDENT; sessionId và sessionQuestionId là ID của phiên/ảnh chụp đề, selectedOptionId là ID lựa chọn trong chính ảnh chụp câu đó, không dùng ID câu/đáp án nguồn.

| Method | Endpoint | Chức năng |
| --- | --- | --- |
| GET | `/api/student/sessions/{sessionId}/answers?deviceId=...` | Đọc đáp án đã lưu, giờ máy chủ, hạn và canSave |
| PUT | `/api/student/sessions/{sessionId}/answers` | Lưu một hoặc nhiều câu đã thay đổi trong một transaction |

Request PUT:

```json
{
  "deviceId": "browser-device-001",
  "answers": [
    { "sessionQuestionId": 101, "selectedOptionId": 501, "answerText": null, "version": null },
    { "sessionQuestionId": 102, "selectedOptionId": null, "answerText": "Nội dung tự luận", "version": null }
  ]
}
```

Thay ID bằng dữ liệu phiên thật. `version: null` dùng khi câu chưa có bản ghi đáp án; lần lưu đầu trả version 0, lần thay đổi sau gửi version đã nhận và nhận phiên bản tăng lên. PUT là cập nhật một phần: câu không gửi được giữ nguyên. Để bỏ trả lời, gửi selectedOptionId và answerText đều null cùng version hiện tại; vẫn giữ bản ghi để chặn việc ghi đè từ yêu cầu cũ. Không có thao tác xóa vật lý đáp án.

Request có 1–1000 câu, không trùng sessionQuestionId. Trắc nghiệm chỉ có selectedOptionId (hoặc null), không nhận answerText; tự luận không nhận selectedOptionId. Device ID giữ cùng quy tắc Task 10 (8–100 ký tự chữ/số/gạch ngang/gạch dưới). Không nhận savedAt, awardedPoints hay thời gian client làm dữ liệu quyết định.

Response data gồm sessionId, serverTime, expiresAt, canSave và answers. Mỗi đáp án có sessionQuestionId, selectedOptionId, answerText, version, savedAt. GET khôi phục phiên Task 10 cũng có `answers` để reload lấy đề và đáp án đã lưu. Không trả đáp án đúng, nguồn chấm hoặc điểm được chấm.

## Quyền, thời hạn và xung đột

Mọi lần đọc/ghi đều kiểm tra role, chủ phiên, thành viên lớp được giao và thiết bị. Phiên của người khác/mất lớp trả 404; sai role 403; thiết bị khác 409; dữ liệu/option sai hoặc trùng câu 400. Chỉ ghi khi IN_PROGRESS và giờ máy chủ nhỏ hơn expiresAt. Đúng thời điểm hết hạn hoặc đã SUBMITTED/AUTO_SUBMITTED trả 409, không đổi đáp án. Đề đóng vẫn cho phiên đã bắt đầu còn hạn lưu tiếp, như Task 10.

PUT khóa đúng bản ghi phiên có studentId của người đăng nhập, rồi kiểm tra toàn bộ batch trước khi ghi. Một câu sai hoặc một xung đột làm toàn bộ batch thất bại. Unique index và version của StudentAnswer cùng kiểm tra version từ request bảo vệ cả trường hợp lần đầu lưu đồng thời. Hai câu khác nhau không ghi đè nhau; cùng giá trị gửi lại sau mất xác nhận không tạo bản ghi mới, không thay savedAt/version. Yêu cầu cũ với giá trị khác bị 409.

Khi 409, frontend đọc lại đáp án. Nếu phiên hết hạn thì khóa nhập; nếu câu đã đổi từ tab khác thì giữ bản đang nhập và hiển thị hai lựa chọn: **Dùng đáp án máy chủ** hoặc **Lưu bản đang nhập**. Lựa chọn thứ hai dùng phiên bản máy chủ vừa đọc để gửi lại có chủ ý. Phản hồi cũ hoặc đồng bộ định kỳ không ghi đè nội dung mới vừa gõ khi đang chờ lưu.

Task 11 không nộp/chấm bài; hết giờ chỉ khóa việc nhập và lưu. Tự nộp và chấm điểm thuộc Task 12.

## Kiểm thử và lỗi các task trước

Đã chạy toàn bộ backend Task 4–11 trên H2 và MySQL thật: **125/125 đạt, không bỏ qua**. Bao gồm đăng nhập/phân quyền, CRUD và phân trang học vụ, ngân hàng câu hỏi, cấu hình/công bố, gán/trộn đề, danh sách sinh viên, phiên thi, lưu/xóa câu trả lời, stale version, batch rollback, hạn thi, dữ liệu giả và lưu đồng thời. Fixture có transaction rollback hoặc dọn riêng các bản ghi đã commit.

Frontend có **9/9** kiểm thử trạng thái đáp án: giữ phần nhập trong khi request chờ, bỏ phản hồi cũ, giải quyết xung đột, khôi phục local draft, mất xác nhận, xóa đáp án và tách bản nháp của từng tab. Kiểm tra trình duyệt thực tế đã xác minh trắc nghiệm/tự luận tự lưu, reload, quay về rồi tiếp tục, máy chủ phản hồi chậm, mất kết nối, hai tab xung đột và hết giờ về 00:00 khóa radio/textarea/nút lưu. Lint/build đạt, không cảnh báo lint. Collection Postman Task 10 chạy lại đạt **20/20 assertion qua 9 request** sau thay đổi API khôi phục. Các đề fixture riêng đã được dọn; đề demo Task 11 để người dùng test chưa bị dùng lượt.

Lỗi giao diện Task 5 đã sửa trong đợt này: thông báo API nằm sau cửa sổ nhập (nay hiển thị trong biểu mẫu); gửi lặp khi đang lưu (khóa form/nút đóng); kết quả yêu cầu cũ khi đổi trang/bộ lọc (hủy request cũ và gắn kết quả với đúng truy vấn). Lỗi kết nối chung đổi thành thông báo tiếng Việt.

```powershell
cd ExamGuardBE
$env:EXAMGUARD_MYSQL_TEST='true'
.\mvnw.cmd test '-Dspring.jpa.show-sql=false'
cd ..\ExamGuardFE
npm test
npm run lint
npm run build
```

Để thử thủ công, teacher công bố một đề mới đang mở và có cả hai loại câu hỏi. Student thuộc lớp được giao bắt đầu lượt mới, chọn/nhập đáp án, chờ trạng thái Đã lưu rồi reload. Nếu phiên cũ đã hết giờ thì không thể dùng nó để thử lưu; tạo đề thực hành mới, không sửa hạn hay xóa lịch sử phiên cũ.
