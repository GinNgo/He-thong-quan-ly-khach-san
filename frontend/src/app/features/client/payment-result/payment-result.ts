import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-payment-result',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './payment-result.html',
  styleUrls: ['./payment-result.css']
})
export class PaymentResultComponent implements OnInit {
  status: 'PROCESSING' | 'SUCCESS' | 'FAILED' = 'PROCESSING';
  message: string = 'Đang xử lý kết quả thanh toán...';
  
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);

  ngOnInit(): void {
    // Get query string
    this.route.queryParams.subscribe(params => {
      if (params['vnp_SecureHash']) {
        // It's a VNPAY callback
        const queryString = new URLSearchParams(params).toString();
        this.verifyVnpay(queryString);
      } else {
        this.status = 'FAILED';
        this.message = 'Không tìm thấy dữ liệu thanh toán hợp lệ.';
      }
    });
  }

  verifyVnpay(queryString: string) {
    this.http.get<any>(`${environment.apiUrl}/payments/vnpay-callback?${queryString}`).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.status = 'SUCCESS';
          this.message = 'Thanh toán thành công! Đơn đặt phòng của bạn đã được xác nhận.';
          // Auto redirect
          setTimeout(() => {
            this.router.navigate(['/profile'], { queryParams: { tab: 'bookings' } });
          }, 3000);
        } else {
          this.status = 'FAILED';
          this.message = res.message || 'Thanh toán thất bại hoặc đã bị hủy.';
        }
      },
      error: (err) => {
        this.status = 'FAILED';
        this.message = 'Lỗi kết nối máy chủ khi xác thực thanh toán.';
        console.error(err);
      }
    });
  }
}
