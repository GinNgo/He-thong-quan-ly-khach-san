# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: home-search.spec.ts >> Home Search Redesign >> should open location autocomplete on click and show popular destinations
- Location: e2e\home-search.spec.ts:28:7

# Error details

```
Error: locator.click: Error: strict mode violation: locator('app-location-autocomplete input') resolved to 2 elements:
    1) <input type="text" placeholder="Bạn muốn đến đâu?" class="w-full h-full border-0 focus:ring-0 text-[15px] font-medium text-gray-800 bg-transparent outline-none placeholder:text-gray-400 ng-untouched ng-pristine ng-valid"/> aka locator('app-sticky-search-bar').getByRole('textbox', { name: 'Bạn muốn đến đâu?' })
    2) <input type="text" placeholder="Bạn muốn đến đâu?" class="w-full h-full border-0 focus:ring-0 text-[15px] font-medium text-gray-800 bg-transparent outline-none placeholder:text-gray-400 ng-untouched ng-pristine ng-valid"/> aka locator('app-hero-search').getByRole('textbox', { name: 'Bạn muốn đến đâu?' })

Call log:
  - waiting for locator('app-location-autocomplete input')

```

# Page snapshot

```yaml
- generic [ref=e2]:
  - generic:
    - alertdialog
  - generic [ref=e3]:
    - banner [ref=e4]:
      - generic [ref=e6]:
        - link "Luxstay Hotels" [ref=e7] [cursor=pointer]:
          - /url: /
          - generic [ref=e13]:
            - generic [ref=e14]: Luxstay
            - generic [ref=e15]: Hotels
        - navigation [ref=e16]:
          - link "Phòng nghỉ" [ref=e17] [cursor=pointer]:
            - /url: /#rooms
          - link "Dịch vụ" [ref=e18] [cursor=pointer]:
            - /url: /#services
          - link "Ưu đãi" [ref=e19] [cursor=pointer]:
            - /url: /#offers
          - link "Tìm khách sạn" [ref=e20] [cursor=pointer]:
            - /url: /search
          - link "Đánh giá" [ref=e21] [cursor=pointer]:
            - /url: /#reviews
        - generic [ref=e22]:
          - generic [ref=e23] [cursor=pointer]:
            - generic [ref=e24]: VN
            - generic [ref=e25]: expand_more
          - link "Đăng nhập/Đăng ký" [ref=e26] [cursor=pointer]:
            - /url: /login
    - main [ref=e27]:
      - generic [ref=e29]:
        - generic [ref=e31]:
          - generic [ref=e33]: LuxeStay
          - generic [ref=e34]:
            - generic [ref=e38]:
              - generic [ref=e39]: 
              - textbox "Bạn muốn đến đâu?" [ref=e40]
            - generic [ref=e43]:
              - generic [ref=e45] [cursor=pointer]:
                - generic [ref=e46]: 
                - generic [ref=e47]:
                  - generic [ref=e48]: 13 tháng 7 2026
                  - generic [ref=e49]: Thứ Hai
              - generic [ref=e51] [cursor=pointer]:
                - generic [ref=e52]: 
                - generic [ref=e53]:
                  - generic [ref=e54]: 14 tháng 7 2026
                  - generic [ref=e55]: Thứ Ba
            - generic [ref=e59] [cursor=pointer]:
              - generic [ref=e60]:
                - generic [ref=e61]: 
                - generic [ref=e62]:
                  - generic [ref=e63]: 2 người lớn
                  - generic [ref=e64]: 1 phòng
              - generic [ref=e65]: 
          - button "TÌM" [ref=e66] [cursor=pointer]
        - generic [ref=e67]:
          - img "Resort" [ref=e69]
          - generic [ref=e71]:
            - heading "Trải nghiệm Đẳng cấp" [level=1] [ref=e72]
            - paragraph [ref=e73]: Khám phá hàng ngàn khách sạn, khu nghỉ dưỡng cao cấp tại Việt Nam với giá tốt nhất.
        - generic [ref=e76]:
          - generic [ref=e78]:
            - button " Tất cả chỗ nghỉ" [ref=e79] [cursor=pointer]:
              - generic [ref=e80]: 
              - generic [ref=e81]: Tất cả chỗ nghỉ
            - button " Khách sạn" [ref=e82] [cursor=pointer]:
              - generic [ref=e83]: 
              - generic [ref=e84]: Khách sạn
            - button " Nhà nghỉ" [ref=e85] [cursor=pointer]:
              - generic [ref=e86]: 
              - generic [ref=e87]: Nhà nghỉ
            - button " Homestay" [ref=e88] [cursor=pointer]:
              - generic [ref=e89]: 
              - generic [ref=e90]: Homestay
            - button " Căn hộ & Villa" [ref=e91] [cursor=pointer]:
              - generic [ref=e92]: 
              - generic [ref=e93]: Căn hộ & Villa
            - button " Vé máy bay Sắp ra mắt" [disabled] [ref=e94]:
              - generic [ref=e95]: 
              - generic [ref=e96]: Vé máy bay
              - generic [ref=e97]: Sắp ra mắt
            - button " Đưa đón sân bay Sắp ra mắt" [disabled] [ref=e98]:
              - generic [ref=e99]: 
              - generic [ref=e100]: Đưa đón sân bay
              - generic [ref=e101]: Sắp ra mắt
          - generic [ref=e102]:
            - generic [ref=e104]:
              - generic [ref=e108] [cursor=pointer]: Chỗ Ở Qua Đêm
              - generic "Tính năng đang được phát triển" [ref=e109]:
                - generic [ref=e111]: Chỗ Ở Trong Ngày
                - generic [ref=e112]: Sắp ra mắt
            - generic [ref=e113]:
              - generic [ref=e117]:
                - generic [ref=e118]: 
                - textbox "Bạn muốn đến đâu?" [ref=e119]
              - generic [ref=e122]:
                - generic [ref=e124] [cursor=pointer]:
                  - generic [ref=e125]: 
                  - generic [ref=e126]:
                    - generic [ref=e127]: 13 tháng 7 2026
                    - generic [ref=e128]: Thứ Hai
                - generic [ref=e130] [cursor=pointer]:
                  - generic [ref=e131]: 
                  - generic [ref=e132]:
                    - generic [ref=e133]: 14 tháng 7 2026
                    - generic [ref=e134]: Thứ Ba
              - generic [ref=e138] [cursor=pointer]:
                - generic [ref=e139]:
                  - generic [ref=e140]: 
                  - generic [ref=e141]:
                    - generic [ref=e142]: 2 người lớn
                    - generic [ref=e143]: 1 phòng
                - generic [ref=e144]: 
              - button "TÌM" [ref=e146] [cursor=pointer]:
                - generic [ref=e147]: TÌM
        - generic [ref=e148]:
          - generic [ref=e150]:
            - heading "Chương trình khuyến mãi chỗ ở" [level=2] [ref=e151]
            - generic [ref=e152]:
              - generic [ref=e153]:
                - generic [ref=e154]:
                  - img "Kỳ nghỉ Vàng" [ref=e155]
                  - generic [ref=e156]: Giảm đến 20%
                - generic [ref=e157]:
                  - heading "Kỳ nghỉ Vàng" [level=3] [ref=e158]
                  - paragraph [ref=e159]: Giảm ngay 20% cho các đặt phòng từ nay đến cuối năm. Áp dụng cho phòng Suite tại toàn bộ hệ thống LuxeStay.
                  - generic [ref=e160]:
                    - generic [ref=e161]:
                      - generic [ref=e162]: GOLDEN26
                      - button "" [ref=e163] [cursor=pointer]:
                        - generic [ref=e164]: 
                    - button "Xem chi tiết" [ref=e165] [cursor=pointer]
              - generic [ref=e166]:
                - generic [ref=e167]:
                  - img "Ưu đãi Mùa Hè" [ref=e168]
                  - generic [ref=e169]: Giảm đến 20%
                - generic [ref=e170]:
                  - heading "Ưu đãi Mùa Hè" [level=3] [ref=e171]
                  - paragraph [ref=e172]: Nhập mã SUMMER26 để nhận ưu đãi ăn sáng buffet miễn phí cho 2 người và miễn phí đưa đón sân bay.
                  - generic [ref=e173]:
                    - generic [ref=e174]:
                      - generic [ref=e175]: SUMMER26
                      - button "" [ref=e176] [cursor=pointer]:
                        - generic [ref=e177]: 
                    - button "Xem chi tiết" [ref=e178] [cursor=pointer]
              - generic [ref=e179]:
                - generic [ref=e180]:
                  - img "Thành viên mới" [ref=e181]
                  - generic [ref=e182]: Giảm đến 20%
                - generic [ref=e183]:
                  - heading "Thành viên mới" [level=3] [ref=e184]
                  - paragraph [ref=e185]: Tặng Voucher 500k cho khách hàng đăng ký tài khoản thành viên LuxeStay ngay hôm nay. Hàng ngàn ưu đãi đang chờ đón.
                  - generic [ref=e186]:
                    - generic [ref=e187]:
                      - generic [ref=e188]: NEWUSER
                      - button "" [ref=e189] [cursor=pointer]:
                        - generic [ref=e190]: 
                    - button "Xem chi tiết" [ref=e191] [cursor=pointer]
          - heading "Các điểm đến thu hút nhất Việt Nam" [level=2] [ref=e194]
    - contentinfo [ref=e216]:
      - generic [ref=e217]:
        - generic [ref=e219]:
          - generic [ref=e220]: LuxeStay
          - generic [ref=e221]: Hotels
        - generic [ref=e222]:
          - link "Privacy Policy" [ref=e223] [cursor=pointer]:
            - /url: "#"
          - link "Terms of Service" [ref=e224] [cursor=pointer]:
            - /url: "#"
        - paragraph [ref=e225]: © 2026 LuxeStay Hotels. All rights reserved.
    - button "" [ref=e227] [cursor=pointer]:
      - generic [ref=e228]: 
```

# Test source

```ts
  1  | import { test, expect } from '@playwright/test';
  2  | 
  3  | test.describe('Home Search Redesign', () => {
  4  | 
  5  |   test.beforeEach(async ({ page }) => {
  6  |     await page.goto('/');
  7  |   });
  8  | 
  9  |   test('should display the hero search panel with default values', async ({ page }) => {
  10 |     // Check tabs
  11 |     await expect(page.locator('app-search-service-tabs')).toBeVisible();
  12 |     await expect(page.getByText('Tất cả chỗ nghỉ')).toBeVisible();
  13 | 
  14 |     // Check location
  15 |     const locationInput = page.locator('app-location-autocomplete input');
  16 |     await expect(locationInput).toBeVisible();
  17 |     await expect(locationInput).toHaveAttribute('placeholder', 'Bạn muốn đến đâu?');
  18 | 
  19 |     // Check dates (should have default text)
  20 |     await expect(page.locator('app-date-range-selector')).toBeVisible();
  21 |     
  22 |     // Check guests (should default to 2 adults, 1 room)
  23 |     const guestSelector = page.locator('app-guest-room-selector');
  24 |     await expect(guestSelector).toContainText('2 người lớn');
  25 |     await expect(guestSelector).toContainText('1 phòng');
  26 |   });
  27 | 
  28 |   test('should open location autocomplete on click and show popular destinations', async ({ page }) => {
  29 |     const locationInput = page.locator('app-location-autocomplete input');
> 30 |     await locationInput.click();
     |                         ^ Error: locator.click: Error: strict mode violation: locator('app-location-autocomplete input') resolved to 2 elements:
  31 |     
  32 |     // Popover should be visible
  33 |     const popover = page.locator('.p-popover');
  34 |     await expect(popover).toBeVisible();
  35 |     
  36 |     // Should show popular destinations
  37 |     await expect(page.getByText('Các thành phố nổi tiếng')).toBeVisible();
  38 |   });
  39 | 
  40 |   test('should toggle stay type to day-use', async ({ page }) => {
  41 |     // Click Day Use
  42 |     await page.getByText('Chỗ Ở Trong Ngày').click();
  43 |     // Wait, we disabled day-use in our implementation for now because backend doesn't support it!
  44 |     // Let's verify it is disabled.
  45 |     const dayUseRadio = page.locator('input[value="DAY_USE"]');
  46 |     await expect(dayUseRadio).toBeDisabled();
  47 |   });
  48 | 
  49 |   test('should update guest counts', async ({ page }) => {
  50 |     await page.locator('app-guest-room-selector').click();
  51 |     
  52 |     // Increase adults (second pi-plus button)
  53 |     const plusButtons = page.locator('.p-popover .pi-plus');
  54 |     await plusButtons.nth(1).click();
  55 |     
  56 |     // Wait for update
  57 |     await expect(page.locator('app-guest-room-selector')).toContainText('3 người lớn');
  58 |   });
  59 | 
  60 |   test('sticky search bar should appear on scroll', async ({ page }) => {
  61 |     // Initially hidden
  62 |     const stickyBar = page.locator('app-sticky-search-bar > div').first();
  63 |     await expect(stickyBar).toHaveClass(/.*-translate-y-full.*/);
  64 | 
  65 |     // Scroll down 500px
  66 |     await page.evaluate(() => window.scrollBy(0, 500));
  67 |     
  68 |     // Should become visible
  69 |     await expect(stickyBar).toHaveClass(/.*translate-y-0.*/);
  70 |   });
  71 | });
  72 | 
```