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
  isProcessing: boolean = false;
  isSuccess: boolean = false;

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.reservationId = Number(params['reservationId']);
      this.method = params['method'] || 'MOMO';
      this.amount = Number(params['amount']) || 0;
    });
  }

  confirmPayment() {
    this.isProcessing = true;
    
    // Call backend callback endpoint
    const callbackUrl = `${environment.apiUrl}/payments/callback?reservationId=${this.reservationId}&status=SUCCESS&method=${this.method}`;
    
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
        alert('Lỗi xử lý thanh toán giả lập!');
      }
    });
  }

  cancelPayment() {
    // Go back to home if cancelled
    this.router.navigate(['/']);
  }
}
