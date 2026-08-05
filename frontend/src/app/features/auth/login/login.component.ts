import { Component, ChangeDetectionStrategy, inject, ChangeDetectorRef, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { AuthService } from '@app/core/services/auth';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import {
  ACCOUNT_DISABLED_CODE,
  ACCOUNT_DISABLED_MESSAGE,
  authenticationErrorMessage,
} from '@app/core/auth/account-status-error';
import { isBookingReturnUrl, safeClientReturnUrl } from '@app/core/auth/client-return-url';
import { FocusOnErrorDirective } from '../../../shared/directives/focus-management.directive';

@Component({
  standalone: true,
  imports: [SharedModule, RouterModule, FocusOnErrorDirective],
  selector: 'app-login',
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./login.component.css'],
})
export class LoginComponent implements OnInit {
  loginObj = {
    username: '',
    password: ''
  };
  errorMessage = '';
  isLoading = false;

  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private cdr = inject(ChangeDetectorRef);

  returnUrl: string = '/';

  ngOnInit() {
    this.returnUrl = safeClientReturnUrl(this.route.snapshot.queryParams['returnUrl']);
    if (this.route.snapshot.queryParams['reason'] === ACCOUNT_DISABLED_CODE) {
      this.errorMessage = ACCOUNT_DISABLED_MESSAGE;
    }

    if (this.authService.isLoggedIn()) {
      const userStr = localStorage.getItem('user');
      let username = '';
      if (userStr) {
        username = JSON.parse(userStr).username;
      }
      const roles = this.authService.getRoles();
      this.navigateAfterAuthentication(username, roles);
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
          
          this.navigateAfterAuthentication(res.username, res.roles);
        }
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.errorMessage = authenticationErrorMessage(error, 'Sai tài khoản hoặc mật khẩu.');
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  private navigateAfterAuthentication(username: string, roles: string[]): void {
    if (isBookingReturnUrl(this.returnUrl) && !roles.includes('CUSTOMER')) {
      this.router.navigate(['/403'], { queryParams: { reason: 'CUSTOMER_REQUIRED' } });
      return;
    }
    if (username === 'admin' || roles.includes('SUPER_ADMIN') || roles.includes('ADMIN')) {
      this.router.navigate(['/admin/dashboard']);
      return;
    }
    if (this.returnUrl !== '/') {
      this.router.navigateByUrl(this.returnUrl);
      return;
    }
    this.router.navigate(['/']);
  }
}
