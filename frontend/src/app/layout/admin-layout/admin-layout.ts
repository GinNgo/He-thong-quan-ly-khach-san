import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, HostListener, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Sidebar } from '../sidebar/sidebar';
import { AuthService } from '../../core/services/auth';
import { AiAssistant } from '../../features/ai-assistant/ai-assistant';
import { NotificationService, AppNotification } from '../../core/services/notification.service';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { Subscription } from 'rxjs';
import { UserService } from '../../core/services/user';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, RouterOutlet, Sidebar, AiAssistant, ToastModule],
  providers: [MessageService],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.css'
})
export class AdminLayout implements OnInit, OnDestroy {
  isSidebarCollapsed = false;
  isMobileSidebarOpen = false;
  isNotificationOpen = false;
  isUserMenuOpen = false;
  globalSearchTerm = '';
  pageTitle = 'Bảng điều khiển';
  currentUserName = 'Admin';
  currentAvatarUrl = '';
  currentRoleLabel = 'Quản trị hệ thống';
  notificationsLoading = true;
  notificationsError = '';

  readonly quickLinks = [
    { label: 'Bảng điều khiển', url: '/admin/dashboard' },
    { label: 'Đặt phòng', url: '/admin/reservations' },
    { label: 'Phòng', url: '/admin/rooms' },
    { label: 'Loại phòng', url: '/admin/room-types' },
    { label: 'Khách hàng', url: '/admin/customers' },
    { label: 'Nhân sự', url: '/admin/users' },
    { label: 'Hóa đơn', url: '/admin/invoices' },
    { label: 'Phân quyền', url: '/admin/role-permissions' },
    { label: 'Cơ sở lưu trú', url: '/admin/properties' },
    { label: 'Nhập cơ sở', url: '/admin/property-imports' },
    { label: 'Khiếu nại cơ sở', url: '/admin/property-claims' },
    { label: 'Gói dịch vụ', url: '/admin/plans' },
    { label: 'Vai trò', url: '/admin/roles' },
    { label: 'Dịch vụ', url: '/admin/services' },
  ];

  notifications: AppNotification[] = [];
  unreadCount = 0;
  private notifSub?: Subscription;
  private authSub?: Subscription;
  private routerSub?: Subscription;
  private apiOrigin = environment.apiUrl.replace(/\/api\/?$/, '');
  private cdr: ChangeDetectorRef;

  constructor(
    private authService: AuthService,
    private userService: UserService,
    private router: Router,
    private notificationService: NotificationService,
    private messageService: MessageService,
    cdr: ChangeDetectorRef
  ) {
    this.cdr = cdr;
    const authState = this.authService.getAuthState();
    this.currentUserName = authState.username || 'Admin';
    this.currentRoleLabel = this.toRoleLabel(authState.roles[0]);
    this.updatePageTitle(this.router.url);

    this.routerSub = this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        this.updatePageTitle(event.urlAfterRedirects);
        this.closeOverlays();
      });
  }

  ngOnInit() {
    this.authSub = this.authService.currentUser$.subscribe((authState) => {
      this.currentUserName = authState.fullName || authState.username || 'Admin';
      this.currentAvatarUrl = authState.avatarUrl;
      this.currentRoleLabel = this.toRoleLabel(authState.roles[0]);
      this.cdr.markForCheck();
    });

    this.userService.getProfile().subscribe({
      next: (profile) => this.authService.updateCurrentUser(profile),
      error: () => this.cdr.markForCheck()
    });

    this.notificationService.connect();
    
    // Tải thông báo cũ
    this.loadNotifications();

    // Lắng nghe thông báo mới realtime
    this.notifSub = this.notificationService.notifications$.subscribe((notif) => {
      this.notifications.unshift(notif);
      this.updateUnreadCount();
      
      // Hiển thị Toast
      this.messageService.add({
        severity: 'info',
        summary: notif.title,
        detail: notif.message,
        life: 5000
      });
      this.cdr.markForCheck();
    });
  }

  ngOnDestroy() {
    this.notifSub?.unsubscribe();
    this.authSub?.unsubscribe();
    this.routerSub?.unsubscribe();
    this.notificationService.disconnect();
  }

  get currentAvatarSrc(): string {
    if (this.currentAvatarUrl.startsWith('data:')) return this.currentAvatarUrl;
    if (this.currentAvatarUrl.startsWith('/')) {
      return `${this.apiOrigin}${this.currentAvatarUrl}`;
    }
    return '';
  }

  get currentUserInitials(): string {
    return (this.currentUserName || 'Admin')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map(part => part[0]?.toUpperCase() || '')
      .join('');
  }

  loadNotifications(): void {
    this.notificationsLoading = true;
    this.notificationsError = '';
    this.notificationService.getAdminNotifications().subscribe({
      next: (data) => {
        this.notifications = data;
        this.updateUnreadCount();
        this.notificationsLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.notificationsLoading = false;
        this.notificationsError = 'Không thể tải thông báo.';
        this.cdr.markForCheck();
      }
    });
  }

  updateUnreadCount(): void {
    this.unreadCount = this.notifications.filter(n => !n.isRead).length;
  }

  markAsRead(notif: AppNotification): void {
    if (!notif.isRead) {
      this.notificationService.markAsRead(notif.id).subscribe(() => {
        notif.isRead = true;
        this.updateUnreadCount();
        this.cdr.markForCheck();
      });
    }
  }

  toggleSidebar(): void {
    if (typeof window !== 'undefined' && window.matchMedia('(max-width: 991px)').matches) {
      this.isMobileSidebarOpen = !this.isMobileSidebarOpen;
      return;
    }
    this.isSidebarCollapsed = !this.isSidebarCollapsed;
  }

  closeMobileNavigation(): void {
    this.isMobileSidebarOpen = false;
  }

  toggleNotifications(): void {
    this.isNotificationOpen = !this.isNotificationOpen;
    this.isUserMenuOpen = false;
  }

  toggleUserMenu(): void {
    this.isUserMenuOpen = !this.isUserMenuOpen;
    this.isNotificationOpen = false;
  }

  executeGlobalSearch(): void {
    const term = this.globalSearchTerm.trim().toLowerCase();
    if (!term) return;

    const match = this.quickLinks.find((link) => link.label.toLowerCase().includes(term));
    if (match) {
      this.router.navigate([match.url]);
      this.globalSearchTerm = '';
    }
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  viewProfile(): void {
    this.isUserMenuOpen = false;
    this.router.navigate(['/admin/profile']);
  }

  @HostListener('document:keydown.escape')
  closeOverlays(): void {
    this.isMobileSidebarOpen = false;
    this.isNotificationOpen = false;
    this.isUserMenuOpen = false;
  }

  private updatePageTitle(url: string): void {
    const normalizedUrl = url.split('?')[0];
    const match = [...this.quickLinks]
      .sort((left, right) => right.url.length - left.url.length)
      .find((link) => normalizedUrl.startsWith(link.url));
    this.pageTitle = match?.label || 'Bảng điều khiển';
  }

  private toRoleLabel(role?: string): string {
    const roleMap: Record<string, string> = {
      SUPER_ADMIN: 'Quản trị hệ thống',
      ADMIN: 'Quản trị viên',
      HOTEL_MANAGER: 'Quản lý khách sạn',
      RECEPTIONIST: 'Lễ tân',
      CUSTOMER: 'Khách hàng',
    };
    return role ? roleMap[role] || role : 'Quản trị hệ thống';
  }
}
