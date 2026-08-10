import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { finalize, map, of, switchMap } from 'rxjs';
import { PublicI18nService } from '@app/core/i18n/public-i18n.service';
import { AuthService } from '@app/core/services/auth';
import {
  ClientApiService,
  ReservationSummary,
  UserContext,
} from '@app/core/services/client-api.service';
import {
  PaymentLifecycleSummary,
  RefundSummary,
  ReservationService,
} from '@app/core/services/reservation.service';
import { AsyncActionCoordinatorService } from '@app/core/services/async-action-coordinator.service';
import { UserService } from '@app/core/services/user';
import { EmailVerificationService } from '@app/core/services/email-verification.service';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css'],
})
export class ProfileComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly clientApi = inject(ClientApiService);
  private readonly userService = inject(UserService);
  private readonly emailVerification = inject(EmailVerificationService);
  private readonly reservationService = inject(ReservationService);
  private readonly actionCoordinator = inject(AsyncActionCoordinatorService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly changeDetector = inject(ChangeDetectorRef);
  readonly i18n = inject(PublicI18nService);

  user: UserContext | null = null;
  activeTab: 'profile' | 'bookings' = 'profile';
  bookings: ReservationSummary[] = [];
  loading = true;
  profileEmpty = false;
  profileLoadFailed = false;
  bookingsLoading = false;
  saving = false;
  uploading = false;
  emailActionBusy = false;
  cancellingId: number | null = null;
  cancellationBookingId: number | null = null;
  error = '';
  bookingsError = '';
  success = '';
  readonly emailVerificationText = {
    verified: 'Email đã xác minh / Email verified',
    unverified: 'Email chưa xác minh / Email not verified',
    pending: 'Email mới đang chờ xác minh / New email awaiting verification',
    resend: 'Gửi lại liên kết / Resend link',
    sending: 'Đang gửi... / Sending...',
    sent: 'Liên kết xác minh đã được tạo. Vui lòng kiểm tra hộp thư. / A verification link was created. Check your inbox.',
    sendError: 'Không thể gửi liên kết xác minh lúc này. / A verification link cannot be sent right now.',
    alreadyVerified: 'Email hiện tại đã được xác minh. / The current email is already verified.',
    changePending: 'Thông tin đã lưu. Email mới chỉ có hiệu lực sau khi xác minh. / Profile saved. The new email takes effect only after verification.',
  } as const;
  readonly profileReadText = {
    emptyTitle: 'Chưa có dữ liệu hồ sơ / Profile data is unavailable',
    emptyHelp: 'Hãy thử tải lại. Nếu lỗi tiếp diễn, vui lòng liên hệ hỗ trợ. / Retry now or contact support if the issue continues.',
    retry: 'Tải lại hồ sơ / Retry profile',
  } as const;

  readonly profileForm = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', [Validators.maxLength(30), Validators.pattern(/^[0-9+().\-\s]*$/)]],
    avatarUrl: [''],
  });

  readonly cancellationForm = this.fb.nonNullable.group({
    reasonCode: ['', Validators.required],
    reason: ['', Validators.maxLength(500)],
  });

  ngOnInit(): void {
    const routeTab = this.route.snapshot.data['tab'];
    this.activeTab = routeTab === 'bookings' ? 'bookings' : 'profile';
    this.route.queryParams.subscribe((params) => {
      if (params['tab'] === 'bookings' || params['tab'] === 'profile')
        this.activeTab = params['tab'];
      if (this.activeTab === 'bookings') this.loadBookings();
    });
    this.loadProfile();
  }

  get initials(): string {
    return (this.user?.fullName || this.user?.username || 'U')
      .trim()
      .split(/\s+/)
      .slice(-2)
      .map((part) => part[0])
      .join('')
      .toUpperCase();
  }

  setActiveTab(tab: 'profile' | 'bookings'): void {
    this.activeTab = tab;
    this.router.navigate(['/profile'], { queryParams: { tab } });
  }

  saveProfile(): void {
    if (this.profileForm.invalid) {
      this.profileForm.markAllAsTouched();
      return;
    }
    const value = this.profileForm.getRawValue();
    const currentEmail = this.user?.email || value.email;
    const requestedEmail = value.email.trim().toLowerCase();
    const emailChanged = requestedEmail !== currentEmail.toLowerCase();
    this.saving = true;
    this.error = '';
    this.success = '';
    this.userService
      .updateProfile({ ...value, email: currentEmail })
      .pipe(
        switchMap((profile) => {
          if (!emailChanged) return of({ profile, pendingEmail: this.user?.pendingEmail });
          return this.emailVerification.requestEmailChange(requestedEmail).pipe(
            map((dispatch) => ({ profile, pendingEmail: dispatch.pendingEmail || requestedEmail })),
          );
        }),
        finalize(() => (this.saving = false)),
      )
      .subscribe({
        next: ({ profile, pendingEmail }) => {
          this.user = { ...this.user!, ...profile, pendingEmail };
          this.profileForm.patchValue({ email: profile.email });
          this.authService.updateCurrentUser(profile);
          this.success = emailChanged
            ? this.emailVerificationText.changePending
            : this.i18n.text('PUBLIC.ACCOUNT.PROFILE_SAVE_SUCCESS');
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.error = this.i18n.text('PUBLIC.ACCOUNT.PROFILE_SAVE_ERROR');
          this.changeDetector.detectChanges();
        },
      });
  }

  resendEmailVerification(): void {
    if (this.emailActionBusy) return;
    this.emailActionBusy = true;
    this.error = '';
    this.success = '';
    this.emailVerification.resend().pipe(
      finalize(() => {
        this.emailActionBusy = false;
        this.changeDetector.detectChanges();
      }),
    ).subscribe({
      next: (dispatch) => {
        if (this.user && dispatch.pendingEmail) {
          this.user = { ...this.user, pendingEmail: dispatch.pendingEmail };
        }
        this.success = dispatch.alreadyVerified
          ? this.emailVerificationText.alreadyVerified
          : this.emailVerificationText.sent;
      },
      error: () => {
        this.error = this.emailVerificationText.sendError;
      },
    });
  }

  uploadAvatar(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/') || file.size > 5 * 1024 * 1024) {
      this.error = this.i18n.text('PUBLIC.ACCOUNT.AVATAR_FILE_ERROR');
      input.value = '';
      return;
    }
    this.uploading = true;
    this.error = '';
    this.userService
      .uploadAvatar(file)
      .pipe(finalize(() => (this.uploading = false)))
      .subscribe({
        next: (response) => {
          this.profileForm.patchValue({ avatarUrl: response.url });
          this.saveProfile();
        },
        error: () => {
          this.error = this.i18n.text('PUBLIC.ACCOUNT.AVATAR_UPLOAD_ERROR');
          this.changeDetector.detectChanges();
        },
      });
  }

  loadBookings(): void {
    if (this.bookingsLoading) return;
    this.bookingsLoading = true;
    this.bookingsError = '';
    this.clientApi
      .getMyBookings()
      .pipe(
        finalize(() => {
          this.bookingsLoading = false;
          this.changeDetector.detectChanges();
        }),
      )
      .subscribe({
        next: (data) => {
          this.bookings = data;
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.bookingsError = this.i18n.text('PUBLIC.ACCOUNT.BOOKINGS_LOAD_ERROR');
          this.changeDetector.detectChanges();
        },
      });
  }

  cancelBooking(id: number): void {
    if (this.cancellingId !== null) return;
    this.cancellationBookingId = id;
    this.cancellationForm.reset({ reasonCode: '', reason: '' });
  }

  closeCancellationDialog(): void {
    if (this.cancellingId !== null) return;
    this.cancellationBookingId = null;
  }

  confirmCancellation(): void {
    const id = this.cancellationBookingId;
    if (id === null || this.cancellationForm.invalid) {
      this.cancellationForm.markAllAsTouched();
      return;
    }
    const cancellation = this.cancellationForm.getRawValue();
    if (cancellation.reasonCode === 'OTHER' && !cancellation.reason.trim()) {
      this.cancellationForm.controls.reason.setErrors({ required: true });
      return;
    }

    this.cancellingId = id;
    this.bookingsError = '';
    this.success = '';
    const idempotencyKey = this.getCancellationKey(id);
    this.actionCoordinator
      .run(`reservation:cancel:${id}`, () => this.reservationService.cancelMyReservation(id, cancellation, idempotencyKey))
      .pipe(
        finalize(() => {
          this.cancellingId = null;
          this.changeDetector.detectChanges();
        }),
      )
      .subscribe({
        next: (updated) => {
          this.bookings = this.bookings.map((booking) =>
            booking.id === id
              ? {
                  ...booking,
                  status: updated.status || 'CANCELLED',
                  payment: updated.payment,
                  refunds: updated.refunds,
                  cancellationReasonCode: updated.cancellationReasonCode,
                  cancellationReason: updated.cancellationReason,
                  cancelledAt: updated.cancelledAt,
                }
              : booking,
          );
          this.success = updated.refunds?.length
            ? this.i18n.text('PUBLIC.ACCOUNT.CANCEL_SUCCESS_REFUND')
            : this.i18n.text('PUBLIC.ACCOUNT.CANCEL_SUCCESS_NO_REFUND');
          sessionStorage.removeItem(`hotel:reservation-cancel:${id}`);
          this.cancellationBookingId = null;
          this.loadProfile();
        },
        error: (err) => {
          this.bookingsError = err.error?.message || this.i18n.text('PUBLIC.ACCOUNT.CANCEL_ERROR');
        },
      });
  }

  private getCancellationKey(id: number): string {
    const storageKey = `hotel:reservation-cancel:${id}`;
    const current = sessionStorage.getItem(storageKey);
    if (current) return current;
    const generated = globalThis.crypto?.randomUUID?.()
      ?? `cancel-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    sessionStorage.setItem(storageKey, generated);
    return generated;
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/']);
  }
  avatarError(): void {
    this.profileForm.patchValue({ avatarUrl: '' });
  }
  getStatusLabel(status: string): string {
    return (
      (
        {
          PENDING: 'PUBLIC.ACCOUNT.STATUS_PENDING',
          PENDING_PAYMENT: 'PUBLIC.ACCOUNT.STATUS_PENDING_PAYMENT',
          CONFIRMED: 'PUBLIC.ACCOUNT.STATUS_CONFIRMED',
          CHECKED_IN: 'PUBLIC.ACCOUNT.STATUS_CHECKED_IN',
          CHECKED_OUT: 'PUBLIC.ACCOUNT.STATUS_CHECKED_OUT',
          CANCELLED: 'PUBLIC.ACCOUNT.STATUS_CANCELLED',
          EXPIRED: 'PUBLIC.ACCOUNT.STATUS_EXPIRED',
          REJECTED: 'PUBLIC.ACCOUNT.STATUS_REJECTED',
          NO_SHOW: 'PUBLIC.ACCOUNT.STATUS_NO_SHOW',
          COMPLETED: 'PUBLIC.ACCOUNT.STATUS_COMPLETED',
        } as Record<string, string>
      )[status] ? this.i18n.text(({
        PENDING: 'PUBLIC.ACCOUNT.STATUS_PENDING', PENDING_PAYMENT: 'PUBLIC.ACCOUNT.STATUS_PENDING_PAYMENT', CONFIRMED: 'PUBLIC.ACCOUNT.STATUS_CONFIRMED', CHECKED_IN: 'PUBLIC.ACCOUNT.STATUS_CHECKED_IN', CHECKED_OUT: 'PUBLIC.ACCOUNT.STATUS_CHECKED_OUT', CANCELLED: 'PUBLIC.ACCOUNT.STATUS_CANCELLED', EXPIRED: 'PUBLIC.ACCOUNT.STATUS_EXPIRED', REJECTED: 'PUBLIC.ACCOUNT.STATUS_REJECTED', NO_SHOW: 'PUBLIC.ACCOUNT.STATUS_NO_SHOW', COMPLETED: 'PUBLIC.ACCOUNT.STATUS_COMPLETED'
      } as Record<string, string>)[status]) : status
    );
  }

  getPaymentLabel(payment?: PaymentLifecycleSummary): string {
    if (!payment) return this.i18n.text('PUBLIC.ACCOUNT.NO_ONLINE_PAYMENT');
    if (payment.reconciliationRequired) return this.i18n.text('PUBLIC.ACCOUNT.RECONCILIATION');
    return (
      (
        {
          CREATED: 'PUBLIC.ACCOUNT.PAYMENT_CREATED',
          PENDING: 'PUBLIC.ACCOUNT.PAYMENT_PENDING',
          SUCCEEDED: 'PUBLIC.ACCOUNT.PAYMENT_SUCCEEDED',
          FAILED: 'PUBLIC.ACCOUNT.PAYMENT_FAILED',
          EXPIRED: 'PUBLIC.ACCOUNT.PAYMENT_EXPIRED',
        } as Record<string, string>
      )[payment.status] ? this.i18n.text(({
        CREATED: 'PUBLIC.ACCOUNT.PAYMENT_CREATED', PENDING: 'PUBLIC.ACCOUNT.PAYMENT_PENDING', SUCCEEDED: 'PUBLIC.ACCOUNT.PAYMENT_SUCCEEDED', FAILED: 'PUBLIC.ACCOUNT.PAYMENT_FAILED', EXPIRED: 'PUBLIC.ACCOUNT.PAYMENT_EXPIRED'
      } as Record<string, string>)[payment.status]) : payment.status
    );
  }

  getPaymentTone(payment?: PaymentLifecycleSummary): string {
    if (!payment) return 'neutral';
    if (payment.reconciliationRequired) return 'warning';
    const tones: Record<string, string> = {
      SUCCEEDED: 'success',
      FAILED: 'danger',
      EXPIRED: 'neutral',
      PENDING: 'warning',
      CREATED: 'info',
    };
    return tones[payment.status] || 'neutral';
  }

  getPaymentIcon(payment?: PaymentLifecycleSummary): string {
    if (!payment) return 'pi pi-wallet';
    if (payment.reconciliationRequired) return 'pi pi-sync';
    return (
      (
        {
          SUCCEEDED: 'pi pi-check-circle',
          FAILED: 'pi pi-times-circle',
          EXPIRED: 'pi pi-clock',
          PENDING: 'pi pi-hourglass',
          CREATED: 'pi pi-wallet',
        } as Record<string, string>
      )[payment.status] || 'pi pi-wallet'
    );
  }

  getPaymentDescription(payment?: PaymentLifecycleSummary): string {
    if (!payment) return this.i18n.text('PUBLIC.ACCOUNT.PAYMENT_ON_SITE');
    if (payment.reconciliationRequired) {
      return this.i18n.text('PUBLIC.ACCOUNT.PAYMENT_RECONCILIATION');
    }
    if (payment.status === 'PENDING' || payment.status === 'CREATED') {
      return this.i18n.text('PUBLIC.ACCOUNT.PAYMENT_WAITING_CALLBACK');
    }
    if (payment.status === 'FAILED')
      return this.i18n.text('PUBLIC.ACCOUNT.PAYMENT_RETRY');
    if (payment.status === 'EXPIRED')
      return this.i18n.text('PUBLIC.ACCOUNT.PAYMENT_SESSION_EXPIRED');
    return this.i18n.text('PUBLIC.ACCOUNT.PAYMENT_PROVIDER_CONFIRMED', { provider: this.getProviderLabel(payment.provider) });
  }

  getRefundLabel(refund: RefundSummary): string {
    return (
      (
        {
          REQUESTED: 'PUBLIC.ACCOUNT.REFUND_REQUESTED',
          PENDING_PROVIDER: 'PUBLIC.ACCOUNT.REFUND_PENDING',
          SUCCEEDED: 'PUBLIC.ACCOUNT.REFUND_SUCCEEDED',
          FAILED: 'PUBLIC.ACCOUNT.REFUND_FAILED',
        } as Record<string, string>
      )[refund.status] ? this.i18n.text(({
        REQUESTED: 'PUBLIC.ACCOUNT.REFUND_REQUESTED', PENDING_PROVIDER: 'PUBLIC.ACCOUNT.REFUND_PENDING', SUCCEEDED: 'PUBLIC.ACCOUNT.REFUND_SUCCEEDED', FAILED: 'PUBLIC.ACCOUNT.REFUND_FAILED'
      } as Record<string, string>)[refund.status]) : refund.status
    );
  }

  getRefundTone(refund: RefundSummary): string {
    const tones: Record<string, string> = {
      REQUESTED: 'info',
      PENDING_PROVIDER: 'warning',
      SUCCEEDED: 'success',
      FAILED: 'danger',
    };
    return tones[refund.status] || 'neutral';
  }

  getRefundIcon(refund: RefundSummary): string {
    return (
      (
        {
          REQUESTED: 'pi pi-file-plus',
          PENDING_PROVIDER: 'pi pi-hourglass',
          SUCCEEDED: 'pi pi-check-circle',
          FAILED: 'pi pi-exclamation-circle',
        } as Record<string, string>
      )[refund.status] || 'pi pi-replay'
    );
  }

  getRefundDescription(refund: RefundSummary): string {
    if (refund.status === 'REQUESTED')
      return this.i18n.text('PUBLIC.ACCOUNT.REFUND_REQUESTED_DESC');
    if (refund.status === 'PENDING_PROVIDER')
      return this.i18n.text('PUBLIC.ACCOUNT.REFUND_PENDING_DESC');
    if (refund.status === 'FAILED') return this.i18n.text('PUBLIC.ACCOUNT.REFUND_FAILED_DESC');
    return this.i18n.text('PUBLIC.ACCOUNT.REFUND_SUCCEEDED_DESC');
  }

  getProviderLabel(provider?: string): string {
    const providers: Record<string, string> = {
      VNPAY: 'VNPay',
      MOMO: 'MoMo',
      ZALOPAY: 'ZaloPay',
      CASH: 'PUBLIC.ACCOUNT.PROVIDER_CASH',
    };
    const key = providers[provider || ''];
    return key ? (key.startsWith('PUBLIC.') ? this.i18n.text(key) : key) : provider || this.i18n.text('PUBLIC.ACCOUNT.PROVIDER_SYSTEM');
  }

  loadProfile(): void {
    this.loading = true;
    this.profileEmpty = false;
    this.profileLoadFailed = false;
    this.error = '';
    this.clientApi
      .getProfile()
      .pipe(
        finalize(() => {
          this.loading = false;
          this.changeDetector.detectChanges();
        }),
      )
      .subscribe({
        next: (profile) => {
          if (!profile) {
            this.user = null;
            this.profileEmpty = true;
            this.profileForm.reset();
            this.changeDetector.detectChanges();
            return;
          }
          this.user = profile;
          this.profileForm.setValue({
            fullName: profile.fullName || '',
            email: profile.email || '',
            phone: profile.phone || '',
            avatarUrl: profile.avatarUrl || '',
          });
          this.changeDetector.detectChanges();
        },
        error: () => {
          this.profileLoadFailed = true;
          this.error = this.i18n.text('PUBLIC.ACCOUNT.PROFILE_LOAD_ERROR');
          this.changeDetector.detectChanges();
        },
      });
  }
}
