import { Component, ChangeDetectionStrategy, inject, ChangeDetectorRef, OnInit } from '@angular/core';
import { SharedModule } from '@app/shared/shared.module';
import { AuthService } from '@app/core/services/auth';
import { AuthLegalCopyService } from '../legal-support/auth-legal-copy.service';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ActionCode, FunctionCode } from '@app/core/services/permission.service';
import { AuthPermission } from '@app/core/services/auth';
import {
  ACCOUNT_DISABLED_CODE,
  ACCOUNT_DISABLED_MESSAGE,
  authenticationErrorMessage,
} from '@app/core/auth/account-status-error';

@Component({
  standalone: true,
  imports: [SharedModule, RouterModule],
  selector: 'app-admin-login',
  templateUrl: './admin-login.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['./admin-login.component.css'],
})
export class AdminLoginComponent implements OnInit {
  private static readonly DEFAULT_PORTAL_URL = '/admin/dashboard';

  readonly i18n = inject(AuthLegalCopyService);
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
  private returnUrl = AdminLoginComponent.DEFAULT_PORTAL_URL;

  ngOnInit(): void {
    this.returnUrl = this.resolvePortalReturnUrl(this.route.snapshot.queryParams['returnUrl']);
    if (this.route.snapshot.queryParams['reason'] === ACCOUNT_DISABLED_CODE) {
      this.errorMessage = ACCOUNT_DISABLED_MESSAGE;
    }
    if (this.authService.isLoggedIn()) {
      this.redirectToPortal();
    }
  }

  onSubmit(): void {
    if (!this.loginObj.username || !this.loginObj.password) {
      this.errorMessage = 'Vui lòng nhập tài khoản và mật khẩu.';
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    this.authService.login(this.loginObj).subscribe({
      next: (res) => {
        if (res && res.accessToken) {
          const roles: string[] = Array.isArray(res.roles) ? res.roles : [];
          this.authService.setSession(res.accessToken, {
            id: res.userId ?? res.id,
            username: res.username,
            roles,
            permissions: Array.isArray(res.permissions) ? res.permissions : []
          });

          this.redirectToPortal();
        }
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.errorMessage = authenticationErrorMessage(err, 'Sai tài khoản hoặc mật khẩu.');
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  private redirectToPortal(): void {
    const authState = this.authService.getAuthState();
    void this.router.navigateByUrl(this.resolveAuthorizedPortalUrl(
      this.returnUrl,
      authState.roles,
      authState.permissions,
    ));
  }

  private resolveAuthorizedPortalUrl(
    requestedUrl: string,
    roles: string[],
    permissions: AuthPermission[],
  ): string {
    const isAdministrator = roles.some((role) => role === 'SUPER_ADMIN' || role === 'ADMIN');
    if (isAdministrator) return requestedUrl;

    const requestedPath = requestedUrl.split(/[?#]/, 1)[0];
    const requirement = this.portalRequirement(requestedPath);
    if (requirement && this.hasPermission(permissions, requirement.functionCode, requirement.actionCode)) {
      return requestedUrl;
    }

    const landingPages = [
      { url: '/admin/dashboard', functionCode: FunctionCode.REPORT, actionCode: ActionCode.VIEW },
      { url: '/admin/reservations', functionCode: FunctionCode.RESERVATION, actionCode: ActionCode.VIEW },
      { url: '/admin/rooms', functionCode: FunctionCode.ROOM, actionCode: ActionCode.VIEW },
      { url: '/admin/invoices', functionCode: FunctionCode.INVOICE, actionCode: ActionCode.VIEW },
      { url: '/admin/customers', functionCode: FunctionCode.CUSTOMER, actionCode: ActionCode.VIEW },
    ];
    return landingPages.find((page) => this.hasPermission(
      permissions,
      page.functionCode,
      page.actionCode,
    ))?.url ?? '/403';
  }

  private portalRequirement(path: string): { functionCode: FunctionCode; actionCode: ActionCode } | null {
    const requirements: Array<{
      prefix: string;
      functionCode: FunctionCode;
      actionCode: ActionCode;
    }> = [
      { prefix: '/admin/services', functionCode: FunctionCode.HOTEL_SERVICE, actionCode: ActionCode.VIEW },
      { prefix: '/admin/reservations/create', functionCode: FunctionCode.RESERVATION, actionCode: ActionCode.CREATE },
      { prefix: '/admin/reservations', functionCode: FunctionCode.RESERVATION, actionCode: ActionCode.VIEW },
      { prefix: '/admin/rooms', functionCode: FunctionCode.ROOM, actionCode: ActionCode.VIEW },
      { prefix: '/admin/room-types', functionCode: FunctionCode.ROOM_TYPE, actionCode: ActionCode.VIEW },
      { prefix: '/admin/invoices', functionCode: FunctionCode.INVOICE, actionCode: ActionCode.VIEW },
      { prefix: '/admin/customers', functionCode: FunctionCode.CUSTOMER, actionCode: ActionCode.VIEW },
      { prefix: '/admin/dashboard', functionCode: FunctionCode.REPORT, actionCode: ActionCode.VIEW },
    ];
    const match = requirements.find((item) => path === item.prefix || path.startsWith(`${item.prefix}/`));
    return match
      ? { functionCode: match.functionCode, actionCode: match.actionCode }
      : null;
  }

  private hasPermission(permissions: AuthPermission[], functionCode: FunctionCode, actionCode: ActionCode): boolean {
    const permission = permissions.find((item) => item.function === functionCode);
    return permission !== undefined && (permission.actionMask & actionCode) === actionCode;
  }

  private resolvePortalReturnUrl(value: unknown): string {
    if (typeof value !== 'string' || !value.startsWith('/')) {
      return AdminLoginComponent.DEFAULT_PORTAL_URL;
    }

    const path = value.split(/[?#]/, 1)[0];
    const isPortalRoute = path === '/admin'
      || path.startsWith('/admin/')
      || path === '/management'
      || path.startsWith('/management/');
    const isLoginRoute = path === '/admin/login';

    return isPortalRoute && !isLoginRoute
      ? value
      : AdminLoginComponent.DEFAULT_PORTAL_URL;
  }
}
