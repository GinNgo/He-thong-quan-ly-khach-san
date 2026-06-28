---
version: "alpha"
name: Hotel Management System
description: "Visual identity for the Hotel Management System dashboard and client interface."
colors:
  primary: "#2563EB"
  primary-hover: "#1D4ED8"
  primary-light: "#DBEAFE"
  secondary: "#0F172A"
  secondary-light: "#334155"
  accent-gold: "#D4AF37"
  success: "#16A34A"
  success-light: "#DCFCE7"
  warning: "#F59E0B"
  warning-light: "#FEF3C7"
  danger: "#DC2626"
  danger-light: "#FEE2E2"
  background: "#F8FAFC"
  card-bg: "#FFFFFF"
  border: "#E2E8F0"
  heading: "#0F172A"
  text: "#334155"
  text-muted: "#64748B"
  chart-revenue: "#2563EB"
  chart-occupancy: "#16A34A"
  chart-reservation-trend: "#F59E0B"
  chart-ai-analytics: "#8B5CF6"
typography:
  default:
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif'
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "#ffffff"
    borderColor: "{colors.primary}"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
    borderColor: "{colors.primary-hover}"
  status-badge-available:
    backgroundColor: "{colors.success-light}"
    textColor: "{colors.success}"
    fontWeight: "600"
  status-badge-reserved:
    backgroundColor: "{colors.warning-light}"
    textColor: "{colors.warning}"
  status-badge-occupied:
    backgroundColor: "{colors.primary-light}"
    textColor: "{colors.primary}"
  status-badge-maintenance:
    backgroundColor: "{colors.danger-light}"
    textColor: "{colors.danger}"
---

## Overview

Dự án Hệ thống Quản lý Khách sạn sử dụng giao diện hiện đại, tối giản nhưng vẫn mang lại cảm giác sang trọng. Màu sắc chủ đạo tập trung vào tông xanh hiện đại, kết hợp với điểm nhấn màu vàng đồng (Gold) đặc trưng của ngành khách sạn. Hệ thống UI dựa trên các framework Bootstrap và PrimeNG.

Design Philosophy: Professional, Modern, Premium, Clean, Enterprise Dashboard.

## Colors

Bảng màu được thiết kế để tạo độ tương phản cao, dễ đọc, và phân biệt rõ ràng các trạng thái của phòng/dịch vụ.

- **Primary ({colors.primary}):** Màu xanh dương chuyên nghiệp dùng cho các nút bấm chính, các liên kết và trạng thái phòng đang có khách (Occupied).
- **Secondary ({colors.secondary}):** Màu xanh đen đậm dùng cho chữ tiêu đề (Heading), Sidebar, Header và các khối nền cần độ tương phản mạnh.
- **Accent ({colors.accent-gold}):** Màu vàng đồng, được dùng cho Premium Badge, VIP Room, Hotel Branding.
- **Backgrounds:** Sử dụng màu nền xám rất nhạt ({colors.background}) để làm nổi bật các component Card màu trắng ({colors.card-bg}).

### Status Colors
Màu sắc trạng thái được sử dụng xuyên suốt trong hệ thống cho Badges và Tags:
- **Available / Success:** Xanh lá ({colors.success}) trên nền nhạt ({colors.success-light}) cho Đặt phòng thành công, Thanh toán thành công, Phòng trống.
- **Reserved / Warning:** Cam/Vàng ({colors.warning}) trên nền nhạt ({colors.warning-light}) cho Chờ duyệt, Sắp checkout, Đã đặt.
- **Occupied / Info:** Xanh dương ({colors.primary}) trên nền nhạt ({colors.primary-light}).
- **Maintenance / Error:** Đỏ ({colors.danger}) trên nền nhạt ({colors.danger-light}) cho Hủy phòng, Xóa, Lỗi.

### Dashboard Charts
- Revenue: `{colors.chart-revenue}`
- Occupancy: `{colors.chart-occupancy}`
- Reservation Trend: `{colors.chart-reservation-trend}`
- AI Analytics: `{colors.chart-ai-analytics}`

### Dark Mode (Future)
- Background: `#0F172A`
- Card: `#1E293B`
- Text: `#F8FAFC`
- Border: `#334155`

## Typography

Hệ thống sử dụng các font chữ mặc định của hệ điều hành (San Francisco trên Apple, Segoe UI trên Windows) để tối ưu tốc độ tải trang và mang lại cảm giác quen thuộc, native cho người dùng.

## Components

### Buttons
- Sử dụng class `.btn-primary-hotel` thay vì `.btn-primary` mặc định của Bootstrap để đảm bảo màu sắc đồng bộ với `{colors.primary}`.

### Status Badges
- Dùng class `.badge-status` kết hợp với các class cụ thể như `.status-available`, `.status-reserved`, v.v. để hiển thị trạng thái của thực thể.

## Do's and Don'ts

- **Do:** Sử dụng các CSS Variables (`--hotel-*`) đã định nghĩa trong `styles.css` khi viết custom CSS.
- **Do:** Dùng Primary Blue cho các action chính.
- **Do:** Dùng Gold chỉ cho các premium features (Ví dụ: VIP Room).
- **Do:** Tuân thủ chuẩn accessibility WCAG và giữ khoảng cách/màu sắc đồng nhất.
- **Don't:** Không hardcode mã màu Hex vào trong các file CSS Component hoặc HTML inline style.
- **Don't:** Lạm dụng màu Đỏ (chỉ dùng cho destructive actions) và màu Gold.
