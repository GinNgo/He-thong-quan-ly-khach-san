# Decisions

## D-001 — Memory Bank theo repository

- Ngày ghi nhận: 2026-07-23
- Bối cảnh: Model phía sau 9Router có thể thay đổi và không có trí nhớ lâu dài đáng tin cậy.
- Quyết định: Lưu ngữ cảnh dự án trong `memory-bank/` tại repository root; model mới phải đọc lại Memory Bank, source và Git diff.
- Lý do: Kiến thức tồn tại độc lập với Codex, Gemini hoặc model dự phòng.
- Hệ quả: Memory Bank phải được cập nhật cuối task và luôn được đối chiếu source.

## D-002 — Nguồn sự thật

- Ngày ghi nhận: 2026-07-23
- Bối cảnh: Memory Bank có thể cũ hoặc không đầy đủ.
- Quyết định: Yêu cầu hiện tại và source hiện tại ưu tiên hơn Memory Bank; câu trả lời AI trước không phải bằng chứng.
- Lý do: Ngăn tiếp tục từ trạng thái sai sau đổi model hoặc đổi phiên.
- Hệ quả: Mọi trạng thái quan trọng phải có đường dẫn source hoặc kết quả kiểm tra liên quan.

## D-003 — Giới hạn dữ liệu lưu

- Ngày ghi nhận: 2026-07-23
- Bối cảnh: Repository có thể chứa cấu hình nhạy cảm.
- Quyết định: Không lưu secret, credential, connection string hoặc dữ liệu thật trong Rules, Skills hay Memory Bank.
- Lý do: Giảm rủi ro rò rỉ và sao chép dữ liệu giữa dự án.
- Hệ quả: Chỉ ghi tên công nghệ, biến khái niệm và đường dẫn không nhạy cảm.

## Quyết định kiến trúc ứng dụng

TBD - Chưa xác định từ source đối với lý do lịch sử của các quyết định kiến trúc ứng dụng. `systemPatterns.md` chỉ mô tả pattern đang tồn tại trong source.