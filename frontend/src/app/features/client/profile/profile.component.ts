import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '@app/core/services/auth';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class ProfileComponent implements OnInit {
  user: any = null;
  activeTab = 'profile'; // 'profile', 'vouchers', 'bookings'

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

  constructor(private authService: AuthService) {}

  ngOnInit() {
    // Mock user data or get from auth service
    const authState = this.authService.getAuthState();
    if (authState.isAuthenticated) {
      this.user = {
        fullName: 'Lina Isan',
        email: authState.username,
        phone: '+84 901 234 567',
        dob: '1995-08-15',
        address: 'Quận 1, TP. Hồ Chí Minh'
      };
    } else {
      this.user = {
        fullName: 'Lina Isan',
        email: 'lina@example.com',
        phone: '+84 901 234 567',
        dob: '1995-08-15',
        address: 'Quận 1, TP. Hồ Chí Minh'
      };
    }
  }

  get progressPercentage(): number {
    return (this.membership.points / this.membership.pointsNeeded) * 100;
  }

  copyCode(code: string) {
    navigator.clipboard.writeText(code);
    alert('Đã copy mã: ' + code);
  }
}
