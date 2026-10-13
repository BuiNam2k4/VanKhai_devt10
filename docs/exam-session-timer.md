# Task 10 — Phiên thi và bộ đếm thời gian

## Hành vi

Sinh viên mở Bài thi của tôi, chọn Bắt đầu thi khi đủ điều kiện hoặc Tiếp tục thi khi có phiên đang làm. API kiểm tra lại role STUDENT, chủ tài khoản, lớp được giao, trạng thái PUBLISHED, lịch mở và số lượt; không tin cờ canStart đã nhận trước đó. Tạo phiên, cấp attemptNumber, ghi startedAt/expiresAt, sao chép đề và maxScore nằm trong một transaction. Lỗi sao chép hủy phiên cùng toàn bộ bản sao.

expiresAt = min(startedAt + durationMinutes, exam.endsAt). Tất cả thời điểm lấy từ Clock UTC của máy chủ và trả ISO có Z, lưu DATETIME(6). Frontend chuyển sang múi giờ thiết bị để hiển thị. Request chỉ nhận deviceId; các giá trị studentId, giờ bắt đầu/hết hạn hoặc thời lượng do client thêm không quyết định phiên.

Bấm bắt đầu lại trên cùng thiết bị trả phiên IN_PROGRESS đã có với HTTP 200; phiên mới trả HTTP 201. Không tăng số lượt, tạo lại đề hay gia hạn. Một phiên đã hết giờ vẫn được trả với expired=true, canContinue=false, remainingSeconds=0; không cấp lượt mới khi phiên đó chưa được chốt. Task 12 sẽ tự nộp/chốt và chấm điểm. Khi đã chốt và còn lượt, lần bắt đầu kế tiếp dùng attemptNumber tiếp theo.

## API

Gửi Authorization: Bearer token sinh viên.

| Method | Endpoint | Nội dung |
| --- | --- | --- |
| POST | /api/student/exams/{examId}/sessions | Bắt đầu hoặc lấy lại phiên đang làm |
| GET | /api/student/sessions/{sessionId}?deviceId=... | Khôi phục đề/thời hạn đã lưu và giờ máy chủ mới |

POST body:

```json
{ "deviceId": "browser-device-001" }
```

deviceId dài 8–100 ký tự, gồm chữ/số/gạch ngang/gạch dưới. Frontend tự tạo UUID và giữ trong localStorage, dùng lại sau reload. Mã này là dấu hiệu thiết bị do client cung cấp; quyền truy cập luôn dựa trên tài khoản đã xác thực và lớp. Task 14 sẽ bổ sung heartbeat/Redis và chính sách đổi thiết bị. Ở Task 10, deviceId khác của cùng phiên bị HTTP 409 và không tạo lượt thay thế.

Response data gồm id, examId, examTitle, subjectName, attemptNumber, status, startedAt, expiresAt, serverTime, remainingSeconds, expired, canContinue, maxScore và questions. Mỗi câu chứa ID SessionQuestion, nội dung/loại/điểm/thứ tự; lựa chọn chứa ID SessionQuestionOption, nội dung và thứ tự. Không có correct, sourceOptionId, examQuestionId, điểm đã chấm, riskScore hoặc thông tin người khác. maxScore là tổng điểm có thể đạt, không phải kết quả bài làm.

Sai quyền role trả 403; đề không được giao/nháp và phiên của người khác trả 404. Đề đóng, ngoài lịch, hết lượt hoặc deviceId xung đột trả 409. deviceId sai trả 400. Bộ câu không hợp lệ trả 400 và không cấp phiên. Phiên cũ thiếu snapshot trả 409 với hướng dẫn liên hệ giảng viên; hệ thống không tự tạo lại đề từ ngân hàng đang thay đổi.

Khôi phục kiểm tra chủ phiên, lớp và deviceId ở mỗi lần gọi. Đề đóng chặn lượt mới nhưng phiên đã tồn tại còn hạn vẫn có thể tiếp tục. GET không đọc lại ngân hàng, không gọi factory trộn và không ghi startedAt/expiresAt. Gỡ sinh viên khỏi lớp làm mất quyền khôi phục.

## Cạnh tranh và ảnh chụp

POST khóa bản ghi Exam trước khi đọc lượt hoặc cấp phiên; thao tác đóng đề dùng cùng khóa. Các yêu cầu bắt đầu cùng đề được tuần tự hóa. Hai yêu cầu cùng tài khoản/thiết bị tạo một phiên và một bộ ảnh chụp; thiết bị khác nhận 409. Unique index của MySQL trên (exam,student,attemptNumber) và phiên IN_PROGRESS hỗ trợ bảo vệ ràng buộc.

Trước sao chép, khóa câu nguồn theo ID tăng dần và đọc lựa chọn bằng locking read để nội dung và đáp án đúng nhất quán khi ngân hàng sửa đồng thời. Service ảnh chụp Task 8 được gọi với propagation MANDATORY trong transaction cấp phiên. Các bản sao lưu nội dung, loại, điểm, thứ tự câu/lựa chọn và đáp án đúng để dùng phía máy chủ; DTO sinh viên loại bỏ dữ liệu chấm điểm.

## Bộ đếm và khôi phục trang

URL #/session/{id} giữ tuyến phiên sau reload. Đồng bộ API cung cấp serverTime và expiresAt; giao diện tính thời gian còn lại bằng performance.now và hiệu chỉnh theo độ trễ request. Thay đổi Date.now hoặc giờ của hệ điều hành không tăng thời gian. Bộ đếm lấy lại chênh lệch theo thời gian đã trôi qua mỗi nhịp, không giảm một biến đếm lưu trong localStorage.

Khôi phục khi tải lại, trở lại tab/cửa sổ, có lại kết nối và mỗi 30 giây. Tại 00:00, giao diện xác minh lại máy chủ và hiển thị hết giờ. Mất đồng bộ vẫn giữ đề đã tải và thời hạn đang đếm; không khẳng định dữ liệu đã lưu hoặc bài đã tự nộp. Đồng bộ bị từ chối do quyền/thiết bị thì ẩn đề. Rời màn hình phiên không dừng thời gian.

Màn hình Task 10 hiển thị nội dung đề và bộ đếm. Task 11 đã bổ sung chọn/nhập đáp án và tự lưu; response khôi phục có thêm `answers` với version/thời điểm lưu. Xem [hướng dẫn autosave](answer-autosave.md). Tự nộp và chấm thuộc Task 12, chưa có nút nộp giả hoặc thông báo đã chấm khi chức năng chưa tồn tại.

## Cách thử

1. Bật MySQL Docker và chạy backend/frontend theo README.
2. Giảng viên chọn đề thực hành 10 câu ở trạng thái nháp, đặt lịch mở trước hiện tại và đóng sau hiện tại; giao lớp và công bố.
3. Sinh viên thuộc lớp đó đăng nhập, bấm Bắt đầu thi, kiểm tra số câu và thời gian còn lại.
4. Reload, quay về danh sách rồi Tiếp tục thi: ID phiên, thứ tự đề và expiresAt phải giữ nguyên; thời gian đã dùng không được trả lại.
5. Thử trình duyệt khác cùng tài khoản: nhận thông báo phiên thuộc thiết bị khác. Thử tài khoản khác đọc ID phiên: bị 404.
6. Với đề sắp đóng, hạn lượt ngắn hơn durationMinutes. Hết giờ, countdown=00:00 và expired=true, chưa tự chuyển thành AUTO_SUBMITTED ở Task 10.

## Kiểm thử

Kết quả kiểm chứng: 106/106 kiểm thử backend đạt, không bỏ qua; Postman CLI đạt 20/20 assertion qua 9 request; frontend lint/build và kiểm tra Edge desktop/di động đạt. Kiểm tra trình duyệt bao gồm bắt đầu bộ đề 10 câu, sửa Date.now không tăng countdown, reload giữ snapshot/deadline, tiếp tục không tạo lượt mới, thiết bị khác bị từ chối và thời hạn ngắn về 00:00. Dữ liệu fixture được dọn sau kiểm tra.

Các bài kiểm thử H2/MySQL dùng Clock cố định để xác minh thời lượng và giờ đóng, đúng thời điểm hết hạn, idempotency, snapshot không đổi khi ngân hàng sửa, quyền/lớp/deviceId, giới hạn lượt và chặn dữ liệu giả từ client. MySqlSessionConcurrencyTests dùng fixture đã commit, hai thread yêu cầu đồng thời và lỗi chủ động sau khi snapshot đã ghi; dọn riêng fixture sau kiểm tra.

```powershell
cd ExamGuardBE
$env:EXAMGUARD_MYSQL_TEST='true'
.\mvnw.cmd test '-Dspring.jpa.show-sql=false'
```

Collection Postman được lưu trong docs/postman/exam-session-timer.postman_collection.json. Chọn một đề đang mở và tài khoản thuộc lớp được giao; collection tạo một lượt thật, rồi kiểm tra lặp/khôi phục, giới hạn deviceId và ownership. Biến examId phải đặt trước khi chạy. Ví dụ chạy tại thư mục gốc:

```powershell
postman collection run docs/postman/exam-session-timer.postman_collection.json --env-var 'examId=ID_DE_THAT' --no-report-events --bail
```

Các biến mặc định baseUrl=http://127.0.0.1:8080, username=demo_student01, otherUsername=demo_student02, password=Demo@123, deviceId=postman-local-device-001. Không export biến token; chạy cục bộ với no-report-events. Không dùng collection để giành phiên đang mở trên browser khác.
