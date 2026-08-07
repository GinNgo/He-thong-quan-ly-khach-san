import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, inject, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import {
  FacebookLoginProvider,
  GoogleLoginProvider,
  GoogleSigninButtonModule,
  SOCIAL_AUTH_CONFIG,
  SocialAuthService,
  SocialAuthServiceConfig,
  SocialUser,
} from '@abacritt/angularx-social-login';
import { Subscription } from 'rxjs';

import { AuthResponse, AuthService } from '@app/core/services/auth';
import { AuthLegalCopyService } from '../legal-support/auth-legal-copy.service';
import { SharedModule } from '@app/shared/shared.module';
import { environment } from '../../../../environments/environment';

// Keep the SDK on a currently supported Graph API version; the package default is v10.0.
const facebookLoginOptions = {
  scope: 'email,public_profile',
  locale: 'vi_VN',
  fields: 'name,email,picture,first_name,last_name',
  version: 'v26.0',
};

const loginSocialProviders: SocialAuthServiceConfig['providers'] = [
  ...(environment.socialAuth.googleClientId
    ? [{ id: GoogleLoginProvider.PROVIDER_ID, provider: new GoogleLoginProvider(environment.socialAuth.googleClientId) }]
    : []),
  ...(environment.socialAuth.facebookAppId
    ? [{
        id: FacebookLoginProvider.PROVIDER_ID,
        provider: new FacebookLoginProvider(environment.socialAuth.facebookAppId, facebookLoginOptions),
      }]
    : []),
];

const loginSocialAuthConfig: SocialAuthServiceConfig = {
  autoLogin: false,
  lang: 'vi',
  providers: loginSocialProviders,
};

const adminPortalRoles = new Set(['SUPER_ADMIN', 'ADMIN', 'RECEPTIONIST', 'ACCOUNTANT', 'HOUSEKEEPING']);

@Component({
  standalone: true,
  imports: [SharedModule, RouterModule, GoogleSigninButtonModule],
  providers: [
    SocialAuthService,
    { provide: SOCIAL_AUTH_CONFIG, useValue: loginSocialAuthConfig },
  ],
  selector: 'app-login',
  templateUrl: './login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./login.component.css'],
})
export class LoginComponent implements OnInit, OnDestroy {
  loginObj = {
    username: '',
    password: ''
  };
  errorMessage = '';
  isLoading = false;
  passwordVisible = false;
  socialProviderLoading = '';
  googleButtonWidth = 360;

  readonly isGoogleConfigured = Boolean(environment.socialAuth.googleClientId);
  readonly isFacebookConfigured = Boolean(environment.socialAuth.facebookAppId);
  readonly i18n = inject(AuthLegalCopyService);

  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly socialAuthService = inject(SocialAuthService);
  private socialAuthSubscription?: Subscription;
  private lastSocialIdentity = '';

  returnUrl = '/';

  ngOnInit(): void {
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/';
    if (this.route.snapshot.queryParams['reason'] === 'ACCOUNT_DISABLED') {
      this.errorMessage = 'Tài khoản đã bị tạm ngưng hoặc vô hiệu hóa. Vui lòng liên hệ bộ phận hỗ trợ. / This account is suspended or disabled.';
    }
    this.updateGoogleButtonWidth();
    this.socialAuthSubscription = this.socialAuthService.authState.subscribe((user) => {
      if (user) this.completeSocialLogin(user);
    });

    if (this.authService.isLoggedIn()) {
      const userStr = localStorage.getItem('user');
      let username = '';
      if (userStr) username = JSON.parse(userStr).username;

      const roles = this.authService.getRoles();
      if (this.isAdminPortalAccount(username, roles)) {
        void this.router.navigate([roles.includes('HOUSEKEEPING') ? '/management/housekeeping' : '/admin/dashboard']);
      } else {
        void this.router.navigate(['/']);
      }
    }
  }

  ngOnDestroy(): void {
    this.socialAuthSubscription?.unsubscribe();
  }

  @HostListener('window:resize')
  updateGoogleButtonWidth(): void {
    if (typeof window === 'undefined') return;
    const cardWidth = Math.min(440, Math.max(0, window.innerWidth - 32));
    const cardPadding = window.innerWidth >= 768 ? 64 : 48;
    this.googleButtonWidth = Math.max(200, Math.min(400, cardWidth - cardPadding - 2));
  }

  togglePasswordVisibility(): void {
    this.passwordVisible = !this.passwordVisible;
  }

  async loginWithGoogle(): Promise<void> {
    if (!this.isGoogleConfigured || this.socialProviderLoading) return;

    this.errorMessage = '';
    this.socialProviderLoading = 'google';
    this.cdr.markForCheck();

    try {
      await this.socialAuthService.signIn(GoogleLoginProvider.PROVIDER_ID);
    } catch {
      this.socialProviderLoading = '';
      this.errorMessage = 'Không thể kết nối Google. Vui lòng thử lại.';
      this.cdr.markForCheck();
    }
  }

  async loginWithFacebook(): Promise<void> {
    if (!this.isFacebookConfigured || this.socialProviderLoading) return;

    this.errorMessage = '';
    this.socialProviderLoading = 'facebook';
    this.cdr.markForCheck();

    try {
      await this.socialAuthService.signIn(FacebookLoginProvider.PROVIDER_ID);
    } catch {
      this.socialProviderLoading = '';
      this.errorMessage = 'Không thể kết nối Facebook. Vui lòng thử lại.';
      this.cdr.markForCheck();
    }
  }

  onSubmit(): void {
    if (!this.loginObj.username || !this.loginObj.password) {
      this.errorMessage = 'Vui lòng nhập email và mật khẩu.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login(this.loginObj).subscribe({
      next: (res) => {
        this.applySession(res);
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.errorMessage = error?.error?.code === 'ACCOUNT_DISABLED'
          ? 'Tài khoản đã bị tạm ngưng hoặc vô hiệu hóa. Vui lòng liên hệ bộ phận hỗ trợ. / This account is suspended or disabled.'
          : 'Sai email hoặc mật khẩu.';
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  private completeSocialLogin(user: SocialUser): void {
    const provider = user.provider;
    const identity = `${provider}:${user.id || user.email || ''}`;
    if (!provider || identity === this.lastSocialIdentity) return;

    const request = provider === GoogleLoginProvider.PROVIDER_ID
      ? this.authService.googleLogin(user.idToken || '')
      : provider === FacebookLoginProvider.PROVIDER_ID
        ? this.authService.facebookLogin(user.authToken || '')
        : null;

    if (!request) return;

    this.lastSocialIdentity = identity;
    this.socialProviderLoading = provider.toLowerCase();
    this.errorMessage = '';
    this.cdr.markForCheck();

    request.subscribe({
      next: (res) => {
        this.applySession(res);
        this.socialProviderLoading = '';
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.lastSocialIdentity = '';
        this.socialProviderLoading = '';
        this.errorMessage = error?.error?.code === 'ACCOUNT_DISABLED'
          ? 'Tài khoản đã bị tạm ngưng hoặc vô hiệu hóa. Vui lòng liên hệ bộ phận hỗ trợ. / This account is suspended or disabled.'
          : error?.error?.message
            || error?.error
            || 'Đăng nhập qua nền tảng bên ngoài chưa thành công.';
        this.cdr.markForCheck();
      }
    });
  }

  private applySession(response: AuthResponse): void {
    if (!response?.accessToken) return;

    const roles = Array.isArray(response.roles) ? response.roles : [];
    const permissions = Array.isArray(response.permissions) ? response.permissions : [];
    this.authService.setSession(response.accessToken, {
      id: response.userId ?? response.id,
      username: response.username,
      roles,
      permissions
    });

    if (this.isAdminPortalAccount(response.username, roles)) {
      void this.router.navigate([roles.includes('HOUSEKEEPING') ? '/management/housekeeping' : '/admin/dashboard']);
      return;
    }

    void this.router.navigateByUrl(this.returnUrl);
  }

  private isAdminPortalAccount(username: string, roles: string[]): boolean {
    return username === 'admin' || roles.some(role => adminPortalRoles.has(role));
  }
}
