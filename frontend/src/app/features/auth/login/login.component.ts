import { Component, ChangeDetectionStrategy, inject } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { AuthService } from '@app/core/services/auth';
import { Router } from '@angular/router';

@Component({
  standalone: true,
  imports: [SharedModule],
  selector: 'app-login',
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./login.component.css'],
})
export class LoginComponent {
  loginObj = {
    username: '',
    password: '',
  };
  errorMessage = '';
  isLoading = false;

  private authService = inject(AuthService);
  private router = inject(Router);

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
            roles: res.roles
          }));
          
          if (res.roles.includes('ADMIN')) {
            this.router.navigate(['/admin/dashboard']);
          } else {
            this.router.navigate(['/']);
          }
        }
        this.isLoading = false;
      },
      error: (err) => {
        this.errorMessage = 'Sai tài khoản hoặc mật khẩu.';
        this.isLoading = false;
      }
    });
  }
}
