import { CommonModule } from '@angular/common';
import { Component, OnInit, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, RouterOutlet, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Sidebar } from '../sidebar/sidebar';
import { AuthService } from '../../core/services/auth';
import { AiAssistant } from '../../features/ai-assistant/ai-assistant';
import { NotificationService, AppNotification } from '../../core/services/notification.service';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterOutlet, Sidebar, AiAssistant, ToastModule],
  providers: [MessageService],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.css'
})
export class AdminLayout implements OnInit, OnDestroy {
  isSidebarCollapsed = false;
  isNotificationOpen = false;
  isUserMenuOpen = false;
  globalSearchTerm = '';
  pageTitle = 'Bảng điều khiển';
  currentUserName = 'Admin';
  currentRoleLabel = 'Quản trị hệ thống';

  quickLinks = [
    { label: 'Bảng điều khiển', url: '/admin/dashboard' },
    { label: 'Đặt phòng', url: '/admin/reservations' },
    { label: 'Phòng', url: '/admin/rooms' },
    { label: 'Loại phòng', url: '/admin/room-types' },
    { label: 'Khách hàng', url: '/admin/customers' },
    { label: 'Nhân sự', url: '/admin/users' },
    { label: 'Hóa đơn', url: '/admin/invoices' },
    { label: 'Phân quyền', url: '/admin/role-permissions' },
  ];

  notifications: AppNotification[] = [];
  unreadCount = 0;
  private notifSub?: Subscription;

  constructor(
    private authService: AuthService, 
    private router: Router,
    private notificationService: NotificationService,
    private messageService: MessageService
  ) {
    const authState = this.authService.getAuthState();
    this.currentUserName = authState.username || 'Admin';
    this.currentRoleLabel = this.toRoleLabel(authState.roles[0]);
    this.updatePageTitle(this.router.url);

    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => this.updatePageTitle(event.urlAfterRedirects));
  }

  ngOnInit() {
    this.notificationService.connect();
    
    // Tải thông báo cũ
    this.notificationService.getAdminNotifications().subscribe({
      next: (data) => {
        this.notifications = data;
        this.updateUnreadCount();
      }
    });

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
    });
  }

  ngOnDestroy() {
    this.notifSub?.unsubscribe();
    this.notificationService.disconnect();
  }

  updateUnreadCount() {
    this.unreadCount = this.notifications.filter(n => !n.isRead).length;
  }

  markAsRead(notif: AppNotification) {
    if (!notif.isRead) {
      this.notificationService.markAsRead(notif.id).subscribe(() => {
        notif.isRead = true;
        this.updateUnreadCount();
      });
    }
  }

  toggleSidebar() {
    this.isSidebarCollapsed = !this.isSidebarCollapsed;
  }

  toggleNotifications() {
    this.isNotificationOpen = !this.isNotificationOpen;
    this.isUserMenuOpen = false;
  }

  toggleUserMenu() {
    this.isUserMenuOpen = !this.isUserMenuOpen;
    this.isNotificationOpen = false;
  }

  executeGlobalSearch() {
    const term = this.globalSearchTerm.trim().toLowerCase();
    if (!term) return;

    const match = this.quickLinks.find((link) => link.label.toLowerCase().includes(term));
    if (match) {
      this.router.navigate([match.url]);
      this.globalSearchTerm = '';
    }
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  viewProfile() {
    this.isUserMenuOpen = false;
    this.router.navigate(['/admin/profile']);
  }

  private updatePageTitle(url: string) {
    const match = this.quickLinks.find((link) => url.startsWith(link.url));
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
