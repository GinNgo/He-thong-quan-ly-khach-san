import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterModule, RouterOutlet } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { AuthService } from '../../core/services/auth';
import {
  ManagedProperty,
  ManagementApiService,
} from '../../core/services/management-api.service';
import { ManagementPropertyContextService } from '../../core/services/management-property-context.service';
import { ActionCode, FunctionCode, PermissionService } from '../../core/services/permission.service';
import { RouteFocusTargetDirective } from '../../shared/directives/focus-management.directive';

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
  imports: [CommonModule, RouterModule, RouterOutlet, RouteFocusTargetDirective],
  templateUrl: './management-layout.html',
  styleUrls: ['./management-layout.css'],
})
export class ManagementLayout implements OnInit, OnDestroy {
  @ViewChild('navigationTrigger') private navigationTrigger?: ElementRef<HTMLButtonElement>;
  @ViewChild('profileTrigger') private profileTrigger?: ElementRef<HTMLButtonElement>;
  private authService = inject(AuthService);
  private managementApi = inject(ManagementApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  private permissionService = inject(PermissionService);
  private propertyContext = inject(ManagementPropertyContextService);

  readonly navigationGroups: ReadonlyArray<{
    label: string;
    links: ReadonlyArray<ManagementLink>;
  }> = [
    {
      label: 'Vận hành',
      links: [
<<<<<<< HEAD
        { label: 'Tổng quan', url: '/management/dashboard', icon: 'dashboard', functionCode: FunctionCode.HOTEL, actionCode: ActionCode.VIEW },
        { label: 'Cơ sở lưu trú', url: '/management/properties', icon: 'domain', functionCode: FunctionCode.HOTEL, actionCode: ActionCode.VIEW },
        { label: 'Loại phòng', url: '/management/room-types', icon: 'bed', functionCode: FunctionCode.ROOM_TYPE, actionCode: ActionCode.VIEW, operationalOnly: true },
        { label: 'Phòng vật lý', url: '/management/rooms', icon: 'meeting_room', functionCode: FunctionCode.ROOM, actionCode: ActionCode.VIEW, operationalOnly: true },
        { label: 'Dịch vụ cơ sở', url: '/management/services', icon: 'room_service', functionCode: FunctionCode.HOTEL_SERVICE, actionCode: ActionCode.VIEW, operationalOnly: true },
=======
        { label: 'Tổng quan', url: '/management/dashboard', icon: 'dashboard' },
        { label: 'Cơ sở lưu trú', url: '/management/properties', icon: 'domain' },
        { label: 'Loại phòng', url: '/management/room-types', icon: 'bed', operationalOnly: true },
        { label: 'Danh sách phòng', url: '/management/rooms', icon: 'meeting_room', operationalOnly: true },
        {
          label: 'Dịch vụ lưu trú',
          url: '/management/services',
          icon: 'room_service',
          functionCode: FunctionCode.HOTEL_SERVICE,
          actionCode: ActionCode.VIEW,
          operationalOnly: true,
        },
>>>>>>> codex/ui-functional-audit-polish
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
          label: 'Hoàn tiền đặt phòng',
          url: '/management/refunds',
          icon: 'currency_exchange',
          functionCode: FunctionCode.PROPERTY_REFUND,
          actionCode: ActionCode.APPROVE,
          operationalOnly: true,
        },
        {
          label: 'Gói phần mềm',
          url: '/management/billing',
          icon: 'workspace_premium',
          functionCode: FunctionCode.PLATFORM_BILLING,
          actionCode: ActionCode.VIEW,
        },
        {
          label: 'Chủ sở hữu',
          url: '/management/ownership',
          icon: 'group',
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
        { label: 'Nhat ky tai chinh', url: '/management/financial-audit', icon: 'policy', functionCode: FunctionCode.AUDIT_LOG, actionCode: ActionCode.VIEW },
      ],
    },
    {
      label: 'Housekeeping',
      links: [
        {
          label: 'Hàng đợi dọn phòng',
          url: '/management/housekeeping',
          icon: 'cleaning_services',
          functionCode: FunctionCode.HOUSEKEEPING,
          actionCode: ActionCode.VIEW,
          operationalOnly: true,
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
  private contextRequestSequence = 0;

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
          this.closeOverlays(false);
          this.cdr.markForCheck();
        }),
    );

    this.updatePageTitle(this.router.url);
    this.subscriptions.add(this.propertyContext.propertyId$.subscribe(propertyId => this.loadContext(propertyId)));
    const propertyId = Number(this.route.snapshot.queryParamMap.get('propertyId'));
    if (Number.isInteger(propertyId) && propertyId > 0) this.propertyContext.select(propertyId);
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  loadContext(propertyId?: number, updateUrl = false): void {
    const requestSequence = ++this.contextRequestSequence;
    this.contextLoading = true;
    this.contextError = '';

    this.subscriptions.add(
      this.managementApi.context(propertyId).subscribe({
        next: (context) => {
          if (requestSequence !== this.contextRequestSequence) return;
          this.properties = context.properties;
          this.activePropertyId = context.activePropertyId;
          this.activePropertyOperational = context.activePropertyOperational
            ?? this.activeProperty?.operational
            ?? (this.activeProperty?.approvalStatus === 'APPROVED'
              && this.activeProperty?.operationStatus === 'ACTIVE');
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
          if (requestSequence !== this.contextRequestSequence) return;
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
    this.propertyContext.select(requestedId);
    void this.router.navigate([], {
      queryParams: { propertyId: requestedId },
      queryParamsHandling: 'merge',
    });
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
    const restoreFocus = this.isUserMenuOpen;
    this.isUserMenuOpen = !this.isUserMenuOpen;
    if (restoreFocus) queueMicrotask(() => this.profileTrigger?.nativeElement.focus());
  }

  closeMobileNavigation(): void {
    this.isMobileSidebarOpen = false;
  }

  @HostListener('document:keydown.escape')
  closeOverlays(restoreFocus = true): void {
    const navigationWasOpen = this.isMobileSidebarOpen;
    const profileWasOpen = this.isUserMenuOpen;
    this.isMobileSidebarOpen = false;
    this.isUserMenuOpen = false;
    if (!restoreFocus) return;
    if (profileWasOpen) queueMicrotask(() => this.profileTrigger?.nativeElement.focus());
    else if (navigationWasOpen) queueMicrotask(() => this.navigationTrigger?.nativeElement.focus());
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

  statusLabel(status?: string): string {
    return ({
      ACTIVE: 'Đang hoạt động',
      INACTIVE: 'Không hoạt động',
      SUSPENDED: 'Tạm ngưng',
      DRAFT: 'Bản nháp',
      PENDING_APPROVAL: 'Chờ duyệt',
      APPROVED: 'Đã duyệt',
      REJECTED: 'Bị từ chối',
    } as Record<string, string>)[status || ''] || status || 'Chưa xác định';
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
