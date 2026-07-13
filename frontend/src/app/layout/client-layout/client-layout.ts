import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { AuthService } from '../../core/services/auth';
import { LayoutStateService } from '../../core/services/layout-state.service';
import { ChatWidgetComponent } from '../../features/client/chat-widget/chat-widget';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'app-client-layout',
  standalone: true,
  imports: [CommonModule, RouterModule, ButtonModule, ChatWidgetComponent],
  templateUrl: './client-layout.html',
  styleUrls: ['./client-layout.css']
})
export class ClientLayout implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private router = inject(Router);
  layoutState = inject(LayoutStateService);
  private destroy$ = new Subject<void>();

  isLoggedIn = false;
  username = '';
  isMobileMenuOpen = false;
  isPropertyOwner = false;

  ngOnInit() {
    this.authService.currentUser$.pipe(takeUntil(this.destroy$)).subscribe(state => {
      this.isLoggedIn = state.isAuthenticated;
      this.username = state.username;
      this.isPropertyOwner = state.roles && state.roles.includes('PROPERTY_OWNER');
    });
  }

  logout() {
    this.authService.logout();
    this.isMobileMenuOpen = false;
    this.router.navigate(['/']);
  }

  toggleMobileMenu() {
    this.isMobileMenuOpen = !this.isMobileMenuOpen;
  }

  closeMobileMenu() {
    this.isMobileMenuOpen = false;
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
