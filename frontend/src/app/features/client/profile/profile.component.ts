import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '@app/core/services/auth';
import { ClientApiService, ReservationSummary, UserContext } from '@app/core/services/client-api.service';
import { UserService } from '@app/core/services/user';

@Component({
  selector: 'app-profile', standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './profile.component.html', styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly clientApi = inject(ClientApiService);
  private readonly userService = inject(UserService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly changeDetector = inject(ChangeDetectorRef);

  user: UserContext | null = null;
  activeTab: 'profile' | 'bookings' = 'profile';
  bookings: ReservationSummary[] = [];
  loading = true; bookingsLoading = false; saving = false; uploading = false;
  error = ''; bookingsError = ''; success = '';

  readonly profileForm = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email]],
    phone: ['', Validators.maxLength(30)],
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
    this.saving = true; this.error = ''; this.success = '';
    this.userService.updateProfile(this.profileForm.getRawValue()).pipe(finalize(() => this.saving = false)).subscribe({
      next: profile => {
        this.user = { ...this.user!, ...profile };
        this.authService.updateCurrentUser(profile);
        this.success = 'Thông tin cá nhân đã được cập nhật.'; this.changeDetector.detectChanges();
      },
      error: () => { this.error = 'Không thể cập nhật thông tin. Vui lòng thử lại.'; this.changeDetector.detectChanges(); }
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
    this.clientApi.getMyBookings().pipe(finalize(() => this.bookingsLoading = false)).subscribe({
      next: data => { this.bookings = data; this.changeDetector.detectChanges(); },
      error: () => { this.bookingsError = 'Không thể tải danh sách chuyến đi.'; this.changeDetector.detectChanges(); }
    });
  }

  logout(): void { this.authService.logout(); this.router.navigate(['/']); }
  avatarError(): void { this.profileForm.patchValue({ avatarUrl: '' }); }
  getStatusLabel(status: string): string { return ({PENDING:'Chờ xác nhận',PENDING_PAYMENT:'Chờ thanh toán',CONFIRMED:'Đã xác nhận',CHECKED_IN:'Đã nhận phòng',CHECKED_OUT:'Đã trả phòng',CANCELLED:'Đã hủy'} as Record<string,string>)[status] || status; }

  private loadProfile(): void {
    this.clientApi.getProfile().pipe(finalize(() => this.loading = false)).subscribe({
      next: profile => {
        this.user = profile;
        this.profileForm.setValue({ fullName: profile.fullName || '', email: profile.email || '', phone: profile.phone || '', avatarUrl: profile.avatarUrl || '' });
        this.changeDetector.detectChanges();
      },
      error: () => { this.error = 'Không thể tải thông tin tài khoản.'; this.changeDetector.detectChanges(); }
    });
  }
}
