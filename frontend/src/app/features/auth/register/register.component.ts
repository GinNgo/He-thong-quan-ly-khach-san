import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';

import { ChangeDetectorRef, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '@app/core/services/auth';
import { isPasswordLengthValid, PASSWORD_POLICY } from '@app/core/auth/password-policy';

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
  readonly passwordPolicy = PASSWORD_POLICY;
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
    if (!isPasswordLengthValid(this.registerObj.password)) {
      this.errorMessage = `Mật khẩu phải có từ ${PASSWORD_POLICY.minLength} đến ${PASSWORD_POLICY.maxLength} ký tự.`;
      return;
    }

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

    const normalizedEmail = this.registerObj.email.trim().toLowerCase();
    const normalizedFullName = this.registerObj.fullName.trim().replace(/\s+/g, ' ');

    const payload = {
      username: normalizedEmail, // Registration identities are normalized server-side too.
      email: normalizedEmail,
      password: this.registerObj.password,
      fullName: normalizedFullName,
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
        const apiError = err?.error;
        this.errorMessage = apiError?.fieldErrors?.username
          || apiError?.fieldErrors?.email
          || apiError?.message
          || (typeof apiError === 'string' ? apiError : 'Đăng ký thất bại. Vui lòng thử lại.');
        this.cdr.markForCheck();
      }
    });
  }
}
