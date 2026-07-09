import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '@app/core/services/auth';
import { UserService } from '@app/core/services/user';
import { MessageService } from 'primeng/api';
import { DialogModule } from 'primeng/dialog';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-admin-profile',
  standalone: true,
  imports: [CommonModule, FormsModule, DialogModule, PasswordModule, ButtonModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class AdminProfileComponent implements OnInit {
  user: any = null;
  facility: any = null;
  
  displayPasswordDialog = false;
  passwordData = {
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  };

  private authService = inject(AuthService);
  private userService = inject(UserService);
  private messageService = inject(MessageService);

  ngOnInit() {
    this.loadProfile();
  }

  loadProfile() {
    this.userService.getProfile().subscribe({
      next: (data) => {
        const roles = data.roles ? data.roles.map((r: any) => r.code) : [];
        this.user = {
          ...data,
          role: roles.includes('SUPER_ADMIN') ? 'Quản trị viên Hệ thống' : 'Quản lý Chi nhánh'
        };
        
        if (!roles.includes('SUPER_ADMIN') && (data as any).hotel) {
          this.facility = {
            name: (data as any).hotel.name,
            address: (data as any).hotel.address,
            status: 'Đang hoạt động',
            roomsCount: 'N/A' // Or load rooms count
          };
        }
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: 'Lỗi', detail: 'Không thể tải thông tin hồ sơ'});
      }
    });
  }

  saveProfile() {
    this.userService.updateProfile(this.user).subscribe({
      next: () => {
        this.messageService.add({severity: 'success', summary: 'Thành công', detail: 'Đã cập nhật thông tin thành công!'});
        this.loadProfile();
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: 'Lỗi', detail: 'Không thể cập nhật hồ sơ'});
      }
    });
  }

  changePassword() {
    this.displayPasswordDialog = true;
    this.passwordData = { currentPassword: '', newPassword: '', confirmPassword: '' };
  }

  savePassword() {
    if (this.passwordData.newPassword !== this.passwordData.confirmPassword) {
      this.messageService.add({severity: 'error', summary: 'Lỗi', detail: 'Mật khẩu xác nhận không khớp!'});
      return;
    }
    
    this.userService.changePassword({
      currentPassword: this.passwordData.currentPassword,
      newPassword: this.passwordData.newPassword
    }).subscribe({
      next: () => {
        this.messageService.add({severity: 'success', summary: 'Thành công', detail: 'Đã đổi mật khẩu thành công!'});
        this.displayPasswordDialog = false;
      },
      error: (err) => {
        this.messageService.add({severity: 'error', summary: 'Lỗi', detail: err.error?.message || 'Không thể đổi mật khẩu'});
      }
    });
  }
}
