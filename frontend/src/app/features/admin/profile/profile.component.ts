import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '@app/core/services/auth';
import { MessageService } from 'primeng/api';

@Component({
  selector: 'app-admin-profile',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.css']
})
export class AdminProfileComponent implements OnInit {
  user: any = null;
  facility: any = null;

  private authService = inject(AuthService);
  private messageService = inject(MessageService);

  ngOnInit() {
    const authState = this.authService.getAuthState();
    const roles = authState.roles || [];
    
    this.user = {
      username: authState.username,
      fullName: authState.username === 'admin' ? 'System Admin' : 'Hotel Manager',
      email: authState.username + '@luxestay.com',
      phone: '+84 900 111 222',
      role: roles.includes('SUPER_ADMIN') ? 'Quản trị viên Hệ thống' : 'Quản lý Chi nhánh'
    };

    if (!roles.includes('SUPER_ADMIN')) {
      this.facility = {
        name: 'LuxeStay Đà Lạt',
        address: '123 Đường Tình Yêu, Phường 1, Đà Lạt',
        status: 'Đang hoạt động',
        roomsCount: 45
      };
    }
  }

  saveProfile() {
    this.messageService.add({severity: 'success', summary: 'Thành công', detail: 'Đã cập nhật thông tin thành công!'});
  }

  changePassword() {
    this.messageService.add({severity: 'info', summary: 'Thông báo', detail: 'Chức năng đổi mật khẩu đang được tích hợp.'});
  }
}
