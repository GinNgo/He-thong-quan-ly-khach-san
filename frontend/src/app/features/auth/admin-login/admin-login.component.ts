import { Component, ChangeDetectionStrategy, inject, ChangeDetectorRef, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { AuthService } from '@app/core/services/auth';
import { Router } from '@angular/router';

@Component({
  standalone: true,
  imports: [SharedModule],
  selector: 'app-admin-login',
  templateUrl: './admin-login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./admin-login.component.css'],
})
export class AdminLoginComponent implements OnInit {
  loginObj = {
    username: '',
    password: '',
    rememberMe: false
  };
  errorMessage = '';
  isLoading = false;

  private authService = inject(AuthService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);

  ngOnInit() {
    if (this.authService.isLoggedIn()) {
      const userStr = localStorage.getItem('user');
      let username = '';
      if (userStr) {
        username = JSON.parse(userStr).username;
      }
      const roles = this.authService.getRoles();
      if (username === 'admin' || roles.includes('SUPER_ADMIN') || roles.includes('ADMIN')) {
        this.router.navigate(['/admin/dashboard']);
      } else {
        this.router.navigate(['/']);
      }
    }
  }

  onSubmit() {
    if (!this.loginObj.username || !this.loginObj.password) {
      this.errorMessage = 'Vui lòng nhập tài khoản và mật khẩu.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login(this.loginObj).subscribe({
      next: (res) => {
        if (res && res.accessToken) {
          localStorage.setItem('token', res.accessToken);
          localStorage.setItem('user', JSON.stringify({
            username: res.username,
            roles: res.roles,
            permissions: res.permissions
          }));
          
          if (res.username === 'admin' || res.roles.includes('SUPER_ADMIN') || res.roles.includes('ADMIN')) {
            this.router.navigate(['/admin/dashboard']);
          } else {
            this.router.navigate(['/']);
          }
        }
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.errorMessage = 'Sai tài khoản hoặc mật khẩu.';
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }
}
