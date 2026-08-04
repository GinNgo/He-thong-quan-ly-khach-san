import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { finalize, map, of, switchMap } from 'rxjs';
import { AuthService } from '@app/core/services/auth';
import { ClientApiService, ReservationSummary, UserContext } from '@app/core/services/client-api.service';
import { Reservation, ReservationService } from '@app/core/services/reservation.service';
import { AsyncActionCoordinatorService } from '@app/core/services/async-action-coordinator.service';
import { UserService } from '@app/core/services/user';
import { EmailVerificationService } from '@app/core/services/email-verification.service';
import { ReservationAmendmentWorkspaceComponent } from '@app/shared/reservation-amendment/reservation-amendment-workspace.component';

@Component({
  selector: 'app-profile', standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, ReservationAmendmentWorkspaceComponent],
  templateUrl: './profile.component.html', styleUrls: ['./profile.component.css']
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

  user: UserContext | null = null;
  activeTab: 'profile' | 'bookings' = 'profile';
  bookings: ReservationSummary[] = [];
  loading = true; bookingsLoading = false; saving = false; uploading = false;
  profileEmpty = false;
  profileLoadFailed = false;
  emailActionBusy = false;
  cancellingId: number | null = null;
  selectedBooking: Reservation | null = null;
  bookingDetailLoading = false;
  bookingDetailError = '';
  amendmentReservationId: number | null = null;
  error = ''; bookingsError = ''; success = '';
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
    phone: ['', [Validators.maxLength(30), Validators.pattern(/^[0-9+().\\-\\s]*$/)]],
    avatarUrl: ['']
  });

  ngOnInit(): void {
    const routeTab = this.route.snapshot.data['tab'];
    this.activeTab = routeTab === 'bookings' ? 'bookings' : 'profile';
    this.route.queryParams.subscribe(params => {
      if (params['tab'] === 'bookings' || params['tab'] === 'profile') this.activeTab = params['tab'];
      if (this.activeTab === 'bookings') this.loadBookings();
    });
    this.loadProfile();
  }

  get initials(): string {
    return (this.user?.fullName || this.user?.username || 'U').trim().split(/\s+/).slice(-2).map(part => part[0]).join('').toUpperCase();
  }

  setActiveTab(tab: 'profile' | 'bookings'): void {
    this.activeTab = tab;
    this.router.navigate(['/profile'], { queryParams: { tab } });
  }

  saveProfile(): void {
    if (this.profileForm.invalid) { this.profileForm.markAllAsTouched(); return; }
    const value = this.profileForm.getRawValue();
    const currentEmail = this.user?.email || value.email;
    const requestedEmail = value.email.trim().toLowerCase();
    const emailChanged = requestedEmail !== currentEmail.toLowerCase();
    this.saving = true; this.error = ''; this.success = '';
    this.userService.updateProfile({ ...value, email: currentEmail }).pipe(
      switchMap(profile => {
        if (!emailChanged) return of({ profile, pendingEmail: this.user?.pendingEmail });
        return this.emailVerification.requestEmailChange(requestedEmail).pipe(
          map(dispatch => ({ profile, pendingEmail: dispatch.pendingEmail || requestedEmail }))
        );
      }),
      finalize(() => this.saving = false)
    ).subscribe({
      next: ({ profile, pendingEmail }) => {
        this.user = { ...this.user!, ...profile, pendingEmail };
        this.profileForm.patchValue({ email: profile.email });
        this.authService.updateCurrentUser(profile);
        this.success = emailChanged
          ? this.emailVerificationText.changePending
          : 'Thông tin cá nhân đã được cập nhật.';
        this.changeDetector.detectChanges();
      },
      error: () => { this.error = 'Không thể cập nhật thông tin. Vui lòng thử lại.'; this.changeDetector.detectChanges(); }
    });
  }

  resendEmailVerification(): void {
    if (this.emailActionBusy) return;
    this.emailActionBusy = true; this.error = ''; this.success = '';
    this.emailVerification.resend().pipe(finalize(() => {
      this.emailActionBusy = false;
      this.changeDetector.detectChanges();
    })).subscribe({
      next: dispatch => {
        if (this.user && dispatch.pendingEmail) {
          this.user = { ...this.user, pendingEmail: dispatch.pendingEmail };
        }
        this.success = dispatch.alreadyVerified
          ? this.emailVerificationText.alreadyVerified
          : this.emailVerificationText.sent;
      },
      error: () => { this.error = this.emailVerificationText.sendError; }
    });
  }

  uploadAvatar(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/') || file.size > 5 * 1024 * 1024) {
      this.error = 'Chỉ chấp nhận ảnh nhỏ hơn 5 MB.'; input.value = ''; return;
    }
    this.uploading = true; this.error = '';
    this.userService.uploadAvatar(file).pipe(finalize(() => this.uploading = false)).subscribe({
      next: response => { this.profileForm.patchValue({ avatarUrl: response.url }); this.saveProfile(); },
      error: () => { this.error = 'Không thể tải ảnh đại diện.'; this.changeDetector.detectChanges(); }
    });
  }

  loadBookings(): void {
    if (this.bookingsLoading) return;
    this.bookingsLoading = true; this.bookingsError = '';
    this.clientApi.getMyBookings().pipe(finalize(() => {
      this.bookingsLoading = false;
      this.changeDetector.detectChanges();
    })).subscribe({
      next: data => { this.bookings = data; this.changeDetector.detectChanges(); },
      error: () => { this.bookingsError = 'Không thể tải danh sách chuyến đi.'; this.changeDetector.detectChanges(); }
    });
  }

  cancelBooking(id: number): void {
    if (this.cancellingId !== null) return;
    if (!confirm('Bạn có chắc chắn muốn hủy đặt phòng này? Khoản đã thanh toán sẽ được hoàn 100% và điểm thưởng từ đặt phòng sẽ bị thu hồi.')) return;

    this.cancellingId = id;
    this.bookingsError = '';
    this.success = '';
    const idempotencyKey = this.getCancellationKey(id);
    this.actionCoordinator
      .run(`reservation:cancel:${id}`, () => this.reservationService.cancelMyReservation(id, idempotencyKey)).pipe(
      finalize(() => {
        this.cancellingId = null;
        this.changeDetector.detectChanges();
      })
    ).subscribe({
      next: updated => {
        this.bookings = this.bookings.map(booking =>
          booking.id === id ? { ...booking, status: updated.status || 'CANCELLED' } : booking
        );
        this.success = 'Đã hủy đặt phòng và xử lý hoàn tiền.';
        sessionStorage.removeItem(`hotel:reservation-cancel:${id}`);
        this.loadProfile();
      },
      error: err => {
        this.bookingsError = err.error?.message || 'Không thể hủy đặt phòng. Vui lòng thử lại.';
      }
    });
  }

  viewBooking(id: number): void {
    if (this.bookingDetailLoading) return;
    this.bookingDetailLoading = true;
    this.bookingDetailError = '';
    this.selectedBooking = null;
    this.reservationService.getReservationById(id).pipe(finalize(() => {
      this.bookingDetailLoading = false;
      this.changeDetector.detectChanges();
    })).subscribe({
      next: booking => this.selectedBooking = booking,
      error: () => this.bookingDetailError = 'Không thể tải chi tiết chuyến đi.',
    });
  }

  openAmendment(id: number): void {
    this.amendmentReservationId = id;
  }

  closeAmendment(): void {
    this.amendmentReservationId = null;
  }

  handleAmendmentApplied(id: number): void {
    this.success = 'Đã cập nhật đặt phòng theo báo giá mới.';
    this.amendmentReservationId = null;
    this.loadBookings();
    this.viewBooking(id);
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

  logout(): void { this.authService.logout(); this.router.navigate(['/']); }
  avatarError(): void { this.profileForm.patchValue({ avatarUrl: '' }); }
  getStatusLabel(status: string): string { return ({PENDING:'Chờ xác nhận',PENDING_PAYMENT:'Chờ thanh toán',CONFIRMED:'Đã xác nhận',CHECKED_IN:'Đã nhận phòng',CHECKED_OUT:'Đã trả phòng',CANCELLED:'Đã hủy'} as Record<string,string>)[status] || status; }
  getEventLabel(eventType: string): string { return ({RESERVATION_CREATED:'Đã tạo đặt phòng',RESERVATION_STATUS_CHANGED:'Đã đổi trạng thái',ROOMS_ASSIGNED:'Đã xếp phòng cụ thể',ROOMS_REASSIGNED:'Đã gán lại phòng',ROOMS_RELEASED:'Đã giải phóng phòng'} as Record<string,string>)[eventType] || eventType; }

  assignedRoomNumbers(booking: Reservation): string[] {
    return [...new Set((booking.details || []).flatMap(detail => detail.assignedRoomNumbers || []))];
  }

  loadProfile(): void {
    this.loading = true;
    this.profileEmpty = false;
    this.profileLoadFailed = false;
    this.error = '';
    this.clientApi.getProfile().pipe(finalize(() => {
      this.loading = false;
      this.changeDetector.detectChanges();
    })).subscribe({
      next: profile => {
        if (!profile) {
          this.user = null;
          this.profileEmpty = true;
          this.profileForm.reset();
          this.changeDetector.detectChanges();
          return;
        }
        this.user = profile;
        this.profileForm.setValue({ fullName: profile.fullName || '', email: profile.email || '', phone: profile.phone || '', avatarUrl: profile.avatarUrl || '' });
        this.changeDetector.detectChanges();
      },
      error: () => {
        this.profileLoadFailed = true;
        this.error = 'Không thể tải thông tin tài khoản.';
        this.changeDetector.detectChanges();
      }
    });
  }
}
