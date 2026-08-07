import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap, take, takeWhile, timer } from 'rxjs';
import { PublicI18nService } from '../../../core/i18n/public-i18n.service';
import { PaymentService, PaymentSessionStatus } from '../../../core/services/payment.service';

@Component({
  selector: 'app-payment-result',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './payment-result.html',
  styleUrls: ['./payment-result.css']
})
export class PaymentResultComponent implements OnInit {
  status: 'PROCESSING' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'RECONCILIATION' = 'PROCESSING';
  message = '';
  provider = '';
  reservationId?: number;
  
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private paymentService = inject(PaymentService);
  private destroyRef = inject(DestroyRef);
  readonly i18n = inject(PublicI18nService);

  ngOnInit(): void {
    this.message = this.i18n.text('PUBLIC.PAYMENT.INITIAL_MESSAGE');
    this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const sessionId = params['session'];
      this.provider = params['provider'] || '';
      if (!sessionId) {
        this.status = 'FAILED';
        this.message = this.i18n.text('PUBLIC.PAYMENT.INVALID_DATA');
        return;
      }
      this.pollAuthoritativeStatus(sessionId);
    });
  }

  goToBookings(): void {
    this.router.navigate(['/profile'], { queryParams: { tab: 'bookings' } });
  }

  private pollAuthoritativeStatus(sessionId: string): void {
    timer(0, 2000).pipe(
      switchMap(() => this.paymentService.getPaymentSessionStatus(sessionId)),
      takeWhile((session) => session.status === 'CREATED' || session.status === 'PENDING', true),
      take(10),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: (session) => this.applySessionStatus(session),
      error: () => {
        this.status = 'PENDING';
        this.message = this.i18n.text('PUBLIC.PAYMENT.STATUS_UNAVAILABLE');
      },
    });
  }

  private applySessionStatus(session: PaymentSessionStatus): void {
    this.provider = session.provider;
    this.reservationId = session.reservationId;
    if (session.status === 'SUCCEEDED' && session.reconciliationRequired) {
      this.status = 'RECONCILIATION';
      this.message = this.i18n.text('PUBLIC.PAYMENT.RECONCILIATION_MESSAGE');
      return;
    }
    if (session.status === 'SUCCEEDED') {
      this.status = 'SUCCESS';
      this.message = this.i18n.text('PUBLIC.PAYMENT.SUCCESS_MESSAGE');
      return;
    }
    if (session.status === 'FAILED') {
      this.status = 'FAILED';
      this.message = this.i18n.text('PUBLIC.PAYMENT.FAILED_MESSAGE');
      return;
    }
    if (session.status === 'EXPIRED') {
      this.status = 'FAILED';
      this.message = this.i18n.text('PUBLIC.PAYMENT.EXPIRED_MESSAGE');
      return;
    }
    this.status = 'PENDING';
    this.message = this.i18n.text('PUBLIC.PAYMENT.PENDING_MESSAGE');
  }
}
