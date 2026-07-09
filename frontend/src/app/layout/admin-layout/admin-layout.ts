import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, RouterOutlet, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { Sidebar } from '../sidebar/sidebar';
import { AuthService } from '../../core/services/auth';
import { AiAssistant } from '../../features/ai-assistant/ai-assistant';

@Component({
  selector: 'app-admin-layout',
  imports: [CommonModule, FormsModule, RouterOutlet, Sidebar, AiAssistant],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.css'
})
export class AdminLayout {
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

  constructor(private authService: AuthService, private router: Router) {
    const authState = this.authService.getAuthState();
    this.currentUserName = authState.username || 'Admin';
    this.currentRoleLabel = this.toRoleLabel(authState.roles[0]);
    this.updatePageTitle(this.router.url);

    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => this.updatePageTitle(event.urlAfterRedirects));
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
