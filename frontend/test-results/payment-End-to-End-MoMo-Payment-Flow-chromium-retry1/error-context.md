# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: payment.spec.ts >> End-to-End MoMo Payment Flow
- Location: e2e\payment.spec.ts:3:5

# Error details

```
Test timeout of 30000ms exceeded.
```

```
Error: page.waitForURL: Test timeout of 30000ms exceeded.
=========================== logs ===========================
waiting for navigation to "http://localhost:4200/" until "load"
============================================================
```

# Page snapshot

```yaml
- generic [ref=e2]:
  - generic:
    - alertdialog
  - generic [ref=e4]:
    - generic:
      - img "Hotel Lobby"
    - main [ref=e5]:
      - generic [ref=e7]:
        - generic [ref=e9]:
          - generic [ref=e11]: apartment
          - heading "LuxeStay Portal" [level=1] [ref=e12]
          - paragraph [ref=e13]: Đăng nhập để truy cập hệ thống
        - generic [ref=e14]: Sai tài khoản hoặc mật khẩu.
        - generic [ref=e15]:
          - generic [ref=e16]:
            - generic [ref=e17]: Email đăng nhập
            - generic [ref=e18]:
              - generic [ref=e19]: mail
              - textbox "Email đăng nhập" [ref=e20]:
                - /placeholder: manager@luxuryhotel.com
                - text: customer1
          - generic [ref=e21]:
            - generic [ref=e22]: Mật khẩu
            - generic [ref=e23]:
              - generic [ref=e24]: lock
              - textbox "Mật khẩu" [ref=e25]:
                - /placeholder: ••••••••
                - text: password
              - button "visibility" [ref=e26] [cursor=pointer]:
                - generic [ref=e27]: visibility
          - generic [ref=e28]:
            - generic [ref=e29] [cursor=pointer]:
              - checkbox "Ghi nhớ tôi" [ref=e31]
              - generic [ref=e32]: Ghi nhớ tôi
            - link "Quên mật khẩu?" [ref=e33] [cursor=pointer]:
              - /url: "#"
          - button "Đăng nhập arrow_forward" [ref=e34] [cursor=pointer]:
            - text: Đăng nhập
            - generic [ref=e35]: arrow_forward
        - paragraph [ref=e37]:
          - text: Chưa có tài khoản?
          - link "Tạo tài khoản mới" [ref=e38] [cursor=pointer]:
            - /url: /register
    - contentinfo [ref=e39]:
      - generic [ref=e40]:
        - generic [ref=e41]:
          - generic [ref=e42]: LuxeStay
          - paragraph [ref=e43]: © 2026 LuxeStay Hospitality Group. All rights reserved.
        - generic [ref=e44]:
          - link "Chính sách Bảo mật" [ref=e45] [cursor=pointer]:
            - /url: "#"
          - link "Điều khoản Dịch vụ" [ref=e46] [cursor=pointer]:
            - /url: "#"
          - link "Hỗ trợ" [ref=e47] [cursor=pointer]:
            - /url: "#"
```

# Test source

```ts
  1  | import { test, expect } from '@playwright/test';
  2  | 
  3  | test('End-to-End MoMo Payment Flow', async ({ page }) => {
  4  |   // 1. Go to login
  5  |   await page.goto('http://localhost:4200/login');
  6  |   
  7  |   // 2. Login
  8  |   await page.fill('input[type="text"]', 'customer1');
  9  |   await page.fill('input[type="password"]', 'password');
  10 |   await page.click('button[type="submit"]');
> 11 |   await page.waitForURL('http://localhost:4200/');
     |              ^ Error: page.waitForURL: Test timeout of 30000ms exceeded.
  12 | 
  13 |   // 3. Go to search and pick a room
  14 |   await page.goto('http://localhost:4200/search');
  15 |   await page.waitForLoadState('networkidle');
  16 |   
  17 |   // Click the first hotel "Xem chi tiết"
  18 |   await page.click('text=Xem chi tiết >> nth=0');
  19 |   
  20 |   // Wait for hotel detail page
  21 |   await page.waitForSelector('text=Đặt phòng ngay');
  22 |   
  23 |   // Click first "Đặt phòng ngay"
  24 |   await page.click('text=Đặt phòng ngay >> nth=0');
  25 |   
  26 |   // Wait for checkout page
  27 |   await page.waitForURL(/.*\/booking\/.*/);
  28 | 
  29 |   // 4. Fill checkout form
  30 |   await page.fill('input[name="lastName"]', 'Test');
  31 |   await page.fill('input[name="firstName"]', 'Customer');
  32 |   await page.fill('input[name="phone"]', '0123456789');
  33 |   
  34 |   // 5. Select MoMo payment
  35 |   await page.click('input[value="MOMO"]');
  36 |   
  37 |   // 6. Submit
  38 |   await page.click('button[type="submit"]');
  39 | 
  40 |   // 7. Verify Redirect to Payment Simulator
  41 |   await page.waitForURL(/.*\/payment-simulator.*/);
  42 |   
  43 |   // Take screenshot of simulator
  44 |   await page.screenshot({ path: 'payment-simulator.png' });
  45 |   
  46 |   // 8. Confirm Payment
  47 |   await page.click('button:has-text("Xác nhận Thanh toán")');
  48 |   
  49 |   // 9. Verify Success Screen
  50 |   await page.waitForSelector('text=Thanh toán thành công!');
  51 |   await page.screenshot({ path: 'payment-success.png' });
  52 |   
  53 |   // 10. Wait for auto redirect to profile bookings
  54 |   await page.waitForURL(/.*\/profile\?tab=bookings.*/, { timeout: 10000 });
  55 |   await page.screenshot({ path: 'booking-history.png' });
  56 | });
  57 | 
```