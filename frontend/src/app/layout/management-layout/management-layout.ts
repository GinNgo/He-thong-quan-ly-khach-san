import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterModule, RouterOutlet } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { AuthService } from '../../core/services/auth';
import {
  ManagedProperty,
  ManagementApiService,
} from '../../core/services/management-api.service';
import { ActionCode, FunctionCode, PermissionService } from '../../core/services/permission.service';

interface ManagementLink {
  label: string;
  url: string;
  icon: string;
  functionCode?: FunctionCode;
  actionCode?: ActionCode;
  operationalOnly?: boolean;
}

@Component({
  selector: 'app-management-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, RouterOutlet],
  templateUrl: './management-layout.html',
  styleUrls: ['./management-layout.css'],
})
export class ManagementLayout implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private managementApi = inject(ManagementApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  private permissionService = inject(PermissionService);

  readonly navigationGroups: ReadonlyArray<{
    label: string;
    links: ReadonlyArray<ManagementLink>;
  }> = [
    {
      label: 'Vận hành',
      links: [
        { label: 'Tổng quan', url: '/management/dashboard', icon: 'dashboard' },
        { label: 'Cơ sở lưu trú', url: '/management/properties', icon: 'domain' },
        { label: 'Loại phòng', url: '/management/room-types', icon: 'bed', operationalOnly: true },
        { label: 'Phòng vật lý', url: '/management/rooms', icon: 'meeting_room', operationalOnly: true },
        { label: 'Dịch vụ cơ sở', url: '/management/services', icon: 'room_service', functionCode: FunctionCode.HOTEL_SERVICE, actionCode: ActionCode.VIEW, operationalOnly: true },
      ],
    },
    {
      label: 'Tài khoản',
      links: [
        {
          label: 'Cấu hình thanh toán',
          url: '/management/payment-configuration',
          icon: 'account_balance_wallet',
          functionCode: FunctionCode.PROPERTY_PAYMENT_CONFIG,
          actionCode: ActionCode.VIEW,
          operationalOnly: true,
        },
        {
          label: 'Gói dịch vụ',
          url: '/management/billing',
          icon: 'workspace_premium',
        },
      ],
    },
    {
      label: 'Báo cáo',
      links: [
        {
          label: 'Doanh thu cơ sở',
          url: '/management/property-revenue',
          icon: 'monitoring',
          functionCode: FunctionCode.REPORT,
          actionCode: ActionCode.VIEW,
          operationalOnly: true,
        },
        {
          label: 'Nhật ký vận hành',
          url: '/management/audit-log',
          icon: 'history',
          functionCode: FunctionCode.AUDIT_LOG,
          actionCode: ActionCode.VIEW,
        },
      ],
    },
  ];

  username = 'Đối tác';
  pageTitle = 'Tổng quan';
  isSidebarCollapsed = false;
  isMobileViewport = false;
  isMobileSidebarOpen = false;
  isUserMenuOpen = false;
  contextLoading = true;
  contextError = '';
  properties: ManagedProperty[] = [];
  activePropertyId?: number;
  activePropertyOperational = false;

  private subscriptions = new Subscription();

  ngOnInit(): void {
    this.updateViewportState();
    this.subscriptions.add(
      this.authService.currentUser$.subscribe((user) => {
        this.username = user.fullName || user.username || 'Đối tác';
        this.cdr.markForCheck();
      }),
    );

    this.subscriptions.add(
      this.router.events
        .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
        .subscribe((event) => {
          this.updatePageTitle(event.urlAfterRedirects);
          this.closeOverlays();
          this.cdr.markForCheck();
        }),
    );

    this.updatePageTitle(this.router.url);
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    this.loadContext(Number.isInteger(propertyId) && propertyId > 0 ? propertyId : undefined);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  loadContext(propertyId?: number, updateUrl = false): void {
    this.contextLoading = true;
    this.contextError = '';

    this.subscriptions.add(
      this.managementApi.context(propertyId).subscribe({
        next: (context) => {
          this.properties = context.properties;
          this.activePropertyId = context.activePropertyId;
          this.activePropertyOperational = context.activePropertyOperational
            ?? this.activeProperty?.operational
            ?? false;
          this.contextLoading = false;

          if (updateUrl && context.activePropertyId === propertyId) {
            void this.router.navigate([], {
              queryParams: { propertyId: context.activePropertyId },
              queryParamsHandling: 'merge',
            });
          }
          this.cdr.markForCheck();
        },
        error: () => {
          this.properties = [];
          this.activePropertyId = undefined;
          this.activePropertyOperational = false;
          this.contextLoading = false;
          this.contextError = 'Không thể tải danh sách cơ sở.';
          this.cdr.markForCheck();
        },
      }),
    );
  }

  selectProperty(rawValue: string): void {
    const requestedId = Number(rawValue);
    if (!Number.isInteger(requestedId) || requestedId <= 0) return;

    this.loadContext(requestedId, true);
  }

  logout(): void {
    this.authService.logout();
    void this.router.navigate(['/login']);
  }

  toggleSidebar(): void {
    if (this.isMobileViewport) {
      this.isMobileSidebarOpen = !this.isMobileSidebarOpen;
      return;
    }
    this.isSidebarCollapsed = !this.isSidebarCollapsed;
  }

  toggleUserMenu(): void {
    this.isUserMenuOpen = !this.isUserMenuOpen;
  }

  closeMobileNavigation(): void {
    this.isMobileSidebarOpen = false;
  }

  @HostListener('document:keydown.escape')
  closeOverlays(): void {
    this.isMobileSidebarOpen = false;
    this.isUserMenuOpen = false;
  }

  @HostListener('window:resize')
  updateViewportState(): void {
    this.isMobileViewport = typeof window !== 'undefined'
      && typeof window.matchMedia === 'function'
      && window.matchMedia('(max-width: 991px)').matches;
    if (!this.isMobileViewport) this.isMobileSidebarOpen = false;
  }

  get sidebarExpanded(): boolean {
    return this.isMobileViewport ? this.isMobileSidebarOpen : !this.isSidebarCollapsed;
  }

  get activeProperty(): ManagedProperty | undefined {
    return this.properties.find((property) => property.id === this.activePropertyId);
  }

  canViewLink(link: ManagementLink): boolean {
    if (link.operationalOnly && !this.activePropertyOperational) return false;
    return !link.functionCode || this.permissionService.hasPermission(
      link.functionCode,
      link.actionCode ?? ActionCode.VIEW,
    );
  }

  private updatePageTitle(url: string): void {
    const normalizedUrl = url.split('?')[0];
    const links = this.navigationGroups.flatMap((group) => group.links);
    const match = [...links]
      .sort((left, right) => right.url.length - left.url.length)
      .find((link) => normalizedUrl.startsWith(link.url));
    this.pageTitle = match?.label || 'Quản lý đối tác';
  }
}
