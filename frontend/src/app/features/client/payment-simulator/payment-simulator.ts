import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { PublicI18nService } from '../../../core/i18n/public-i18n.service';
import { PaymentService } from '../../../core/services/payment.service';

@Component({
  selector: 'app-payment-simulator',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './payment-simulator.html',
  styleUrls: ['./payment-simulator.css'],
})
export class PaymentSimulatorComponent implements OnInit {
  token = '';
  isProcessing = false;
  isSuccess = false;
  contextError = '';
  processingError = '';

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private paymentService = inject(PaymentService);
  readonly i18n = inject(PublicI18nService);

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      this.token = String(params['token'] || '').trim();
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

    this.paymentService.confirmDemoPayment(this.token).subscribe({
      next: () => {
        this.isProcessing = false;
        this.isSuccess = true;
        setTimeout(() => {
          this.router.navigate(['/profile'], { queryParams: { tab: 'bookings' } });
        }, 3000);
      },
      error: (error) => {
        console.error('Error in payment confirmation', error);
        this.isProcessing = false;
        this.processingError =
          error?.error?.message ||
          this.i18n.text('PUBLIC.PAYMENT.CONFIRM_ERROR');
      },
    });
  }

  cancelPayment(): void {
    this.router.navigate(['/profile'], { queryParams: { tab: 'bookings' } });
  }

  goHome(): void {
    this.router.navigate(['/']);
  }

  private validateContext(): void {
    this.contextError = this.token
      ? ''
      : this.i18n.text('PUBLIC.PAYMENT.TOKEN_ERROR');
  }
}
