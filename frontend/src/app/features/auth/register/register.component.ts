import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { ChangeDetectorRef, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '@app/core/services/auth';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RegisterComponent {
  private authService = inject(AuthService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  registerObj = {
    fullName: '',
    email: '',
    password: '',
    confirmPassword: '',
    countryCode: '+84',
    phone: '',
    terms: false
  };

  errorMessage = '';
  successMessage = '';
  isLoading = false;

  onSubmit() {
    if (this.registerObj.password !== this.registerObj.confirmPassword) {
      this.errorMessage = 'Mật khẩu xác nhận không khớp.';
      return;
    }

    if (!this.registerObj.terms) {
      this.errorMessage = 'Vui lòng đồng ý với Điều khoản Dịch vụ.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';
    this.successMessage = '';

    const payload = {
      username: this.registerObj.email, // Use email as username
      email: this.registerObj.email,
      password: this.registerObj.password,
      fullName: this.registerObj.fullName,
      phone: this.registerObj.countryCode + this.registerObj.phone,
      roles: ["CUSTOMER"]
    };

    this.authService.register(payload).subscribe({
      next: (res) => {
        this.isLoading = false;
        this.successMessage = 'Đăng ký thành công! Vui lòng đăng nhập.';
        this.cdr.markForCheck();
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 2000);
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage = err?.error || 'Đăng ký thất bại. Vui lòng thử lại.';
        this.cdr.markForCheck();
      }
    });
  }
}
