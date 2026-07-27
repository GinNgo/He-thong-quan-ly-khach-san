import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-payment-simulator',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './payment-simulator.html',
  styleUrls: ['./payment-simulator.css']
})
export class PaymentSimulatorComponent implements OnInit {
  reservationId: number = 0;
  method: string = '';
  amount: number = 0;
  transactionId: string = '';
  isProcessing: boolean = false;
  isSuccess: boolean = false;
  contextError = '';
  processingError = '';

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.reservationId = Number(params['reservationId']);
      this.method = String(params['method'] || 'MOMO').toUpperCase();
      this.amount = Number(params['amount']) || 0;
      this.transactionId = params['transactionId'] || '';
      this.validateContext();
    });
  }

  get hasValidContext(): boolean {
    return !this.contextError;
  }

  confirmPayment(): void {
    if (this.isProcessing || this.isSuccess || !this.hasValidContext) return;
    this.isProcessing = true;
    this.processingError = '';
    
    // Call backend callback endpoint
    const callbackUrl = `${environment.apiUrl}/payments/callback?reservationId=${this.reservationId}&status=SUCCESS&method=${this.method}&transactionId=${encodeURIComponent(this.transactionId)}`;
    
    this.http.get(callbackUrl).subscribe({
      next: () => {
        this.isProcessing = false;
        this.isSuccess = true;
        
        // Auto redirect after 3 seconds
        setTimeout(() => {
          this.router.navigate(['/profile'], { queryParams: { tab: 'bookings' } });
        }, 3000);
      },
      error: (err) => {
        console.error('Error in payment callback', err);
        this.isProcessing = false;
        this.processingError = err?.error?.message || 'Không thể xác nhận thanh toán demo. Vui lòng thử lại hoặc quay về lịch sử đặt phòng.';
      }
    });
  }

  cancelPayment(): void {
    this.router.navigate(['/profile'], { queryParams: { tab: 'bookings' } });
  }

  goHome(): void {
    this.router.navigate(['/']);
  }

  private validateContext(): void {
    const validReservation = Number.isInteger(this.reservationId) && this.reservationId > 0;
    const validAmount = Number.isFinite(this.amount) && this.amount > 0;
    const validMethod = ['MOMO', 'VNPAY', 'STRIPE'].includes(this.method.toUpperCase());
    const validTransaction = this.transactionId.trim().length > 0;
    this.contextError = validReservation && validAmount && validMethod && validTransaction
      ? ''
      : 'Liên kết thanh toán thiếu mã đặt phòng, số tiền hoặc mã giao dịch hợp lệ. Hãy quay lại lịch sử đặt phòng để mở lại phiên thanh toán.';
  }
}
