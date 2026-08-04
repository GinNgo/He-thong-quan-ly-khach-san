import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import {
  ReservationAmendmentContext,
  ReservationAmendmentQuote,
  ReservationAmendmentRequest,
  ReservationService,
} from '../../core/services/reservation.service';

@Component({
  selector: 'app-reservation-amendment-workspace',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './reservation-amendment-workspace.component.html',
  styleUrls: ['./reservation-amendment-workspace.component.css'],
})
export class ReservationAmendmentWorkspaceComponent implements OnInit, OnDestroy {
  @Input({ required: true }) reservationId!: number;
  @Input() staffMode = false;
  @Output() readonly closed = new EventEmitter<void>();
  @Output() readonly applied = new EventEmitter<number>();

  private readonly reservationService = inject(ReservationService);
  private readonly fb = inject(FormBuilder);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private countdownTimer: ReturnType<typeof setInterval> | null = null;
  private pollTimer: ReturnType<typeof setInterval> | null = null;
  private formReady = false;
  private quotedIdentity = '';

  context: ReservationAmendmentContext | null = null;
  quote: ReservationAmendmentQuote | null = null;
  loading = true;
  busy = false;
  error = '';
  secondsRemaining = 0;
  selectedPaymentMethod = '';

  readonly form = this.fb.nonNullable.group({
    proposedRoomTypeId: [0, [Validators.required, Validators.min(1)]],
    proposedCheckInDate: ['', Validators.required],
    proposedCheckOutDate: ['', Validators.required],
    proposedQuantity: [1, [Validators.required, Validators.min(1)]],
    proposedAdults: [1, [Validators.required, Validators.min(1)]],
    proposedChildren: [0, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.form.valueChanges.subscribe(() => {
      if (!this.formReady || !this.quote) return;
      if (JSON.stringify(this.form.getRawValue()) === this.quotedIdentity) return;
      this.quote = null;
      this.quotedIdentity = '';
      this.error = '';
      this.stopTimers();
    });
    this.loadContext();
  }

  ngOnDestroy(): void {
    this.stopTimers();
  }

  loadContext(): void {
    if (this.loading && this.context) return;
    this.loading = true;
    this.error = '';
    this.reservationService.getAmendmentContext(this.reservationId, this.staffMode)
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: context => {
          this.context = context;
          this.selectedPaymentMethod = context.paymentMethods[0] || '';
          this.form.patchValue({
            proposedRoomTypeId: context.current.roomTypeId,
            proposedCheckInDate: context.current.checkInDate,
            proposedCheckOutDate: context.current.checkOutDate,
            proposedQuantity: context.current.quantity,
            proposedAdults: context.current.adults,
            proposedChildren: context.current.children,
          }, { emitEvent: false });
          this.formReady = true;
        },
        error: error => this.error = this.errorMessage(error, 'Không thể tải chính sách thay đổi đặt phòng.'),
      });
  }

  requestQuote(): void {
    if (this.busy || this.form.invalid || !this.context?.allowed) {
      this.form.markAllAsTouched();
      return;
    }
    const request = this.form.getRawValue() as ReservationAmendmentRequest;
    this.quotedIdentity = JSON.stringify(request);
    this.busy = true;
    this.error = '';
    this.reservationService.createAmendmentQuote(
      this.reservationId,
      request,
      this.idempotencyKey('quote', JSON.stringify(request)),
      this.staffMode,
    ).pipe(finalize(() => this.busy = false)).subscribe({
      next: quote => this.acceptQuote(quote),
      error: error => this.error = this.errorMessage(error, 'Không thể tạo báo giá thay đổi.'),
    });
  }

  confirm(): void {
    if (!this.quote || this.busy || this.expired) return;
    if (this.moneyValue(this.quote.priceDelta.amount) > 0) {
      const attempt = this.quote.settlement.paymentAttempt;
      if (attempt?.status === 'SUCCESS') {
        this.applyQuote();
        return;
      }
      if (attempt && ['CREATED', 'PENDING', 'PENDING_VERIFICATION', 'PROCESSING'].includes(attempt.status)) {
        this.refreshQuote();
        return;
      }
      this.createPaymentAttempt();
      return;
    }
    this.applyQuote();
  }

  requote(): void {
    this.quote = null;
    this.quotedIdentity = '';
    this.error = '';
    this.stopTimers();
    this.requestQuote();
  }

  close(): void {
    this.closed.emit();
  }

  formatMoney(amount: number | string): string {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 })
      .format(this.moneyValue(amount));
  }

  priceDeltaValue(quote: ReservationAmendmentQuote): number {
    return this.moneyValue(quote.priceDelta.amount);
  }

  get expired(): boolean {
    return Boolean(this.quote && (this.quote.status === 'EXPIRED' || this.secondsRemaining <= 0));
  }

  get countdownLabel(): string {
    const minutes = Math.floor(this.secondsRemaining / 60).toString().padStart(2, '0');
    const seconds = (this.secondsRemaining % 60).toString().padStart(2, '0');
    return `${minutes}:${seconds}`;
  }

  get primaryLabel(): string {
    if (!this.quote) return 'Tạo báo giá';
    if (this.expired) return 'Báo giá đã hết hạn';
    if (this.moneyValue(this.quote.priceDelta.amount) <= 0) return 'Áp dụng thay đổi';
    const status = this.quote.settlement.paymentAttempt?.status;
    if (status === 'SUCCESS') return 'Áp dụng thay đổi';
    if (status && ['CREATED', 'PENDING', 'PENDING_VERIFICATION', 'PROCESSING'].includes(status)) {
      return 'Kiểm tra thanh toán';
    }
    return 'Tạo thanh toán phần chênh lệch';
  }

  private createPaymentAttempt(): void {
    if (!this.quote || !this.selectedPaymentMethod) {
      this.error = 'Khách sạn chưa cấu hình phương thức thanh toán khả dụng.';
      return;
    }
    this.busy = true;
    this.error = '';
    this.reservationService.createAmendmentPaymentAttempt(
      this.reservationId,
      this.quote.publicId,
      this.selectedPaymentMethod,
      this.idempotencyKey('payment', this.quote.publicId),
      this.staffMode,
    ).pipe(finalize(() => this.busy = false)).subscribe({
      next: quote => {
        this.acceptQuote(quote);
        this.startPolling();
      },
      error: error => this.error = this.errorMessage(error, 'Không thể tạo giao dịch thanh toán.'),
    });
  }

  private applyQuote(): void {
    if (!this.quote) return;
    this.busy = true;
    this.error = '';
    this.reservationService.applyAmendmentQuote(
      this.reservationId,
      this.quote.publicId,
      this.idempotencyKey('apply', this.quote.publicId),
      this.staffMode,
    ).pipe(finalize(() => this.busy = false)).subscribe({
      next: quote => {
        this.acceptQuote(quote);
        if (quote.status === 'APPLIED') {
          this.clearKeys();
          this.applied.emit(this.reservationId);
        }
      },
      error: error => this.error = this.errorMessage(error, 'Không thể áp dụng thay đổi đặt phòng.'),
    });
  }

  private refreshQuote(): void {
    if (!this.quote || this.busy) return;
    const quoteId = this.quote.publicId;
    this.reservationService.getAmendmentQuote(this.reservationId, quoteId, this.staffMode).subscribe({
      next: quote => {
        this.acceptQuote(quote);
        if (quote.settlement.paymentAttempt?.status === 'SUCCESS' && quote.status !== 'APPLIED') {
          this.applyQuote();
        }
      },
      error: error => {
        this.stopPolling();
        this.error = this.errorMessage(error, 'Không thể kiểm tra trạng thái thanh toán.');
      },
    });
  }

  private acceptQuote(quote: ReservationAmendmentQuote): void {
    this.quote = quote;
    this.secondsRemaining = Math.max(0, Math.ceil((Date.parse(quote.expiresAt) - Date.now()) / 1000));
    this.startCountdown();
    const attemptStatus = quote.settlement.paymentAttempt?.status;
    if (quote.status === 'PAYMENT_PENDING'
      && attemptStatus
      && ['CREATED', 'PENDING', 'PENDING_VERIFICATION', 'PROCESSING'].includes(attemptStatus)) {
      this.startPolling();
    } else {
      this.stopPolling();
    }
    this.changeDetector.markForCheck();
  }

  private startCountdown(): void {
    if (!this.quote) return;
    if (this.countdownTimer) clearInterval(this.countdownTimer);
    this.countdownTimer = setInterval(() => {
      if (!this.quote) return;
      this.secondsRemaining = Math.max(0, Math.ceil((Date.parse(this.quote.expiresAt) - Date.now()) / 1000));
      if (this.secondsRemaining === 0) {
        this.stopTimers();
      }
      this.changeDetector.markForCheck();
    }, 1000);
  }

  private startPolling(): void {
    if (this.pollTimer) return;
    this.pollTimer = setInterval(() => this.refreshQuote(), 3000);
  }

  private stopPolling(): void {
    if (this.pollTimer) clearInterval(this.pollTimer);
    this.pollTimer = null;
  }

  private stopTimers(): void {
    if (this.countdownTimer) clearInterval(this.countdownTimer);
    this.countdownTimer = null;
    this.stopPolling();
  }

  private idempotencyKey(operation: string, identity: string): string {
    const storageKey = `hotel:amendment:${this.reservationId}:${operation}:${this.hash(identity)}`;
    const existing = sessionStorage.getItem(storageKey);
    if (existing) return existing;
    const generated = globalThis.crypto?.randomUUID?.()
      ?? `${operation}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    sessionStorage.setItem(storageKey, generated);
    return generated;
  }

  private clearKeys(): void {
    const prefix = `hotel:amendment:${this.reservationId}:`;
    for (let index = sessionStorage.length - 1; index >= 0; index--) {
      const key = sessionStorage.key(index);
      if (key?.startsWith(prefix)) sessionStorage.removeItem(key);
    }
  }

  private hash(value: string): string {
    let result = 2166136261;
    for (let index = 0; index < value.length; index++) {
      result ^= value.charCodeAt(index);
      result = Math.imul(result, 16777619);
    }
    return (result >>> 0).toString(16);
  }

  private errorMessage(error: any, fallback: string): string {
    return error?.error?.message || fallback;
  }

  private moneyValue(value: number | string): number {
    const amount = typeof value === 'number' ? value : Number(value);
    return Number.isFinite(amount) ? amount : 0;
  }
}
