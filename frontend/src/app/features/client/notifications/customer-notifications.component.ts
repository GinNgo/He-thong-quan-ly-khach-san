import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

import {
  CustomerNotification,
  CustomerNotificationService,
} from '../../../core/services/customer-notification.service';

@Component({
  selector: 'app-customer-notifications',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './customer-notifications.component.html',
  styleUrl: './customer-notifications.component.css',
})
export class CustomerNotificationsComponent implements OnInit, OnDestroy {
  private readonly notificationsApi = inject(CustomerNotificationService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroy$ = new Subject<void>();

  notifications: CustomerNotification[] = [];
  unreadCount = 0;
  page = 0;
  totalPages = 0;
  loading = true;
  errorMessage = '';

  ngOnInit(): void {
    this.notificationsApi.notifications$
      .pipe(takeUntil(this.destroy$))
      .subscribe(notification => {
        if (this.notifications.some(item => item.id === notification.id)) return;
        this.notifications = [notification, ...this.notifications];
        if (!notification.isRead) this.unreadCount += 1;
        this.changeDetector.markForCheck();
      });
    this.notificationsApi.connect();
    this.loadPage(0);
  }

  loadPage(page: number): void {
    this.loading = true;
    this.errorMessage = '';
    this.notificationsApi.getInbox(page, 20)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: result => {
          this.notifications = result.content;
          this.unreadCount = result.unreadCount;
          this.page = result.number;
          this.totalPages = result.totalPages;
          this.loading = false;
          this.changeDetector.markForCheck();
        },
        error: () => {
          this.errorMessage = 'Khong the tai thong bao. Vui long thu lai.';
          this.loading = false;
          this.changeDetector.markForCheck();
        },
      });
  }

  markAsRead(notification: CustomerNotification): void {
    if (notification.isRead) return;
    this.notificationsApi.markAsRead(notification.id)
      .pipe(takeUntil(this.destroy$))
      .subscribe(updated => {
        this.notifications = this.notifications.map(item => item.id === updated.id ? updated : item);
        this.unreadCount = Math.max(0, this.unreadCount - 1);
        this.changeDetector.markForCheck();
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
