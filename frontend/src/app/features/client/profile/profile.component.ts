import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '@app/core/services/auth';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ClientApiService, ReservationSummary } from '@app/core/services/client-api.service';
import { MessageService } from 'primeng/api';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnInit {
  user: any = null;
  activeTab = 'profile'; // 'profile', 'vouchers', 'bookings'
  bookings: ReservationSummary[] = [];
  bookingsLoading = false;
  bookingsError = '';

  // Mock data for Membership
  membership = {
    tier: 'Gold Member',
    points: 12500,
    nextTier: 'Platinum',
    pointsNeeded: 15000,
    icon: 'workspace_premium',
    color: 'text-yellow-500'
  };

  // Mock data for Vouchers
  vouchers = [
    {
      title: 'Giảm 20% Đặt Phòng Sớm',
      desc: 'Áp dụng cho kỳ nghỉ Hè 2026. Tối đa 500k.',
      code: 'EARLYBIRD20',
      exp: '31/08/2026',
      type: 'discount'
    },
    {
      title: 'Miễn Phí Buffet Sáng',
      desc: 'Tặng buffet sáng dành cho 2 người tại mọi khách sạn LuxeStay.',
      code: 'FREEBREAKFAST',
      exp: '15/12/2026',
      type: 'gift'
    },
    {
      title: 'Giảm 10% Spa & Massage',
      desc: 'Dành riêng cho thành viên hạng Gold trở lên.',
      code: 'GOLDSPA10',
      exp: 'Không thời hạn',
      type: 'exclusive'
    }
  ];

  constructor(
    private authService: AuthService,
    private clientApi: ClientApiService,
    private route: ActivatedRoute,
    private router: Router,
    private messageService: MessageService
  ) {}

  ngOnInit() {
    const authState = this.authService.getAuthState();
    if (authState.isAuthenticated) {
      this.clientApi.getProfile().subscribe({
        next: (profile) => {
          this.user = profile;
          this.membership.points = profile.points || 0;
        },
        error: (err) => {
          this.user = {
            fullName: authState.username,
            email: authState.username,
            phone: '+84 901 234 567',
            dob: '1995-08-15',
            address: 'Quận 1, TP. Hồ Chí Minh'
          };
        }
      });
    } else {
      this.user = {
        fullName: 'Khách',
        email: 'guest@example.com',
        phone: '',
        dob: '',
        address: ''
      };
    }

    this.route.queryParams.subscribe((params) => {
      const tab = params['tab'];
      if (['profile', 'vouchers', 'bookings'].includes(tab)) {
        this.activeTab = tab;
      }
      if (this.activeTab === 'bookings') {
        this.loadBookings();
      }
    });
  }

  get progressPercentage(): number {
    return (this.membership.points / this.membership.pointsNeeded) * 100;
  }

  copyCode(code: string) {
    navigator.clipboard.writeText(code);
    this.messageService.add({severity: 'success', summary: 'Thành công', detail: 'Đã copy mã: ' + code});
  }

  setActiveTab(tab: 'profile' | 'vouchers' | 'bookings') {
    this.activeTab = tab;
    if (tab === 'bookings') {
      this.loadBookings();
    }
  }

  loadBookings() {
    if (!this.authService.isLoggedIn() || this.bookingsLoading) return;

    this.bookingsLoading = true;
    this.bookingsError = '';
    this.clientApi.getMyBookings().subscribe({
      next: (bookings) => {
        this.bookings = bookings;
        this.bookingsLoading = false;
      },
      error: (err) => {
        console.error('Error loading bookings', err);
        this.bookingsError = 'Không thể tải danh sách chuyến đi. Vui lòng thử lại sau.';
        this.bookingsLoading = false;
      },
    });
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/']);
  }

  getStatusLabel(status: string): string {
    const statusMap: Record<string, string> = {
      PENDING: 'Chờ xác nhận',
      CONFIRMED: 'Đã xác nhận',
      CHECKED_IN: 'Đã nhận phòng',
      CHECKED_OUT: 'Đã trả phòng',
      CANCELLED: 'Đã hủy',
    };
    return statusMap[status] || status;
  }
}
