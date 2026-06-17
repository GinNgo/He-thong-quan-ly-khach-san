import { Component } from '@angular/core';
import { RouterOutlet, Router } from '@angular/router';
import { Sidebar } from '../sidebar/sidebar';
import { AuthService } from '../../core/services/auth';
import { AiAssistant } from '../../features/ai-assistant/ai-assistant';

@Component({
  selector: 'app-admin-layout',
  imports: [RouterOutlet, Sidebar, AiAssistant],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.css'
})
export class AdminLayout {
  isSidebarCollapsed = false;

  constructor(private authService: AuthService, private router: Router) {}

  toggleSidebar() {
    this.isSidebarCollapsed = !this.isSidebarCollapsed;
  }

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  viewProfile() {
    alert('Chức năng hồ sơ đang được phát triển!');
  }
}
