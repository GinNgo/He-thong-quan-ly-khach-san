import { Component, ChangeDetectionStrategy, inject, ChangeDetectorRef, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { AuthService } from '@app/core/services/auth';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';

@Component({
  standalone: true,
  imports: [SharedModule, RouterModule],
  selector: 'app-login',
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./login.component.css'],
})
export class LoginComponent implements OnInit {
  loginObj = {
    username: '',
    password: '',
    rememberMe: false
  };
  errorMessage = '';
  isLoading = false;

  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);

  returnUrl: string = '/';

  ngOnInit() {
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/';

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
          this.authService.setSession(res.accessToken, {
            username: res.username,
            roles: res.roles,
            permissions: res.permissions
          });
          
          if (res.username === 'admin' || res.roles.includes('SUPER_ADMIN') || res.roles.includes('ADMIN')) {
            this.router.navigate(['/admin/dashboard']);
          } else {
            this.router.navigateByUrl(this.returnUrl);
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
