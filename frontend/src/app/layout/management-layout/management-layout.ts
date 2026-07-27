import { CommonModule } from '@angular/common';
import { Component, HostListener, OnDestroy, OnInit, inject } from '@angular/core';
import { ActivatedRoute, NavigationEnd, Router, RouterModule, RouterOutlet } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { AuthService } from '../../core/services/auth';
import {
  ManagedProperty,
  ManagementApiService,
} from '../../core/services/management-api.service';

interface ManagementLink {
  label: string;
  url: string;
  icon: string;
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

  readonly navigationGroups: ReadonlyArray<{
    label: string;
    links: ReadonlyArray<ManagementLink>;
  }> = [
    {
      label: 'Vận hành',
      links: [
        { label: 'Tổng quan', url: '/management/dashboard', icon: 'dashboard' },
        { label: 'Cơ sở lưu trú', url: '/management/properties', icon: 'domain' },
        { label: 'Loại phòng', url: '/management/room-types', icon: 'bed' },
        { label: 'Phòng vật lý', url: '/management/rooms', icon: 'meeting_room' },
      ],
    },
    {
      label: 'Tài khoản',
      links: [
        {
          label: 'Gói dịch vụ',
          url: '/management/billing',
          icon: 'workspace_premium',
        },
      ],
    },
  ];

  username = 'Đối tác';
  pageTitle = 'Tổng quan';
  isSidebarCollapsed = false;
  isMobileSidebarOpen = false;
  isUserMenuOpen = false;
  contextLoading = true;
  contextError = '';
  properties: ManagedProperty[] = [];
  activePropertyId?: number;

  private subscriptions = new Subscription();

  ngOnInit(): void {
    this.subscriptions.add(
      this.authService.currentUser$.subscribe((user) => {
        this.username = user.fullName || user.username || 'Đối tác';
      }),
    );

    this.subscriptions.add(
      this.router.events
        .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
        .subscribe((event) => {
          this.updatePageTitle(event.urlAfterRedirects);
          this.closeOverlays();
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
          this.contextLoading = false;

          if (updateUrl && context.activePropertyId === propertyId) {
            void this.router.navigate([], {
              queryParams: { propertyId: context.activePropertyId },
              queryParamsHandling: 'merge',
            });
          }
        },
        error: () => {
          this.properties = [];
          this.activePropertyId = undefined;
          this.contextLoading = false;
          this.contextError = 'Không thể tải danh sách cơ sở.';
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
    if (typeof window !== 'undefined' && window.matchMedia('(max-width: 991px)').matches) {
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

  private updatePageTitle(url: string): void {
    const normalizedUrl = url.split('?')[0];
    const links = this.navigationGroups.flatMap((group) => group.links);
    const match = [...links]
      .sort((left, right) => right.url.length - left.url.length)
      .find((link) => normalizedUrl.startsWith(link.url));
    this.pageTitle = match?.label || 'Quản lý đối tác';
  }
}